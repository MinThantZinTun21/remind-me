package app.remindme.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The test seam: the tools and task core, driven by a fake clock, with every Android edge replaced by a stand-in. */
class World(now: String, zone: String = "Asia/Yangon") {
    val clock = FakeClock(LocalDateTime.parse(now), ZoneId.of(zone))
    val store = InMemoryTaskStore()
    val scheduler = RecordingScheduler()
    val announcer = RecordingAnnouncer()
    val fireLog = RecordingFireLog()
    val tools = Tools(store, scheduler, announcer, fireLog, clock)

    /** Move the clock to [time], delivering every scheduled fire that comes due on the way, in order. */
    fun advanceTo(time: String) {
        val target = at(time)
        while (true) {
            val next = scheduler.pending().filter { it.at <= target }.minByOrNull { it.at } ?: break
            if (next.at > clock.local) clock.local = next.at
            scheduler.remove(next)
            tools.fire(next)
        }
        clock.local = target
    }

    /** The phone is off until [time]: nothing is delivered meanwhile, its alarm list is wiped, and the app reschedules at start-up. */
    fun restartPhoneAt(time: String) {
        clock.local = at(time)
        scheduler.wipe()
        tools.reschedule()
    }
}

fun at(time: String): LocalDateTime = LocalDateTime.parse(time)

class FakeClock(var local: LocalDateTime, var zoneId: ZoneId) : Clock {
    override fun instant(): Instant = local.atZone(zoneId).toInstant()
    override fun zone(): ZoneId = zoneId
}

class InMemoryTaskStore : TaskStore {
    private val tasks = linkedMapOf<Long, Task>()
    private var nextId = 1L

    override fun all(): List<Task> = tasks.values.toList()
    override fun get(id: Long): Task? = tasks[id]
    override fun put(task: Task): Task {
        val saved = if (task.id == 0L) task.copy(id = nextId++) else task
        tasks[saved.id] = saved
        return saved
    }
    override fun delete(id: Long) {
        tasks.remove(id)
    }
}

class RecordingScheduler : FireScheduler {
    private val byTask = mutableMapOf<Long, List<Fire>>()
    private val zones = mutableMapOf<Long, ZoneId>()

    override fun replace(taskId: Long, fires: List<Fire>, zone: ZoneId) {
        if (fires.isEmpty()) byTask.remove(taskId) else byTask[taskId] = fires
        zones[taskId] = zone
    }

    /** The real moments the pending fires were scheduled for. */
    fun pendingInstants(): List<Instant> = pending().map { it.at.atZone(zones.getValue(it.taskId)).toInstant() }

    fun wipe() = byTask.clear()

    fun pending(): List<Fire> = byTask.values.flatten().sortedBy { it.at }
    fun remove(fire: Fire) {
        byTask[fire.taskId] = byTask[fire.taskId].orEmpty() - fire
    }
}

class RecordingAnnouncer : Announcer {
    val announced = mutableListOf<Announcement>()
    val cleared = mutableListOf<Long>()
    override fun announce(announcement: Announcement) {
        announced += announcement
    }
    override fun clear(taskId: Long) {
        cleared += taskId
    }
}

class RecordingFireLog : FireLog {
    val entries = mutableListOf<FireRecord>()
    override fun record(record: FireRecord) {
        entries += record
    }
}

