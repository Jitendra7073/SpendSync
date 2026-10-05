package com.example.spendsync.data.assistant

import com.example.spendsync.BuildConfig
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.ApiClient
import com.example.spendsync.ui.settings.AssistantModelInfo
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.UnknownHostException
import java.util.TimeZone

/** A report the user sent to support, as listed in "Your reports". */
data class SupportTicketSummary(val ref: String, val category: String, val message: String, val status: String, val createdAt: String)

/** Everything attached to a report so the team can act on it without asking again. */
data class SupportContext(
    val appVersion: String,
    val device: String,
    val os: String,
    val language: String,
    val screen: String?,
    /** Recent chat lines, only when the user ticked "include this chat". */
    val chat: List<ChatTurn>,
)

/** One turn sent to the server. The server is stateless, so the phone sends the recent history. */
data class ChatTurn(val role: String, val content: String)

/**
 * Talks to `POST /api/assistant/chat` and exposes the streamed answer as a [Flow] of events.
 * Cancelling the collector cancels the HTTP call, so "Stop" really stops the model.
 */
class AssistantRepository(
    private val sessionDataStore: SessionDataStore,
    private val client: OkHttpClient = ApiClient.streamingClient,
    private val baseUrl: String = BuildConfig.API_BASE_URL,
) {
    private val gson = Gson()

    /** The models the server can use right now (for Settings -> Assistant). Null when it cannot be reached. */
    suspend fun models(): List<AssistantModelInfo>? = withContext(Dispatchers.IO) {
        val token = sessionDataStore.sessionToken.firstOrNull().orEmpty()
        if (token.isBlank()) return@withContext null
        try {
            client.newCall(
                Request.Builder().url("${baseUrl.trimEnd('/')}/api/assistant/status").header("Authorization", "Bearer $token").build(),
            ).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val models = JsonParser().parse(r.body?.string().orEmpty()).asJsonObject
                    .getAsJsonObject("data")?.getAsJsonArray("models") ?: return@use null
                models.mapNotNull { e ->
                    val o = e.asJsonObject
                    val id = o.get("id")?.asString ?: return@mapNotNull null
                    AssistantModelInfo(id, o.get("label")?.asString ?: id, o.get("healthy")?.asBoolean ?: true)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun <T> authed(path: String, build: Request.Builder.() -> Unit, parse: (String) -> T?): T? = withContext(Dispatchers.IO) {
        val token = sessionDataStore.sessionToken.firstOrNull().orEmpty()
        if (token.isBlank()) return@withContext null
        try {
            val req = Request.Builder().url("${baseUrl.trimEnd('/')}$path").header("Authorization", "Bearer $token").apply(build).build()
            client.newCall(req).execute().use { r -> if (r.isSuccessful) parse(r.body?.string().orEmpty()) else null }
        } catch (e: Exception) {
            null
        }
    }

    private fun json(body: Map<String, Any?>) = gson.toJson(body).toRequestBody("application/json".toMediaType())

    /** Thumbs up/down with reasons. Best-effort: false means it did not reach the server. */
    suspend fun sendFeedback(
        conversationId: String, messageRef: String, rating: String, reasons: List<String>, comment: String,
        question: String, answer: String, model: String,
    ): Boolean = authed(
        "/api/assistant/feedback",
        {
            post(json(mapOf(
                "conversationId" to conversationId, "messageRef" to messageRef, "rating" to rating, "reasons" to reasons,
                "comment" to comment, "question" to question.take(600), "answer" to answer.take(1500), "model" to model,
            )))
        },
        { true },
    ) == true

    /** Sends a report to the support team. Returns the reference (e.g. SS-7K3QX2), or null if it failed. */
    suspend fun submitTicket(category: String, message: String, context: SupportContext): String? = authed(
        "/api/support/tickets",
        {
            post(json(mapOf(
                "category" to category,
                "message" to message,
                "context" to mapOf(
                    "appVersion" to context.appVersion, "device" to context.device, "os" to context.os,
                    "language" to context.language, "screen" to context.screen,
                    "chat" to context.chat.takeLast(10).map { mapOf("role" to it.role, "text" to it.content.take(600)) }.ifEmpty { null },
                ).filterValues { it != null },
            )))
        },
        { body -> JsonParser().parse(body).asJsonObject.getAsJsonObject("data")?.get("ref")?.asString },
    )

    /** The user's own reports, newest first. Null when offline. */
    suspend fun tickets(): List<SupportTicketSummary>? = authed("/api/support/tickets", { get() }) { body ->
        JsonParser().parse(body).asJsonObject.getAsJsonArray("data")?.map { e ->
            val o = e.asJsonObject
            SupportTicketSummary(
                ref = o.get("ref")?.asString.orEmpty(), category = o.get("category")?.asString.orEmpty(),
                message = o.get("message")?.asString.orEmpty(), status = o.get("status")?.asString ?: "open",
                createdAt = o.get("createdAt")?.asString.orEmpty(),
            )
        }
    }

    fun chat(
        turns: List<ChatTurn>,
        language: String,
        screen: String?,
        conversationId: String,
        prefs: AssistantPrefs = AssistantPrefs(),
        /** An earlier answer the user is replying to. */
        reference: String? = null,
    ): Flow<AssistantEvent> = flow {
        val token = sessionDataStore.sessionToken.firstOrNull().orEmpty()
        if (token.isBlank()) {
            emit(AssistantEvent.Failure(FailureKind.SignedOut))
            return@flow
        }

        val body = gson.toJson(
            mapOf(
                "messages" to turns.map { mapOf("role" to it.role, "content" to it.content) },
                "language" to language,
                "screen" to screen,
                "timezone" to TimeZone.getDefault().id,
                "conversationId" to conversationId,
                "reference" to reference?.take(2500),
                "prefs" to mapOf(
                    "model" to prefs.model,
                    "disabledTools" to prefs.disabledTools.toList(),
                    "style" to prefs.style,
                    "tone" to prefs.tone,
                    "instructions" to prefs.instructions,
                ),
            ),
        ).toRequestBody("application/json".toMediaType())

        val call = client.newCall(
            Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/assistant/chat")
                .header("Authorization", "Bearer $token")
                .header("Accept", "text/event-stream")
                .post(body)
                .build(),
        )
        // Stop = cancel the coroutine = cancel the network call.
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(
                        AssistantEvent.Failure(
                            when (response.code) {
                                401 -> FailureKind.SignedOut
                                429 -> FailureKind.Busy
                                else -> FailureKind.Unavailable
                            },
                        ),
                    )
                    return@flow
                }
                val source = response.body?.source() ?: run {
                    emit(AssistantEvent.Failure(FailureKind.Unavailable))
                    return@flow
                }
                val sse = SseAccumulator()
                var finished = false
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val payload = sse.feed(line) ?: continue
                    val event = AssistantEventParser.parse(payload) ?: continue
                    emit(event)
                    if (event is AssistantEvent.Done || event is AssistantEvent.Failure) finished = true
                }
                // A stream that ends without "done" was cut off (network drop, server timeout).
                if (!finished) emit(AssistantEvent.Failure(FailureKind.Unavailable))
            }
        } catch (e: UnknownHostException) {
            emit(AssistantEvent.Failure(FailureKind.Offline))
        } catch (e: IOException) {
            // A cancelled call also surfaces as IOException — only report it if we are still collecting.
            if (!call.isCanceled()) emit(AssistantEvent.Failure(FailureKind.Unavailable))
        }
    }.flowOn(Dispatchers.IO)
}
