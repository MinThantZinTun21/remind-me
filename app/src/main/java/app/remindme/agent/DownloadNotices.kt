package app.remindme.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import app.remindme.MainActivity
import app.remindme.R
import app.remindme.wording.gigabytes

/**
 * What the notification bar shows about model downloads: a live notification with a progress bar for each
 * download while it fetches or waits, then a notice when it is ready or has stopped. Tapping any of them opens
 * the Models screen, where a download can be cancelled after a confirm. There is no Cancel button on the
 * notification itself: a stray tap there threw away a download.
 */
object DownloadNotices {
    /** Apart from task notifications, which use the task's id, and the ringing alarm's -1. */
    const val PROGRESS_ID = -2
    private const val CHANNEL = "model-download-progress"

    /** The first channel, silent and so kept out of the status bar. Removed in favour of [CHANNEL]. */
    private const val OLD_CHANNEL = "model-downloads"
    private const val READY_TAG = "model-ready:"
    private const val STOPPED_TAG = "model-stopped:"

    /** A download still under way, as the notification names it. */
    data class Going(val file: String, val name: String, val state: Models.Progress)

    /** The words and the bar of the live notification. [total] is 0 while the size is not yet known. */
    data class Line(val title: String, val text: String, val done: Long, val total: Long)

    fun line(going: List<Going>): Line {
        val done = going.sumOf { sizes(it.state).first }
        val total = if (going.any { sizes(it.state).second <= 0 }) 0L else going.sumOf { sizes(it.state).second }
        val title = when (going.size) {
            0 -> "Downloading a model"
            1 -> "Downloading ${going.single().name}"
            else -> "Downloading ${going.size} models"
        }
        val amount = if (total > 0) "${gigabytes(done)} of ${gigabytes(total)}  ·  ${done * 100 / total}%" else "Starting…"
        // When nothing is fetching, the reason it waits matters more than the amount.
        val waiting = going.map { it.state }.filterIsInstance<Models.Progress.Waiting>()
        val text = if (going.isNotEmpty() && waiting.size == going.size) "${waiting.first().reason}  ·  $amount" else amount
        return Line(title, text, done, total)
    }

    private fun sizes(state: Models.Progress): Pair<Long, Long> = when (state) {
        is Models.Progress.Running -> state.done to state.total
        is Models.Progress.Waiting -> state.done to state.total
        else -> 0L to 0L
    }

    /** The live notification: its icon in the status bar, its bar in the notification panel, and no sound. */
    fun progress(context: Context, going: List<Going>): Notification {
        channel(context)
        val line = line(going)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(line.title)
            .setContentText(line.text)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openModels(context, 0))
        if (line.total > 0) builder.setProgress(1000, (line.done * 1000 / line.total).toInt(), false) else builder.setProgress(0, 0, true)
        return builder.build()
    }

    /**
     * The number of one download's live notification. Below the ringing alarm's -1 and the old -2, and apart
     * from task notifications, which use the task's own (positive) id.
     */
    fun idFor(file: String): Int = -1_000 - (file.hashCode() and 0xFFFF)

    /** Posts or redraws one download's live notification outside the job, such as while it waits for Wi-Fi. */
    fun show(context: Context, file: String, notification: Notification) =
        context.getSystemService(NotificationManager::class.java).notify(idFor(file), notification)

    /** Removes one download's live notification, and the one the first Models build used for all of them. */
    fun clear(context: Context, file: String) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.cancel(idFor(file))
        notifications.cancel(PROGRESS_ID)
    }

    /** A download finished and the model can be chosen. */
    fun ready(context: Context, name: String) = notice(context, READY_TAG + name, "$name is downloaded", "Open Models to use it")

    /** A download stopped and will not carry on by itself. */
    fun stopped(context: Context, name: String, reason: String) =
        notice(context, STOPPED_TAG + name, "$name did not download", reason)

    private fun notice(context: Context, tag: String, title: String, text: String) {
        channel(context)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openModels(context, tag.hashCode()))
            .build()
        // Tagged, so these never replace a task's notification of the same number.
        context.getSystemService(NotificationManager::class.java).notify(tag, 0, notification)
    }

    private fun channel(context: Context) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.deleteNotificationChannel(OLD_CHANNEL)
        // Default importance, so the icon shows in the status bar (Android hides the icons of low, silent
        // channels there), but without a sound or a vibration.
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun openModels(context: Context, request: Int): PendingIntent =
        PendingIntent.getActivity(context, request, MainActivity.openModels(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
