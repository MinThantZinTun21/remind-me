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
    val fires = Fires(store, scheduler, announcer, fireLog, clock)
    val tools = Tools(store, fires, announcer, clock)

    /** Move the clock to [time], delivering whatever the alarm system holds for a moment on the way, in order. */
    fun advanceTo(time: String) {
        val target = at(time)
        val deadline = target.atZone(clock.zoneId).toInstant()
        while (true) {
            val fire = scheduler.nextFire()
            val ringEnd = scheduler.nextRingEnd()
            val moment = listOfNotNull(fire?.value, ringEnd?.value).minOrNull()?.takeIf { it <= deadline } ?: break
            clock.local = maxOf(clock.local, LocalDateTime.ofInstant(moment, clock.zoneId))
            if (fire?.value == moment) {
                scheduler.remove(fire.key)
                fires.deliver(fire.key)
            } else {
                val taskId = ringEnd!!.key
                scheduler.removeRingEnd(taskId)
                fires.endRing(taskId)
            }
        }
        clock.local = target
    }

    /** The kinds of fire delivered so far, in order. */
    val fired: List<FireKind> get() = fireLog.entries.map { it.kind }

    /** The phone is off until [time]: nothing is delivered meanwhile, its alarm list is wiped, and the app reschedules at start-up. */
    fun restartPhoneAt(time: String) {
        clock.local = at(time)
        scheduler.wipe()
        fires.reschedule()
    }
}

fun at(time: String): LocalDateTime = LocalDateTime.parse(time)

val Announcement.rings: Boolean get() = form == Announcement.Form.Ring
val Announcement.saysWasDue: Boolean get() = form == Announcement.Form.WasDue

/** The lead time of an early warning; null for every other announcement. */
val Announcement.leadTime get() = (form as? Announcement.Form.Warning)?.leadTime

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
    private val fires = linkedMapOf<Long, Map<Fire, Instant>>()
    private val ringEnds = linkedMapOf<Long, Instant>()

    override fun replace(taskId: Long, fires: Map<Fire, Instant>, ringEnd: Instant?) {
        if (fires.isEmpty()) this.fires.remove(taskId) else this.fires[taskId] = fires
        if (ringEnd == null) ringEnds.remove(taskId) else ringEnds[taskId] = ringEnd
    }

    /** The scheduled fires, earliest first. */
    fun pending(): List<Fire> = fires.values.flatMap { it.entries }.sortedBy { it.value }.map { it.key }

    /** Every real moment the alarm system holds, for fires and for rings giving up. */
    fun pendingInstants(): List<Instant> = (fires.values.flatMap { it.values } + ringEnds.values).sorted()

    fun nextFire(): Map.Entry<Fire, Instant>? = fires.values.flatMap { it.entries }.minByOrNull { it.value }
    fun nextRingEnd(): Map.Entry<Long, Instant>? = ringEnds.entries.minByOrNull { it.value }

    fun wipe() {
        fires.clear()
        ringEnds.clear()
    }

    fun remove(fire: Fire) {
        fires[fire.taskId] = fires[fire.taskId].orEmpty() - fire
    }

    fun removeRingEnd(taskId: Long) {
        ringEnds.remove(taskId)
    }
}

class RecordingAnnouncer : Announcer {
    val announced = mutableListOf<Announcement>()
    val cleared = mutableListOf<Long>()

    /** The tasks ringing right now. */
    val ringing = linkedSetOf<Long>()

    override fun announce(announcement: Announcement) {
        announced += announcement
        if (announcement.rings) ringing += announcement.taskId else ringing -= announcement.taskId
    }
    override fun clear(taskId: Long) {
        cleared += taskId
        ringing -= taskId
    }
}

class RecordingFireLog : FireLog {
    val entries = mutableListOf<FireRecord>()
    override fun record(record: FireRecord) {
        entries += record
    }
}

