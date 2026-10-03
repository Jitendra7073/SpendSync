package com.example.spendsync.data.assistant

import com.example.spendsync.utils.maskAmountsInText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantLogicTest {

    // ── SSE framing and event parsing ────────────────────────────────────────

    @Test
    fun sseJoinsDataLinesAndEndsOnBlankLine() {
        val sse = SseAccumulator()
        assertNull(sse.feed(": keep-alive"))
        assertNull(sse.feed("data: {\"type\":\"delta\","))
        assertNull(sse.feed("data: \"text\":\"Hi\"}"))
        assertEquals("{\"type\":\"delta\",\n\"text\":\"Hi\"}", sse.feed(""))
        assertNull(sse.feed("")) // stray blank line is ignored
    }

    @Test
    fun parsesEveryServerEvent() {
        assertEquals(AssistantEvent.Delta("Hello"), AssistantEventParser.parse("""{"type":"delta","text":"Hello"}"""))
        assertEquals(
            AssistantEvent.Tool("get_balance", ToolStatus.Running),
            AssistantEventParser.parse("""{"type":"tool","name":"get_balance","status":"running"}"""),
        )
        assertEquals(
            AssistantEvent.OpenScreen("budget"),
            AssistantEventParser.parse("""{"type":"ui_action","action":{"type":"open_screen","screen":"budget"}}"""),
        )
        assertEquals(AssistantEvent.Followups(listOf("A?", "B?")), AssistantEventParser.parse("""{"type":"followups","items":["A?","B?"]}"""))
        assertEquals(AssistantEvent.Done, AssistantEventParser.parse("""{"type":"done","usage":{"input":1,"output":2,"cacheRead":0}}"""))
        assertEquals(AssistantEvent.Failure(FailureKind.Busy), AssistantEventParser.parse("""{"type":"error","code":"busy"}"""))
        assertEquals(AssistantEvent.Failure(FailureKind.Refused), AssistantEventParser.parse("""{"type":"error","code":"refused"}"""))
        assertEquals(AssistantEvent.Failure(FailureKind.Unavailable), AssistantEventParser.parse("""{"type":"error","code":"???"}"""))
    }

    @Test
    fun sourceResetAndOfflineEventsDriveTheReply() {
        assertEquals(AssistantEvent.Source("gemini:g", "Gemma"), AssistantEventParser.parse("""{"type":"source","id":"gemini:g","label":"Gemma"}"""))
        assertEquals(AssistantEvent.Reset, AssistantEventParser.parse("""{"type":"reset"}"""))

        var s = reduceReply(ReplyState(), AssistantEvent.Source("gemini:g", "Gemma"))
        s = reduceReply(s, AssistantEvent.Delta("Half an ans"))
        s = reduceReply(s, AssistantEvent.Reset)
        s = reduceReply(s, AssistantEvent.Source("groq:b", "Llama"))
        s = reduceReply(s, AssistantEvent.Delta("Full answer."))
        assertEquals("Full answer.", s.text)
        assertEquals("Llama", s.source)

        val offline = reduceReply(ReplyState(source = "Llama"), AssistantEvent.Source("offline", "offline"))
        assertTrue(offline.offline)
        assertNull(offline.source)
    }

    @Test
    fun toolSetRoundTripsAndToleratesJunk() {
        assertEquals(setOf("a", "b"), parseToolSet(" b, a ,,"))
        assertEquals("a,b", serializeToolSet(setOf("b", "a")))
        assertEquals(emptySet<String>(), parseToolSet(null))
        // every tool shown as a switch is a real, unblocked tool name
        assertTrue(AssistantToolInfo.entries.none { t -> ASSISTANT_LOCKED_ACTIONS.any { it == t.id } })
    }

    @Test
    fun ignoresGarbageInsteadOfCrashing() {
        assertNull(AssistantEventParser.parse("not json"))
        assertNull(AssistantEventParser.parse("""{"type":"mystery"}"""))
        assertNull(AssistantEventParser.parse("""{"type":"ui_action","action":{"type":"delete_everything","screen":"x"}}"""))
    }

    // ── Reply building ───────────────────────────────────────────────────────

    @Test
    fun replyAccumulatesTextActionsAndFollowups() {
        val events = listOf(
            AssistantEvent.Tool("get_spending_summary", ToolStatus.Running),
            AssistantEvent.Tool("get_spending_summary", ToolStatus.Done),
            AssistantEvent.Delta("You spent "),
            AssistantEvent.Delta("₹500."),
            AssistantEvent.OpenScreen("analytics"),
            AssistantEvent.OpenScreen("analytics"), // duplicates collapse
            AssistantEvent.Followups(listOf("Why?")),
            AssistantEvent.Done,
        )
        val s = events.fold(ReplyState(), ::reduceReply)
        assertEquals("You spent ₹500.", s.text)
        assertEquals(listOf("analytics"), s.actions)
        assertEquals(listOf("Why?"), s.followups)
        assertTrue(s.done)
        assertNull(s.tool)
    }

    @Test
    fun toolStatusShowsWhileRunningAndClearsOnText() {
        var s = reduceReply(ReplyState(), AssistantEvent.Tool("list_holds", ToolStatus.Running))
        assertEquals("list_holds", s.tool)
        s = reduceReply(s, AssistantEvent.Delta("Raj owes you."))
        assertNull(s.tool)
    }

    @Test
    fun textAfterAToolStartsANewParagraph() {
        var s = reduceReply(ReplyState(), AssistantEvent.Delta("Let me check."))
        s = reduceReply(s, AssistantEvent.Tool("get_balance", ToolStatus.Running))
        s = reduceReply(s, AssistantEvent.Tool("get_balance", ToolStatus.Done))
        s = reduceReply(s, AssistantEvent.Delta("Your balance is ₹900."))
        assertEquals("Let me check.\n\nYour balance is ₹900.", s.text)
    }

    @Test
    fun failureIsRecordedAndKeepsPartialText() {
        var s = reduceReply(ReplyState(), AssistantEvent.Delta("Half an ans"))
        s = reduceReply(s, AssistantEvent.Failure(FailureKind.Unavailable))
        assertEquals(FailureKind.Unavailable, s.failure)
        assertEquals("Half an ans", s.text)
    }

    // ── Instant local replies ────────────────────────────────────────────────

    @Test
    fun fastPathHandlesOnlyShortSmallTalk() {
        assertEquals(FastReply.Greeting, AssistantFastPath.match("Hello!"))
        assertEquals(FastReply.Greeting, AssistantFastPath.match("  hola "))
        assertEquals(FastReply.Greeting, AssistantFastPath.match("नमस्ते"))
        assertEquals(FastReply.Thanks, AssistantFastPath.match("thank you"))
        assertEquals(FastReply.Capabilities, AssistantFastPath.match("what can you do?"))
        // Anything with substance goes to the real assistant.
        assertNull(AssistantFastPath.match("hi, how much did I spend on food?"))
        assertNull(AssistantFastPath.match("delete my account"))
        assertNull(AssistantFastPath.match(""))
    }

    // ── Amount masking in prose ──────────────────────────────────────────────

    @Test
    fun masksLargeAmountsOnlyWhileLocked() {
        val text = "You spent ₹12,450.50 on food and ₹300 on coffee."
        val locked = maskAmountsInText(text, maskingEnabled = true, unlocked = false)
        assertEquals("You spent ₹★★★★★ on food and ₹300 on coffee.", locked.text)
        assertTrue(locked.hidSomething)

        assertEquals(text, maskAmountsInText(text, maskingEnabled = true, unlocked = true).text)
        val off = maskAmountsInText(text, maskingEnabled = false, unlocked = false)
        assertEquals(text, off.text)
        assertFalse(off.hidSomething)
    }

    @Test
    fun amountsAtOrBelowTheThresholdStayVisible() {
        val r = maskAmountsInText("₹1,000 and ₹1,000.01", maskingEnabled = true, unlocked = false)
        assertEquals("₹1,000 and ₹★★★★★", r.text)
    }
}
