package app.remindme.fire

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.remindme.MainActivity
import app.remindme.core.Fire
import app.remindme.core.FireKind
import app.remindme.core.FireScheduler
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Hands planned fires to the phone's alarm system. Every fire, not only alarms, uses the alarm-clock
 * call, because it is the one call seen to wake this app from the HiOS freezer (docs/adr/0001).
 */
class AlarmFireScheduler(private val context: Context) : FireScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun replace(taskId: Long, fires: List<Fire>) {
        for (kind in FireKind.entries) {
            val fire = fires.firstOrNull { it.kind == kind }
            if (fire == null) {
                alarms.cancel(broadcast(taskId, kind, null))
            } else {
                val at = fire.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp()), broadcast(taskId, kind, fire.at))
            }
        }
    }

    /** One slot per task and kind of fire, so replacing or cancelling always finds the earlier one. */
    private fun broadcast(taskId: Long, kind: FireKind, at: LocalDateTime?): PendingIntent {
        val intent = Intent(context, FireReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("remindme://fire/$taskId/${kind.name}"))
        if (at != null) intent.putExtra(EXTRA_AT, at.toString())
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val ACTION_FIRE = "app.remindme.FIRE"
        const val EXTRA_AT = "at"

        fun fireFrom(intent: Intent): Fire? {
            val segments = intent.data?.pathSegments ?: return null
            val taskId = segments.getOrNull(0)?.toLongOrNull() ?: return null
            val kind = segments.getOrNull(1)?.let { name -> FireKind.entries.firstOrNull { it.name == name } } ?: return null
            val at = intent.getStringExtra(EXTRA_AT)?.let(LocalDateTime::parse) ?: return null
            return Fire(taskId, kind, at)
        }
    }
}
