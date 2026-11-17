package app.remindme.fire

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.remindme.RemindMeApp

/** Where the phone's alarm system delivers a fire, or the end of a ring. All the deciding happens in the task core. */
class FireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fires = RemindMeApp.from(context).fires
        when (intent.action) {
            AlarmFireScheduler.ACTION_FIRE -> AlarmFireScheduler.fireFrom(intent)?.let(fires::deliver)
            AlarmFireScheduler.ACTION_RING_END -> AlarmFireScheduler.ringEndFrom(intent)?.let(fires::endRing)
        }
    }
}
