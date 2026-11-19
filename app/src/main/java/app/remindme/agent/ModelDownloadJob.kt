package app.remindme.agent

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import android.os.SystemClock
import android.util.Log
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads one model as a user-initiated data transfer job: Android runs it only on Wi-Fi, does not time it
 * out, stops it when Wi-Fi drops and starts it again when Wi-Fi comes back. Each start asks Hugging Face for
 * the file afresh (its signed links expire) and carries on from the end of the `.part` file.
 *
 * Android's download service did this job before, and on the Camon 40 it stalled for good after a dropped
 * connection.
 */
class ModelDownloadJob : JobService() {
    private val attempts = ConcurrentHashMap<Int, Attempt>()

    override fun onStartJob(params: JobParameters): Boolean {
        val file = params.extras.getString(FILE) ?: return false
        val models = Models(this)
        if (!models.isTracked(file)) return false
        val attempt = Attempt(params, models, file)
        show(params, attempt.going(Models.Progress.Waiting(models.partFile(file)?.length() ?: 0, params.extras.getLong(BYTES), "Connecting")))
        attempts[params.jobId] = attempt
        attempt.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        attempts.remove(params.jobId)?.halt()
        val file = params.extras.getString(FILE) ?: return false
        val models = Models(this)
        if (!models.isTracked(file)) {
            DownloadNotices.clear(this, file)
            return false
        }
        val reason = when (params.stopReason) {
            JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "Waiting for Wi-Fi"
            else -> "Paused by Android, carrying on soon"
        }
        Log.i(TAG, "Download of $file stopped by Android (reason ${params.stopReason}): $reason")
        models.waiting(file, reason)
        DownloadNotices.show(this, file, DownloadNotices.progress(this, listOf(goingFrom(models, file))))
        return true
    }

    private fun show(params: JobParameters, going: DownloadNotices.Going) {
        val file = going.file
        setNotification(params, DownloadNotices.idFor(file), DownloadNotices.progress(this, listOf(going)), JOB_END_NOTIFICATION_POLICY_DETACH)
    }

    /** One run of the job: fetches from where the part file ends until the file is complete or the run stops. */
    private inner class Attempt(private val params: JobParameters, private val models: Models, private val file: String) : Thread("model-download") {
        @Volatile private var halted = false
        private val url = params.extras.getString(URL_KEY).orEmpty()
        private val expected = params.extras.getLong(BYTES)

        fun halt() {
            halted = true
            interrupt()
        }

        fun going(state: Models.Progress) = DownloadNotices.Going(file, models.nameOf(file), state)

        override fun run() {
            val part = models.partFile(file) ?: return finish(reschedule = false)
            val start = part.length()
            var done = start
            try {
                val link = URL(url)
                val connection = (params.network?.openConnection(link) ?: link.openConnection()) as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = true
                if (start > 0) connection.setRequestProperty("Range", "bytes=$start-")
                val code = connection.responseCode
                val append = when (code) {
                    HttpURLConnection.HTTP_PARTIAL -> true
                    HttpURLConnection.HTTP_OK -> false
                    // Asked for bytes past the end: the part is already whole.
                    416 -> if (start > 0 && (expected <= 0 || start >= expected)) return complete() else return giveUp("Hugging Face answered 416")
                    in 400..499 -> if (code == 408 || code == 429) throw IOException("HTTP $code") else return giveUp("Hugging Face answered $code")
                    else -> throw IOException("HTTP $code")
                }
                if (!append) done = 0
                val length = connection.contentLengthLong
                val total = if (length > 0) done + length else expected
                var shown = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            if (halted) return
                            if (!models.isTracked(file)) return cancelled()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            val now = SystemClock.elapsedRealtime()
                            if (now - shown >= 1_000) {
                                shown = now
                                models.running(file, done, total)
                                show(params, going(Models.Progress.Running(done, total)))
                            }
                        }
                    }
                }
                if (total > 0 && done < total) throw IOException("The connection ended at $done of $total bytes")
                complete()
            } catch (problem: IOException) {
                if (halted) return
                Log.w(TAG, "Download of $file broke off at $done bytes", problem)
                // Only attempts that fetched nothing count towards giving up; one that got further starts the count again.
                if (done > start) models.madeProgress(file) else if (models.failedAttempt(file) >= MAX_FAILURES) return giveUp("The connection kept dropping")
                models.waiting(file, "Connection dropped, trying again")
                show(params, going(Models.Progress.Waiting(done, expected, "Connection dropped, trying again")))
                finish(reschedule = true)
            } catch (problem: RuntimeException) {
                // Anything else would end the app's process, alarms included, so the download stops instead.
                if (halted) return
                Log.e(TAG, "Download of $file failed", problem)
                giveUp("Something went wrong: ${problem.javaClass.simpleName}")
            }
        }

        private fun complete() {
            if (models.finish(file)) {
                Log.i(TAG, "Download of $file finished")
                DownloadNotices.clear(this@ModelDownloadJob, file)
                DownloadNotices.ready(this@ModelDownloadJob, models.nameOf(file))
            } else {
                return giveUp("The finished file could not be put in place")
            }
            finish(reschedule = false)
        }

        private fun giveUp(reason: String) {
            Log.w(TAG, "Download of $file given up: $reason")
            models.failed(file, reason)
            DownloadNotices.clear(this@ModelDownloadJob, file)
            DownloadNotices.stopped(this@ModelDownloadJob, models.nameOf(file), reason)
            finish(reschedule = false)
        }

        private fun cancelled() {
            models.partFile(file)?.delete()
            DownloadNotices.clear(this@ModelDownloadJob, file)
            finish(reschedule = false)
        }

        private fun finish(reschedule: Boolean) {
            attempts.remove(params.jobId)
            jobFinished(params, reschedule)
        }
    }

    companion object {
        private const val TAG = "ModelDownloadJob"
        private const val FILE = "file"
        private const val URL_KEY = "url"
        private const val BYTES = "bytes"

        /** Attempts in a row that fetched nothing before the download is given up. */
        private const val MAX_FAILURES = 8

        /** Starts the download. Android only allows this while the app is on screen. */
        fun schedule(context: Context, offer: ModelOffer) {
            val extras = PersistableBundle().apply {
                putString(FILE, offer.file)
                putString(URL_KEY, offer.url)
                putLong(BYTES, offer.bytes)
            }
            val job = JobInfo.Builder(jobId(offer.file), ComponentName(context, ModelDownloadJob::class.java))
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setEstimatedNetworkBytes(offer.bytes, 0)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true)
                .setExtras(extras)
                .build()
            val result = runCatching { context.getSystemService(JobScheduler::class.java).schedule(job) }
            if (result.getOrNull() != JobScheduler.RESULT_SUCCESS) {
                Log.w(TAG, "Android would not start the download of ${offer.file}", result.exceptionOrNull())
                Models(context).failed(offer.file, "Android would not start the download")
            }
        }

        fun cancel(context: Context, file: String) {
            context.getSystemService(JobScheduler::class.java).cancel(jobId(file))
            DownloadNotices.clear(context, file)
        }

        private fun jobId(file: String): Int = 1_000 + (file.hashCode() and 0xFFFFF)
    }
}

/** Where a download stands, from the record [Models] keeps, for the notification. */
private fun goingFrom(models: Models, file: String): DownloadNotices.Going =
    DownloadNotices.Going(file, models.nameOf(file), models.progress()[file] ?: Models.Progress.Waiting(0, 0, "Waiting"))
