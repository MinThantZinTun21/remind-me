package app.remindme.fire

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import app.remindme.RemindMeApp
import app.remindme.wording.AnswerLabels

/** Where a notification's buttons, and its being swiped away, reach the tools. Works with the app closed. */
class AnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        val tools = RemindMeApp.from(context).tools
        when (intent.action) {
            ACTION_DONE -> tools.done(taskId)
            ACTION_SNOOZE -> tools.snooze(taskId)
            ACTION_STOP -> tools.stop(taskId)
        }
    }

    companion object {
        const val ACTION_DONE = "app.remindme.DONE"
        const val ACTION_SNOOZE = "app.remindme.SNOOZE"
        const val ACTION_STOP = "app.remindme.STOP"

        fun doneButton(context: Context, taskId: Long) = button(context, AnswerLabels.DONE, ACTION_DONE, taskId)
        fun snoozeButton(context: Context, taskId: Long) = button(context, AnswerLabels.SNOOZE, ACTION_SNOOZE, taskId)
        fun stopButton(context: Context, taskId: Long) = button(context, AnswerLabels.STOP, ACTION_STOP, taskId)

        private fun button(context: Context, label: String, action: String, taskId: Long): Notification.Action =
            Notification.Action.Builder(null as Icon?, label, intent(context, action, taskId)).build()

        fun intent(context: Context, action: String, taskId: Long): PendingIntent {
            val intent = Intent(context, AnswerReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("remindme://answer/$taskId"))
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
