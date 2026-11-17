package app.remindme

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import app.remindme.core.Clock
import app.remindme.core.Tools
import app.remindme.data.RemindMeDatabase
import app.remindme.data.RoomFireLog
import app.remindme.data.RoomTaskStore
import app.remindme.fire.AlarmFireScheduler
import app.remindme.fire.NotificationAnnouncer
import java.time.Instant
import java.time.ZoneId

/** Wires the tools to the phone by hand. There is no dependency-injection framework. */
class RemindMeApp : Application() {
    /** Bumped on every change to tasks or the fire log, so screens that read it redraw. */
    val revision = mutableIntStateOf(0)

    lateinit var tools: Tools
        private set
    lateinit var fireLog: RoomFireLog
        private set

    override fun onCreate() {
        super.onCreate()
        val database = RemindMeDatabase.open(this)
        val changed: () -> Unit = { revision.intValue++ }
        fireLog = RoomFireLog(database.fireLog(), changed)
        tools = Tools(
            store = RoomTaskStore(database.tasks(), changed),
            scheduler = AlarmFireScheduler(this),
            announcer = NotificationAnnouncer(this),
            fireLog = fireLog,
            clock = PhoneClock,
        )
    }

    companion object {
        fun from(context: Context): RemindMeApp = context.applicationContext as RemindMeApp
    }
}

object PhoneClock : Clock {
    override fun instant(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
