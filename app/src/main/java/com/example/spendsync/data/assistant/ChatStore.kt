package com.example.spendsync.data.assistant

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A message as saved on the phone. [actions] are the screens the assistant offered buttons for. */
data class StoredMessage(
    val id: Long,
    val role: String,
    val text: String,
    val actions: List<String>,
    val createdAt: Long,
)

/**
 * The on-device chat history (plain SQLite, no extra dependency). Every row carries the user id and
 * every query filters on it, so a second account on the same phone never sees the first one's chat.
 * This is also where the future offline fast-path data will live.
 */
class ChatStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "assistant.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                role TEXT NOT NULL,
                text TEXT NOT NULL,
                actions TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )""",
        )
        db.execSQL("CREATE INDEX idx_chat_user_time ON chat_messages(user_id, id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    suspend fun add(userId: String, role: String, text: String, actions: List<String> = emptyList()): Long =
        withContext(Dispatchers.IO) {
            writableDatabase.insert(
                "chat_messages",
                null,
                ContentValues().apply {
                    put("user_id", userId)
                    put("role", role)
                    put("text", text)
                    put("actions", actions.joinToString(","))
                    put("created_at", System.currentTimeMillis())
                },
            )
        }

    /** The newest [limit] messages, oldest first. */
    suspend fun recent(userId: String, limit: Int = 60): List<StoredMessage> = withContext(Dispatchers.IO) {
        val out = ArrayList<StoredMessage>()
        readableDatabase.rawQuery(
            "SELECT id, role, text, actions, created_at FROM chat_messages WHERE user_id = ? ORDER BY id DESC LIMIT ?",
            arrayOf(userId, limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                out += StoredMessage(
                    id = c.getLong(0),
                    role = c.getString(1),
                    text = c.getString(2),
                    actions = c.getString(3).split(',').filter { it.isNotBlank() },
                    createdAt = c.getLong(4),
                )
            }
        }
        out.reversed()
    }

    suspend fun clear(userId: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("chat_messages", "user_id = ?", arrayOf(userId))
    }
}
