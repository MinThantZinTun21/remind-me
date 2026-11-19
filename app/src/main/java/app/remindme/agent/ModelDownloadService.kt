package app.remindme.agent

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log

/**
 * Keeps the download notification live: once a second it reads where Android's download service has got to
 * and redraws the progress bar. It puts finished files into place and stops once nothing is downloading.
 * The download itself is Android's and carries on even if this service is stopped.
 */
class ModelDownloadService : Service() {
    private lateinit var models: Models
    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler
    private val toldStopped = mutableSetOf<String>()
    private val tick = object : Runnable {
        override fun run() {
            if (update()) worker.postDelayed(this, EVERY_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        models = Models(this)
        thread = HandlerThread("model-downloads").apply { start() }
        worker = Handler(thread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(DownloadNotices.PROGRESS_ID, DownloadNotices.progress(this, emptyList()), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        worker.removeCallbacks(tick)
        worker.post(tick)
        return START_NOT_STICKY
    }

    /** Redraws the notification. False once nothing is left to show, and the service has stopped. */
    private fun update(): Boolean {
        for (file in models.finishDownloads()) DownloadNotices.ready(this, models.nameOf(file))
        val states = models.progress()
        for ((file, state) in states) {
            if (state is Models.Progress.Failed && toldStopped.add(file)) DownloadNotices.stopped(this, models.nameOf(file), state.reason)
        }
        val going = states
            .filterValues { it is Models.Progress.Running || it is Models.Progress.Waiting }
            .map { (file, state) -> DownloadNotices.Going(file, models.nameOf(file), state) }
        if (going.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return false
        }
        getSystemService(NotificationManager::class.java).notify(DownloadNotices.PROGRESS_ID, DownloadNotices.progress(this, going))
        return true
    }

    /** Android allows a data-sync service six hours a day; the download carries on without the live bar. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        worker.removeCallbacksAndMessages(null)
        thread.quitSafely()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val EVERY_MS = 1_000L

        /** Shows the live notification. Only works while the app is on screen, which is when downloads start. */
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, ModelDownloadService::class.java)) }
                .onFailure { Log.w(TAG, "Could not show the download notification", it) }
        }

        /** Brings the live notification back when the app opens and a download is still going (the phone may have stopped it). */
        fun resume(context: Context) {
            if (Models(context).downloading()) start(context)
        }
    }
}
