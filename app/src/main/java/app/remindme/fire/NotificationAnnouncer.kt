package app.remindme.fire

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        val open = PendingIntent.getActivity(
            context,
            announcement.taskId.toInt(),
            Intent(context, MainActivity::class.java),
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
            .build()
        notifications.notify(announcement.taskId.toInt(), notification)
    }

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
