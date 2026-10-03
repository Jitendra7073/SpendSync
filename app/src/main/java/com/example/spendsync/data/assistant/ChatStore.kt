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
    /** How long the answer took, in milliseconds (0 for user messages and old rows). */
    val elapsedMs: Long = 0,
    /** Display name of the model that wrote it. */
    val model: String = "",
    /** "up", "down" or "". */
    val feedback: String = "",
)

/** One saved chat, for the history list. The title is the first thing the user asked. */
data class ConversationSummary(val id: String, val title: String, val messages: Int, val lastAt: Long)

/**
 * The on-device chat history (plain SQLite, no extra dependency). Every row carries the user id and
 * every query filters on it, so a second account on the same phone never sees the first one's chat.
 * Messages belong to a conversation, so the user can start a new chat and come back to old ones.
 */
class ChatStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "assistant.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                role TEXT NOT NULL,
                text TEXT NOT NULL,
                actions TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                conversation_id TEXT NOT NULL DEFAULT 'legacy-chat',
                elapsed_ms INTEGER NOT NULL DEFAULT 0,
                model TEXT NOT NULL DEFAULT '',
                feedback TEXT NOT NULL DEFAULT ''
            )""",
        )
        db.execSQL("CREATE INDEX idx_chat_user_time ON chat_messages(user_id, id)")
        db.execSQL("CREATE INDEX idx_chat_conv ON chat_messages(user_id, conversation_id, id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Old chats all become one conversation called "legacy".
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN conversation_id TEXT NOT NULL DEFAULT 'legacy-chat'")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN elapsed_ms INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN model TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN feedback TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_conv ON chat_messages(user_id, conversation_id, id)")
        }
    }

    suspend fun add(
        userId: String,
        conversationId: String,
        role: String,
        text: String,
        actions: List<String> = emptyList(),
        elapsedMs: Long = 0,
        model: String = "",
    ): Long = withContext(Dispatchers.IO) {
        writableDatabase.insert(
            "chat_messages",
            null,
            ContentValues().apply {
                put("user_id", userId)
                put("conversation_id", conversationId)
                put("role", role)
                put("text", text)
                put("actions", actions.joinToString(","))
                put("created_at", System.currentTimeMillis())
                put("elapsed_ms", elapsedMs)
                put("model", model)
            },
        )
    }

    /** The newest [limit] messages of one chat, oldest first. */
    suspend fun messages(userId: String, conversationId: String, limit: Int = 200): List<StoredMessage> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<StoredMessage>()
            readableDatabase.rawQuery(
                "SELECT id, role, text, actions, created_at, elapsed_ms, model, feedback FROM chat_messages " +
                    "WHERE user_id = ? AND conversation_id = ? ORDER BY id DESC LIMIT ?",
                arrayOf(userId, conversationId, limit.toString()),
            ).use { c ->
                while (c.moveToNext()) {
                    out += StoredMessage(
                        id = c.getLong(0), role = c.getString(1), text = c.getString(2),
                        actions = c.getString(3).split(',').filter { it.isNotBlank() },
                        createdAt = c.getLong(4), elapsedMs = c.getLong(5), model = c.getString(6), feedback = c.getString(7),
                    )
                }
            }
            out.reversed()
        }

    /** The chat the user was last in, or null when there is none. */
    suspend fun latestConversation(userId: String): String? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT conversation_id FROM chat_messages WHERE user_id = ? ORDER BY id DESC LIMIT 1",
            arrayOf(userId),
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    /** Every saved chat, most recent first. */
    suspend fun conversations(userId: String): List<ConversationSummary> = withContext(Dispatchers.IO) {
        val out = ArrayList<ConversationSummary>()
        readableDatabase.rawQuery(
            """SELECT conversation_id, COUNT(*), MAX(created_at), MAX(id),
                      (SELECT text FROM chat_messages m2 WHERE m2.user_id = m.user_id AND m2.conversation_id = m.conversation_id AND m2.role = 'user' ORDER BY m2.id LIMIT 1)
               FROM chat_messages m WHERE user_id = ? GROUP BY conversation_id ORDER BY MAX(id) DESC""",
            arrayOf(userId),
        ).use { c ->
            while (c.moveToNext()) {
                out += ConversationSummary(c.getString(0), c.getString(4).orEmpty(), c.getInt(1), c.getLong(2))
            }
        }
        out
    }

    suspend fun setFeedback(userId: String, messageId: Long, value: String) = withContext(Dispatchers.IO) {
        writableDatabase.update("chat_messages", ContentValues().apply { put("feedback", value) }, "id = ? AND user_id = ?", arrayOf(messageId.toString(), userId))
    }

    /** Removes a message and everything after it in that chat (used when the user edits an earlier question). */
    suspend fun deleteFrom(userId: String, conversationId: String, fromId: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete("chat_messages", "user_id = ? AND conversation_id = ? AND id >= ?", arrayOf(userId, conversationId, fromId.toString()))
    }

    suspend fun deleteConversation(userId: String, conversationId: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("chat_messages", "user_id = ? AND conversation_id = ?", arrayOf(userId, conversationId))
    }

    suspend fun clear(userId: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("chat_messages", "user_id = ?", arrayOf(userId))
    }
}
