package app.remindme.fire

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import app.remindme.MainActivity
import app.remindme.R
import app.remindme.core.Announcement
import app.remindme.core.Announcement.Form
import app.remindme.core.Announcer
import app.remindme.wording.Times
import app.remindme.wording.inWords

/** Tells the user on the phone: a notification, or a ring handed to [RingService]. */
class NotificationAnnouncer(private val context: Context) : Announcer {
    private val notifications = context.getSystemService(NotificationManager::class.java)

    init {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_NOTIFICATIONS, "Task notifications", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    override fun announce(announcement: Announcement) {
        if (announcement.form == Form.Ring && ring(announcement)) return
        silence(announcement.taskId)
        notify(announcement)
    }

    override fun clear(taskId: Long) {
        silence(taskId)
        notifications.cancel(taskId.toInt())
    }

    /** Starts the ring. Returns false when the phone refuses, so the user at least gets a notification. */
    private fun ring(announcement: Announcement): Boolean {
        Ringing.add(announcement)
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
        val form = announcement.form
        val due = announcement.due
        val (title, text) = when (form) {
            is Form.Warning ->
                (if (form.onTime) "In ${form.leadTime.inWords()}: ${announcement.title}" else announcement.title) to
                    "Due ${Times.dateAndTime(context, due)}"
            Form.WasDue -> announcement.title to "Was due ${Times.dateAndTime(context, due)}"
            // A ring the phone refused is told as the notification it would have been.
            Form.DueNow, Form.Ring -> announcement.title to Times.dueAt(context, due)
        }
        val builder = Notification.Builder(context, CHANNEL_NOTIFICATIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(AnswerReceiver.doneButton(context, taskId))
            // Swiping the notification away is Stop: the task stays as it is, overdue once its due time passes.
            .setDeleteIntent(AnswerReceiver.stopOnSwipe(context, taskId))
        // An early warning offers Done but no Snooze.
        if (form !is Form.Warning) builder.addAction(AnswerReceiver.snoozeButton(context, taskId))
        notifications.notify(taskId.toInt(), builder.build())
    }

    private companion object {
        const val TAG = "NotificationAnnouncer"
        const val CHANNEL_NOTIFICATIONS = "task-notifications"
    }
}
