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

    fun chat(
        turns: List<ChatTurn>,
        language: String,
        screen: String?,
        conversationId: String,
        prefs: AssistantPrefs = AssistantPrefs(),
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
