package com.example.spendsync.data.assistant

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Everything the server can stream to the phone (mirrors `AssistantEvent` in the backend). */
sealed interface AssistantEvent {
    data class Delta(val text: String) : AssistantEvent
    data class Tool(val name: String, val status: ToolStatus) : AssistantEvent
    /** The assistant offers an "Open <screen>" button; the user decides whether to tap it. */
    data class OpenScreen(val screen: String) : AssistantEvent
    data class Followups(val items: List<String>) : AssistantEvent
    /** Which model is answering (re-sent if a backup takes over). `id` == "offline" for the built-in answer. */
    data class Source(val id: String, val label: String) : AssistantEvent
    /** The model died mid-answer and a backup restarts it: drop the text shown so far. */
    data object Reset : AssistantEvent
    data class Proposed(val proposal: Proposal) : AssistantEvent
    /** The assistant started a follow-up message about a hold. The phone writes it; the user confirms before it is sent. */
    data class FollowUpStarted(val followUp: FollowUpProposal) : AssistantEvent
    data object Done : AssistantEvent
    data class Failure(val kind: FailureKind) : AssistantEvent
}

/** An entry the assistant prepared. Nothing is saved until the user taps Confirm. */
data class Proposal(
    val kind: String, // "expense" | "income"
    val amount: Double,
    val category: String,
    val note: String?,
    val person: String?,
    val returnDate: String?,
    val date: String,
)

/** Which hold a follow-up is about, plus optional choices the user already stated (channel, tone, context). */
data class FollowUpProposal(
    val person: String,
    val direction: String,
    val amount: Double,
    val dueDate: String,
    val overdueDays: Int,
    val channel: String?,
    val tone: String?,
    val context: String?,
)

enum class ToolStatus { Running, Done, Failed }

/** Why an answer didn't arrive — each maps to one plain-language message in the UI. */
enum class FailureKind { Offline, Busy, Unavailable, NotConfigured, Refused, SignedOut }

/** Turns one SSE `data:` payload into an event. Unknown or malformed payloads are ignored, never fatal. */
object AssistantEventParser {
    fun parse(data: String): AssistantEvent? = try {
        val o = JsonParser().parse(data).asJsonObject
        when (o.str("type")) {
            "delta" -> AssistantEvent.Delta(o.str("text").orEmpty())
            "tool" -> AssistantEvent.Tool(
                name = o.str("name").orEmpty(),
                status = when (o.str("status")) {
                    "running" -> ToolStatus.Running
                    "failed" -> ToolStatus.Failed
                    else -> ToolStatus.Done
                },
            )
            "ui_action" -> o.getAsJsonObject("action")?.let { a ->
                when (a.str("type")) {
                    "open_screen" -> a.str("screen")?.let { AssistantEvent.OpenScreen(it) }
                    "propose_entry" -> a.getAsJsonObject("entry")?.let { e ->
                        val amount = e.get("amount")?.takeIf { it.isJsonPrimitive }?.asDouble ?: return@let null
                        val kind = e.str("kind")?.takeIf { it == "expense" || it == "income" } ?: return@let null
                        AssistantEvent.Proposed(
                            Proposal(kind, amount, e.str("category") ?: "Other", e.str("note"), e.str("person"), e.str("returnDate"), e.str("date").orEmpty()),
                        )
                    }
                    "followup" -> a.getAsJsonObject("followup")?.let { f ->
                        val amount = f.get("amount")?.takeIf { it.isJsonPrimitive }?.asDouble ?: return@let null
                        val person = f.str("person") ?: return@let null
                        val direction = f.str("direction")?.takeIf { it == "owed_to_me" || it == "owed_by_me" } ?: return@let null
                        AssistantEvent.FollowUpStarted(
                            FollowUpProposal(
                                person, direction, amount, f.str("dueDate").orEmpty(),
                                f.get("overdueDays")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                                f.str("channel"), f.str("tone"), f.str("context"),
                            ),
                        )
                    }
                    else -> null
                }
            }
            "followups" -> AssistantEvent.Followups(
                o.getAsJsonArray("items")?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString } ?: emptyList(),
            )
            "source" -> AssistantEvent.Source(o.str("id").orEmpty(), o.str("label").orEmpty())
            "reset" -> AssistantEvent.Reset
            "done" -> AssistantEvent.Done
            "error" -> AssistantEvent.Failure(
                when (o.str("code")) {
                    "busy" -> FailureKind.Busy
                    "not_configured" -> FailureKind.NotConfigured
                    "refused" -> FailureKind.Refused
                    else -> FailureKind.Unavailable
                },
            )
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString
}

/**
 * Collects `data:` lines of a Server-Sent Events stream into complete payloads.
 * A blank line ends an event; multi-line data is joined with newlines; comments (`:`) are skipped.
 */
class SseAccumulator {
    private val buffer = StringBuilder()
    private var hasData = false

    /** Feed one line (without its line break); returns a finished payload when the event ends. */
    fun feed(line: String): String? {
        if (line.isEmpty()) {
            if (!hasData) return null
            val out = buffer.toString()
            buffer.clear()
            hasData = false
            return out
        }
        if (line.startsWith(":")) return null
        if (line.startsWith("data:")) {
            if (hasData) buffer.append('\n')
            buffer.append(line.removePrefix("data:").removePrefix(" "))
            hasData = true
        }
        return null
    }
}
