package app.remindme.fire

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import app.remindme.AlarmActivity
import app.remindme.R
import app.remindme.ui.Times

/**
 * Rings for as long as [Ringing] lists an alarm: the default alarm sound, looping on the alarm stream so
 * it is heard on silent, with vibration. A foreground service of the system-exempted type, which an app
 * holding the exact-alarm permission may start from the background when a fire arrives.
 */
class RingService : Service() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var sounding = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, "Task alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                // The service makes the sound itself, on the alarm stream.
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A service started as foreground has to show its notification even when every alarm was answered meanwhile.
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        if (Ringing.tasks.isEmpty()) finish() else startSounding()
        return START_NOT_STICKY
    }

    /** The set of ringing alarms changed. */
    private fun refresh() {
        if (Ringing.tasks.isEmpty()) {
            finish()
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
    }

    private fun finish() {
        stopSounding()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSounding()
        if (running === this) running = null
        super.onDestroy()
    }

    private fun startSounding() {
        if (sounding) return
        sounding = true
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "remindme:ring")
            .apply { acquire(LONGEST_RING_MILLIS) }
        val alarmStream = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        player = listOf(Settings.System.DEFAULT_ALARM_ALERT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .firstNotNullOfOrNull { sound ->
                try {
                    MediaPlayer().apply {
                        setAudioAttributes(alarmStream)
                        setDataSource(this@RingService, sound)
                        isLooping = true
                        prepare()
                        start()
                    }
                } catch (failure: Exception) {
                    Log.w(TAG, "Could not play $sound", failure)
                    null
                }
            }
        getSystemService(Vibrator::class.java).vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    private fun stopSounding() {
        if (!sounding) return
        sounding = false
        player?.runCatching { stop(); release() }
        player = null
        getSystemService(Vibrator::class.java).cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun notification(): Notification {
        val ringing = Ringing.tasks.toList()
        val screen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(this, CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(screen)
            // Over the lock screen or a sleeping phone this opens the alarm screen; on a phone in use it is a banner.
            .setFullScreenIntent(screen, true)
        val only = ringing.singleOrNull()
        if (only != null) {
            builder.setContentTitle(only.title)
                .setContentText("Due ${Times.time(this, only.due)}")
                .addAction(action("Done", AnswerReceiver.ACTION_DONE, only.taskId))
                .addAction(action("Snooze 10 min", AnswerReceiver.ACTION_SNOOZE, only.taskId))
                .addAction(action("Stop", AnswerReceiver.ACTION_STOP, only.taskId))
        } else {
            builder.setContentTitle("${ringing.size} alarms ringing")
                .setContentText(ringing.joinToString(", ") { it.title })
        }
        return builder.build()
    }

    private fun action(label: String, action: String, taskId: Long): Notification.Action =
        Notification.Action.Builder(null as Icon?, label, AnswerReceiver.intent(this, action, taskId)).build()

    companion object {
        private const val TAG = "RingService"
        private const val CHANNEL_ALARMS = "task-alarms"

        /** Task notifications use the task's id, which is never negative. */
        private const val NOTIFICATION_ID = -1

        /** A backstop for the wake lock; the task core ends each ring after ten minutes. */
        private const val LONGEST_RING_MILLIS = 30 * 60 * 1000L

        private var running: RingService? = null

        /** Makes the ring match [Ringing] after it changed. */
        fun sync(context: Context) {
            val service = running
            when {
                service != null -> service.refresh()
                Ringing.tasks.isNotEmpty() -> context.startForegroundService(Intent(context, RingService::class.java))
            }
        }
    }
}
