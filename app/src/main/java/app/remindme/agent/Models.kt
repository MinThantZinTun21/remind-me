package app.remindme.agent

import android.app.DownloadManager
import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.File

/**
 * The model files on the phone: which one the agent uses, the ones downloading, and the ones copied over USB.
 * See GLOSSARY.md.
 *
 * A download is a user-initiated job ([ModelDownloadJob]) that Android runs on Wi-Fi, starts again by itself
 * after Wi-Fi comes back, and does not time out. It writes under a `.part` name and carries on where the file
 * stops, and the file is renamed to its `.litertlm` name only once complete, so a half-finished file is never
 * offered to the agent. The job keeps its progress here, where the Models screen reads it.
 */
class Models(context: Context) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("models", Context.MODE_PRIVATE)

    /** The app's own folder on shared storage: where downloads land and where a USB copy goes. */
    val folder: File? get() = context.getExternalFilesDir(null)

    /** A finished model file on the phone. */
    data class Installed(val file: File, val downloaded: Boolean) {
        val bytes: Long get() = file.length()
    }

    /** The finished model files, newest first. */
    fun installed(): List<Installed> {
        val fromDownloads = prefs.getStringSet(DOWNLOADED, emptySet()).orEmpty()
        return folder?.listFiles { file -> file.isFile && file.name.endsWith(".litertlm") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { Installed(it, downloaded = it.name in fromDownloads) }
    }

    /**
     * The file the agent loads, or null when there is none to use. Until the owner chooses, that is the newest
     * file on the phone, as in agent build 1. Once they have chosen, it is that file only: after they delete it,
     * the agent has none until they choose another.
     */
    fun inUse(): File? {
        val chosen = prefs.getString(IN_USE, null)
        val files = installed()
        return if (chosen == null) files.firstOrNull()?.file else files.firstOrNull { it.file.name == chosen }?.file
    }

    fun use(fileName: String) = prefs.edit().putString(IN_USE, fileName).apply()

    /** Removes a finished file. When it was the one in use, the agent has no model until another is chosen. */
    fun delete(fileName: String): Boolean {
        val wasInUse = inUse()?.name == fileName
        val file = File(folder ?: return false, fileName)
        val gone = !file.exists() || file.delete()
        if (gone) {
            if (wasInUse) prefs.edit().putString(IN_USE, "").apply()
            prefs.edit().putStringSet(DOWNLOADED, prefs.getStringSet(DOWNLOADED, emptySet()).orEmpty() - fileName).apply()
        }
        return gone
    }

    /** Free space where models go, in bytes. */
    fun freeBytes(): Long = folder?.let { runCatching { StatFs(it.path).availableBytes }.getOrNull() } ?: 0L

    /** Starts downloading [offer] on Wi-Fi, with its progress in the notification bar. Call it from the screen. */
    fun download(offer: ModelOffer) {
        val folder = folder ?: return
        // A part left behind by an earlier download that was given up would be taken for this one's start.
        File(folder, offer.file + PART).delete()
        prefs.edit()
            .putString(NAME + offer.file, offer.name)
            .putString(STATE + offer.file, WAITING + "Waiting to start")
            .putLong(DONE + offer.file, 0)
            .putLong(TOTAL + offer.file, offer.bytes)
            .putInt(FAILURES + offer.file, 0)
            .commit()
        ModelDownloadJob.schedule(context, offer)
    }

    /** The model's name as the owner saw it when downloading, or the file name. */
    fun nameOf(fileName: String): String = prefs.getString(NAME + fileName, null) ?: fileName

    /** Where a download stands. */
    sealed interface Progress {
        data class Running(val done: Long, val total: Long) : Progress
        data class Waiting(val done: Long, val total: Long, val reason: String) : Progress
        data class Failed(val reason: String) : Progress
    }

    /** Every download the app has started that has not finished, by file name. */
    fun progress(): Map<String, Progress> {
        forgetOldDownloads()
        return prefs.all.keys.filter { it.startsWith(STATE) }.associate { key ->
            val file = key.removePrefix(STATE)
            val state = prefs.getString(key, "").orEmpty()
            val done = prefs.getLong(DONE + file, 0)
            val total = prefs.getLong(TOTAL + file, 0)
            file to when {
                state == RUNNING -> Progress.Running(done, total)
                state.startsWith(FAILED) -> Progress.Failed(state.removePrefix(FAILED))
                else -> Progress.Waiting(done, total, state.removePrefix(WAITING))
            }
        }
    }

    /** Stops a download and throws away what it had fetched. Logged, so a download that vanished can be explained. */
    fun cancel(fileName: String) {
        Log.i(TAG, "Download of $fileName cancelled by the owner")
        ModelDownloadJob.cancel(context, fileName)
        forget(fileName)
        folder?.let { File(it, fileName + PART).delete() }
    }

    // What the download job reports. Kept here so the screen and the job read and write one record.

    internal fun running(fileName: String, done: Long, total: Long) {
        prefs.edit().putString(STATE + fileName, RUNNING).putLong(DONE + fileName, done).putLong(TOTAL + fileName, total).apply()
    }

    internal fun waiting(fileName: String, reason: String) {
        prefs.edit().putString(STATE + fileName, WAITING + reason).apply()
    }

    internal fun failed(fileName: String, reason: String) {
        prefs.edit().putString(STATE + fileName, FAILED + reason).apply()
    }

    /** Counts an attempt that ended without getting any further; returns how many in a row. */
    internal fun failedAttempt(fileName: String): Int {
        val failures = prefs.getInt(FAILURES + fileName, 0) + 1
        prefs.edit().putInt(FAILURES + fileName, failures).apply()
        return failures
    }

    internal fun madeProgress(fileName: String) = prefs.edit().putInt(FAILURES + fileName, 0).apply()

    /** False once the owner has cancelled: the job then stops and leaves nothing behind. */
    internal fun isTracked(fileName: String): Boolean = prefs.contains(STATE + fileName)

    /** The part file is complete: rename it into place, so the agent can use it. */
    internal fun finish(fileName: String): Boolean {
        val folder = folder ?: return false
        val part = File(folder, fileName + PART)
        val target = File(folder, fileName)
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) return false
        forget(fileName)
        prefs.edit().putStringSet(DOWNLOADED, prefs.getStringSet(DOWNLOADED, emptySet()).orEmpty() + fileName).apply()
        return true
    }

    internal fun partFile(fileName: String): File? = folder?.let { File(it, fileName + PART) }

    private fun forget(fileName: String) {
        prefs.edit()
            .remove(STATE + fileName).remove(DONE + fileName).remove(TOTAL + fileName).remove(FAILURES + fileName)
            .apply()
    }

    /**
     * Downloads started through Android's download service by the first Models build: that service stalled on
     * this phone, so they are stopped and their parts removed. The owner downloads again.
     */
    private fun forgetOldDownloads() {
        val old = prefs.all.filter { it.key.startsWith(OLD_KEY) }
        if (old.isEmpty()) return
        val downloads = context.getSystemService(DownloadManager::class.java)
        for ((key, id) in old) {
            (id as? Long)?.let { runCatching { downloads.remove(it) } }
            folder?.let { File(it, key.removePrefix(OLD_KEY) + PART).delete() }
            prefs.edit().remove(key).apply()
        }
    }

    companion object {
        private const val TAG = "Models"
        private const val IN_USE = "in_use"
        private const val DOWNLOADED = "downloaded"
        private const val OLD_KEY = "download:"
        private const val NAME = "name:"
        private const val STATE = "state:"
        private const val DONE = "done:"
        private const val TOTAL = "total:"
        private const val FAILURES = "failures:"
        private const val RUNNING = "running"
        private const val WAITING = "waiting:"
        private const val FAILED = "failed:"
        const val PART = ".part"
    }
}
