package app.remindme.setup

import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * The one-time phone settings Remind Me needs to tell the user on time: what the app can read of each,
 * the system screen that changes it, and the user's own tick for the two the app cannot read.
 */
class SetUp(private val context: Context) {
    private val ticks = context.getSharedPreferences("set-up", Context.MODE_PRIVATE)
    private val appPackage = Uri.parse("package:${context.packageName}")

    /** Without this nothing the app fires can be seen or heard, so it is the one item that blocks. */
    fun notificationsOn(): Boolean = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    fun batteryExempt(): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** HiOS does not let an app read its Auto-start switch, so the user says when it is on. */
    var autoStartTicked: Boolean
        get() = ticks.getBoolean(AUTO_START, false)
        set(value) = ticks.edit().putBoolean(AUTO_START, value).apply()

    var lockedInRecentsTicked: Boolean
        get() = ticks.getBoolean(LOCKED_IN_RECENTS, false)
        set(value) = ticks.edit().putBoolean(LOCKED_IN_RECENTS, value).apply()

    /** The user has been through the checklist once; after that it only opens from the menu. */
    var finished: Boolean
        get() = ticks.getBoolean(FINISHED, false)
        set(value) = ticks.edit().putBoolean(FINISHED, value).apply()

    fun openNotificationSettings(from: Activity) = open(
        from,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        appSettings(),
    )

    /** Opens Phone Master's Auto-start list on HiOS; on a phone without it, the app's own settings page. */
    fun openAutoStart(from: Activity) = open(from, Intent().setComponent(HIOS_AUTO_START), appSettings())

    /** Shows Android's own "let this app run in the background without restrictions?" dialog. */
    fun askForBatteryExemption(from: Activity) = open(
        from,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appPackage),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appSettings(),
    )

    private fun appSettings() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appPackage)

    /** Starts the first of [screens] this phone has and lets this app open. */
    private fun open(from: Activity, vararg screens: Intent) {
        for (screen in screens) {
            try {
                from.startActivity(screen)
                return
            } catch (_: ActivityNotFoundException) {
                // This phone does not have that screen: try the next.
            } catch (_: SecurityException) {
                // The screen exists but is closed to other apps: try the next.
            }
        }
    }

    private companion object {
        const val AUTO_START = "auto-start-ticked"
        const val LOCKED_IN_RECENTS = "locked-in-recents-ticked"
        const val FINISHED = "finished"

        /** Reported for Transsion phones in the research for ticket 04; whether it opens on the Camon 40 is checked there. */
        val HIOS_AUTO_START = ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")
    }
}
