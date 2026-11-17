package app.remindme.fire

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.remindme.RemindMeApp

/** Where the phone's alarm system delivers a fire. All the deciding happens in the tools. */
class FireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmFireScheduler.ACTION_FIRE) return
        val fire = AlarmFireScheduler.fireFrom(intent) ?: return
        RemindMeApp.from(context).tools.fire(fire)
    }
}
