package app.remindme

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import app.remindme.agent.Agent
import app.remindme.core.Clock
import app.remindme.core.DayButtons
import app.remindme.core.Fires
import app.remindme.core.Tools
import app.remindme.data.RemindMeDatabase
import app.remindme.data.RoomFireLog
import app.remindme.data.RoomTaskStore
import app.remindme.fire.AlarmFireScheduler
import app.remindme.fire.NotificationAnnouncer
import java.time.Instant
import java.time.ZoneId

/** Wires the tools and the fires to the phone by hand. There is no dependency-injection framework. */
class RemindMeApp : Application() {
    /** Bumped on every change to tasks or the fire log, so screens that read it redraw. */
    val revision = mutableIntStateOf(0)

    lateinit var tools: Tools
        private set
    lateinit var agent: Agent
        private set
    lateinit var dayButtons: DayButtons
        private set
    lateinit var fires: Fires
        private set
    lateinit var fireLog: RoomFireLog
        private set

    override fun onCreate() {
        super.onCreate()
        val database = RemindMeDatabase.open(this)
        val changed: () -> Unit = { revision.intValue++ }
        fireLog = RoomFireLog(database.fireLog(), changed)
        val store = RoomTaskStore(database.tasks(), changed)
        val announcer = NotificationAnnouncer(this)
        fires = Fires(store, AlarmFireScheduler(this), announcer, fireLog, PhoneClock)
        tools = Tools(store, fires, announcer, PhoneClock)
        agent = Agent(tools, PhoneClock)
        dayButtons = DayButtons(tools, PhoneClock)
    }

    companion object {
        fun from(context: Context): RemindMeApp = context.applicationContext as RemindMeApp
    }
}

object PhoneClock : Clock {
    override fun instant(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
