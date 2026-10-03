package com.example.spendsync.data.assistant

/** What the assistant is doing behind the scenes, one line per step, shown to the user as it happens. */
enum class StepKind { Understand, Model, Backup, Tool, Prepare, Write }

enum class StepState { Running, Done, Failed }

data class ActivityStep(val kind: StepKind, val arg: String = "", val state: StepState)

/** The streamed reply as it builds up. Pure data, so the whole flow can be unit-tested. */
data class ReplyState(
    val text: String = "",
    val actions: List<String> = emptyList(),
    val proposals: List<Proposal> = emptyList(),
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
    /** The activity log. Starts with "understanding your message" the moment the question is sent. */
    val steps: List<ActivityStep> = listOf(ActivityStep(StepKind.Understand, "", StepState.Running)),
)

private fun List<ActivityStep>.settleUnderstand(): List<ActivityStep> =
    map { if (it.kind == StepKind.Understand && it.state == StepState.Running) it.copy(state = StepState.Done) else it }

private fun List<ActivityStep>.settleAll(to: StepState): List<ActivityStep> =
    map { if (it.state == StepState.Running) it.copy(state = to) else it }

fun reduceReply(s: ReplyState, e: AssistantEvent): ReplyState = when (e) {
    is AssistantEvent.Delta -> s.copy(
        text = s.text + (if (s.breakBefore && s.text.isNotBlank()) "\n\n" else "") + e.text,
        tool = null,
        breakBefore = false,
        steps = s.steps.settleUnderstand().let { st ->
            if (st.any { it.kind == StepKind.Write }) st else st + ActivityStep(StepKind.Write, "", StepState.Running)
        },
    )
    is AssistantEvent.Tool ->
        if (e.status == ToolStatus.Running) s.copy(
            tool = e.name,
            breakBefore = s.text.isNotBlank(),
            steps = s.steps.settleUnderstand() + ActivityStep(StepKind.Tool, e.name, StepState.Running),
        ) else s.copy(
            tool = null,
            steps = s.steps.map {
                if (it.kind == StepKind.Tool && it.arg == e.name && it.state == StepState.Running) {
                    it.copy(state = if (e.status == ToolStatus.Failed) StepState.Failed else StepState.Done)
                } else it
            },
        )
    is AssistantEvent.OpenScreen ->
        if (e.screen in s.actions) s else s.copy(actions = s.actions + e.screen)
    is AssistantEvent.Proposed -> s.copy(
        proposals = (s.proposals + e.proposal).takeLast(3),
        steps = if (s.steps.any { it.kind == StepKind.Prepare }) s.steps else s.steps + ActivityStep(StepKind.Prepare, "", StepState.Done),
    )
    is AssistantEvent.Followups -> s.copy(followups = e.items)
    is AssistantEvent.Source ->
        if (e.id == "offline") s.copy(source = null, offline = true, steps = s.steps.settleUnderstand())
        else s.copy(
            source = e.label,
            offline = false,
            steps = when {
                s.source == null -> s.steps.settleUnderstand() + ActivityStep(StepKind.Model, e.label, StepState.Done)
                s.source != e.label -> s.steps + ActivityStep(StepKind.Backup, e.label, StepState.Done)
                else -> s.steps
            },
        )
    AssistantEvent.Reset -> s.copy(
        text = "", tool = null, breakBefore = false, followups = emptyList(),
        steps = s.steps.settleAll(StepState.Failed) + ActivityStep(StepKind.Backup, "", StepState.Failed),
    )
    AssistantEvent.Done -> s.copy(done = true, tool = null, steps = s.steps.settleAll(StepState.Done))
    is AssistantEvent.Failure -> s.copy(failure = e.kind, tool = null, steps = s.steps.settleAll(StepState.Failed))
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

/** "850 ms" under a second, "2.4 s" after. Shown under every answer. */
fun formatElapsed(ms: Long): String = if (ms < 1000) "$ms ms" else String.format(java.util.Locale.US, "%.1f s", ms / 1000.0)
