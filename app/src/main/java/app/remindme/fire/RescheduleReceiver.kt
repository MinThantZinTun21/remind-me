package app.remindme.fire

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.remindme.RemindMeApp

/**
 * Puts every pending fire back after a restart (before and after the first unlock), an app update,
 * or a change to the clock or time zone.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        RemindMeApp.from(context).tools.reschedule()
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
