package com.example.spendsync.data.assistant

/** The streamed reply as it builds up. Pure data, so the whole flow can be unit-tested. */
data class ReplyState(
    val text: String = "",
    val actions: List<String> = emptyList(),
    val followups: List<String> = emptyList(),
    /** Backend tool name currently running, for a "Checking your balance…" line. */
    val tool: String? = null,
    val failure: FailureKind? = null,
    val done: Boolean = false,
    /** Model that wrote the reply, e.g. "Gemma 4 31B · Google"; null for the built-in answer. */
    val source: String? = null,
    /** True when the built-in guide answered because no AI model was reachable. */
    val offline: Boolean = false,
    /** True after a tool ran: the next text starts a new paragraph instead of gluing onto the last. */
    val breakBefore: Boolean = false,
)

fun reduceReply(s: ReplyState, e: AssistantEvent): ReplyState = when (e) {
    is AssistantEvent.Delta -> s.copy(
        text = s.text + (if (s.breakBefore && s.text.isNotBlank()) "\n\n" else "") + e.text,
        tool = null,
        breakBefore = false,
    )
    is AssistantEvent.Tool ->
        if (e.status == ToolStatus.Running) s.copy(tool = e.name, breakBefore = s.text.isNotBlank())
        else s.copy(tool = null)
    is AssistantEvent.OpenScreen ->
        if (e.screen in s.actions) s else s.copy(actions = s.actions + e.screen)
    is AssistantEvent.Followups -> s.copy(followups = e.items)
    is AssistantEvent.Source ->
        if (e.id == "offline") s.copy(source = null, offline = true) else s.copy(source = e.label, offline = false)
    AssistantEvent.Reset -> s.copy(text = "", tool = null, breakBefore = false, followups = emptyList())
    AssistantEvent.Done -> s.copy(done = true, tool = null)
    is AssistantEvent.Failure -> s.copy(failure = e.kind, tool = null)
}

/** Replies that need no server round trip: instant, free and work offline. */
enum class FastReply { Greeting, Thanks, Capabilities }

object AssistantFastPath {
    private val greeting = Regex(
        """^(hi|hii+|hello|hey|hola|bonjour|salut|hallo|namaste|namaskar|नमस्ते|नमस्कार|good (morning|afternoon|evening))\b[\s!.,]*$""",
        RegexOption.IGNORE_CASE,
    )
    private val thanks = Regex(
        """^(thanks?|thank you|thx|ty|shukriya|dhanyavaad|धन्यवाद|शुक्रिया|gracias|merci|danke|danke schön)\b[\s!.,]*$""",
        RegexOption.IGNORE_CASE,
    )
    private val capabilities = Regex(
        """^(help|what can you do\??|what do you do\??|who are you\??|how can you help( me)?\??|ayuda|aide|hilfe|मदद|क्या कर सकते हो\??)[\s!.]*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Null when the message needs the real assistant. Only matches whole, short messages. */
    fun match(text: String): FastReply? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 40) return null
        return when {
            greeting.matches(t) -> FastReply.Greeting
            thanks.matches(t) -> FastReply.Thanks
            capabilities.matches(t) -> FastReply.Capabilities
            else -> null
        }
    }
}
