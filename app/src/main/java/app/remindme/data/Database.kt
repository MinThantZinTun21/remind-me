package app.remindme.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "tasks")
data class TaskRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String,
    val due: String,
    val handledUntil: String,
    val style: String,
    /** Lead times in whole minutes, separated by commas; empty when the task has none. */
    val leadTimes: String,
    val snoozedUntil: String?,
    val ringingUntil: String?,
    val doneAt: String?,
)

@Entity(tableName = "fire_log")
data class FireLogRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val taskId: Long,
    val title: String,
    val kind: String,
    val scheduledFor: String,
    val deliveredAtMillis: Long,
    val lateMillis: Long,
    val caughtUp: Boolean,
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun all(): List<TaskRow>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun get(id: Long): TaskRow?

    /** Inserts the row, or replaces the one with the same id. A row with id 0 is given a new id, which is returned. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(row: TaskRow): Long

    @Query("DELETE FROM tasks WHERE id = :id")
    fun delete(id: Long)
}

@Dao
interface FireLogDao {
    @Insert
    fun insert(row: FireLogRow)

    @Query("SELECT * FROM fire_log ORDER BY id DESC LIMIT :limit")
    fun latest(limit: Int): List<FireLogRow>

    @Query("DELETE FROM fire_log WHERE id NOT IN (SELECT id FROM fire_log ORDER BY id DESC LIMIT :keep)")
    fun trim(keep: Int)
}

@Database(entities = [TaskRow::class, FireLogRow::class], version = 1)
abstract class RemindMeDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun fireLog(): FireLogDao

    companion object {
        /**
         * The database lives in device-protected storage so a fire can be delivered after a restart,
         * before the owner has unlocked the phone (docs/adr/0003).
         *
         * Queries run on the calling thread on purpose. The data is a handful of rows, and a fire has to be
         * fully handled before its broadcast receiver returns: HiOS freezes this process five seconds after
         * it leaves the screen, so work handed to another thread may not get to run.
         */
        fun open(context: Context): RemindMeDatabase {
            val storage = context.applicationContext.createDeviceProtectedStorageContext()
            return Room.databaseBuilder(storage, RemindMeDatabase::class.java, "remind-me.db")
                .allowMainThreadQueries()
                .build()
        }
    }
}
