package app.remindme.fire

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import app.remindme.MainActivity
import app.remindme.R
import app.remindme.core.Announcement
import app.remindme.core.Announcer
import app.remindme.ui.Times

class NotificationAnnouncer(private val context: Context) : Announcer {
    private val notifications = context.getSystemService(NotificationManager::class.java)

    init {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_NOTIFICATIONS, "Task notifications", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    override fun announce(announcement: Announcement) {
        val taskId = announcement.taskId
        val open = PendingIntent.getActivity(
            context,
            0,
            MainActivity.openTask(context, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_NOTIFICATIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(announcement.title)
            .setContentText(wording(announcement))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(action("Done", AnswerReceiver.ACTION_DONE, taskId))
            .addAction(action("Snooze 10 min", AnswerReceiver.ACTION_SNOOZE, taskId))
            // Swiping the notification away is Stop: the task stays overdue.
            .setDeleteIntent(AnswerReceiver.intent(context, AnswerReceiver.ACTION_STOP, taskId))
            .build()
        notifications.notify(taskId.toInt(), notification)
    }

    override fun clear(taskId: Long) {
        notifications.cancel(taskId.toInt())
    }

    private fun action(label: String, action: String, taskId: Long): Notification.Action =
        Notification.Action.Builder(null as Icon?, label, AnswerReceiver.intent(context, action, taskId)).build()

    private fun wording(announcement: Announcement): String =
        if (announcement.late) {
            "Was due ${Times.dateAndTime(context, announcement.due)}"
        } else {
            "Due ${Times.time(context, announcement.due)}"
        }

    private companion object {
        const val CHANNEL_NOTIFICATIONS = "task-notifications"
    }
}
