package com.example.spendsync.data.local

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

/** Last good JSON of a GET, keyed "userId|endpoint:params". Shown when the phone is offline. */
@Entity(tableName = "cached_responses")
data class CachedResponse(@PrimaryKey val key: String, val json: String, val savedAt: Long)

/**
 * A transaction write made while offline, replayed in order by [com.example.spendsync.notifications.PendingCaptureRetryWorker].
 * [kind] is create / update / delete; [body] is the request JSON; [transactionId] is the client-made id for creates.
 */
@Entity(tableName = "outbox")
data class OutboxOp(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val userId: String,
    val kind: String,
    val transactionId: String,
    val body: String,
    val createdAt: Long,
)

@Dao
interface OfflineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putResponse(row: CachedResponse)

    @Query("SELECT json FROM cached_responses WHERE `key` = :key")
    suspend fun getResponse(key: String): String?

    @Query("DELETE FROM cached_responses")
    suspend fun clearResponses()

    @Insert
    suspend fun enqueue(op: OutboxOp)

    @Query("SELECT * FROM outbox WHERE userId = :userId ORDER BY seq")
    suspend fun pending(userId: String): List<OutboxOp>

    @Query("SELECT COUNT(*) FROM outbox WHERE userId = :userId AND transactionId = :transactionId")
    suspend fun pendingFor(userId: String, transactionId: String): Int

    @Query("DELETE FROM outbox WHERE seq = :seq")
    suspend fun remove(seq: Long)
}

@Database(entities = [CachedResponse::class, OutboxOp::class], version = 1, exportSchema = false)
abstract class OfflineDb : RoomDatabase() {
    abstract fun dao(): OfflineDao

    companion object {
        @Volatile private var instance: OfflineDb? = null

        fun get(context: Context): OfflineDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OfflineDb::class.java, "offline.db")
                .build().also { instance = it }
        }
    }
}
