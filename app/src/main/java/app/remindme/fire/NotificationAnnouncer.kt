package app.remindme.fire

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.util.Log
import app.remindme.MainActivity
import app.remindme.R
import app.remindme.core.Announcement
import app.remindme.core.Announcer
import app.remindme.ui.Times
import app.remindme.ui.inWords

/** Tells the owner on the phone: a notification, or a ring handed to [RingService]. */
class NotificationAnnouncer(private val context: Context) : Announcer {
    private val notifications = context.getSystemService(NotificationManager::class.java)

    init {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_NOTIFICATIONS, "Task notifications", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    override fun announce(announcement: Announcement) {
        if (announcement.ring && ring(announcement)) return
        silence(announcement.taskId)
        notify(announcement)
    }

    override fun clear(taskId: Long) {
        silence(taskId)
        notifications.cancel(taskId.toInt())
    }

    /** Starts the ring. Returns false when the phone refuses, so the owner at least gets a notification. */
    private fun ring(announcement: Announcement): Boolean {
        Ringing.add(RingingTask(announcement.taskId, announcement.title, announcement.due))
        return try {
            RingService.sync(context)
            notifications.cancel(announcement.taskId.toInt())
            true
        } catch (refused: RuntimeException) {
            Log.w(TAG, "The phone refused to start the ring", refused)
            Ringing.remove(announcement.taskId)
            false
        }
    }

    private fun silence(taskId: Long) {
        if (Ringing.remove(taskId)) RingService.sync(context)
    }

    private fun notify(announcement: Announcement) {
        val taskId = announcement.taskId
        val open = PendingIntent.getActivity(
            context,
            0,
            MainActivity.openTask(context, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // An early warning, given on time, leads with how long is left; it offers Done but no Snooze.
        val warning = announcement.leadTime?.takeUnless { announcement.late }
        val builder = Notification.Builder(context, CHANNEL_NOTIFICATIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (warning == null) announcement.title else "In ${warning.inWords()}: ${announcement.title}")
            .setContentText(wording(announcement))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(action("Done", AnswerReceiver.ACTION_DONE, taskId))
            // Swiping the notification away is Stop: the task stays as it is, overdue once its due time passes.
            .setDeleteIntent(AnswerReceiver.intent(context, AnswerReceiver.ACTION_STOP, taskId))
        if (announcement.leadTime == null) builder.addAction(action("Snooze 10 min", AnswerReceiver.ACTION_SNOOZE, taskId))
        val notification = builder.build()
        notifications.notify(taskId.toInt(), notification)
    }

    private fun action(label: String, action: String, taskId: Long): Notification.Action =
        Notification.Action.Builder(null as Icon?, label, AnswerReceiver.intent(context, action, taskId)).build()

    private fun wording(announcement: Announcement): String = when {
        // Before the due time: an early warning, on time or late.
        announcement.leadTime != null -> "Due ${Times.dateAndTime(context, announcement.due)}"
        announcement.late -> "Was due ${Times.dateAndTime(context, announcement.due)}"
        else -> "Due ${Times.time(context, announcement.due)}"
    }

    private companion object {
        const val TAG = "NotificationAnnouncer"
        const val CHANNEL_NOTIFICATIONS = "task-notifications"
    }
}
