package com.example.spendsync.data.assistant

import com.example.spendsync.data.bills.BillCall
import com.example.spendsync.data.remote.model.EmailBillsResultDto
import org.junit.Assert.*
import org.junit.Test

class BillCardsTest {
    @Test fun parsesBillCardsAndEmailCards() {
        val b = AssistantEventParser.parse("""{"type":"ui_action","action":{"type":"bills","transactionIds":["a","b"]}}""")
        assertEquals(AssistantEvent.BillsFound(listOf("a", "b")), b)
        val e = AssistantEventParser.parse("""{"type":"ui_action","action":{"type":"email_bills","email":{"transactionIds":["a"],"files":2,"approxBytes":1000,"to":"j***@x.com"}}}""")
        assertEquals(AssistantEvent.EmailBillsStarted(EmailBillsProposal(listOf("a"), 2, 1000, "j***@x.com")), e)
        assertNull(AssistantEventParser.parse("""{"type":"ui_action","action":{"type":"bills","transactionIds":[]}}"""))
    }

    @Test fun reducerKeepsTheCards() {
        var s = ReplyState()
        s = reduceReply(s, AssistantEvent.BillsFound(listOf("a")))
        s = reduceReply(s, AssistantEvent.EmailBillsStarted(EmailBillsProposal(listOf("a"), 1, 10, "j***@x.com")))
        assertEquals(listOf(listOf("a")), s.billCards)
        assertEquals(1, s.emailCards.size)
    }

    @Test fun emailCardStates() {
        assertEquals(EmailCardState.Sent("j***@x.com", 2, 0, 0), EmailCard.from(BillCall.Ok(EmailBillsResultDto("j***@x.com", 2, 0, 0))))
        assertEquals(EmailCardState.Failed("Please wait 40 s", cooldown = true), EmailCard.from(BillCall.Fail("Please wait 40 s", permanent = false, code = 429)))
        assertEquals(EmailCardState.Failed("boom", cooldown = false), EmailCard.from(BillCall.Fail("boom", permanent = false, code = 502)))
    }
    @Test fun shareUsesTheOriginalPdfAndTheFullImage() {
        val img = com.example.spendsync.data.remote.model.BillDto("i", "t", 0, "ready", "jpg", null, "th", "full-url", "bl", listOf("full-url"), null)
        val pdf = img.copy(id = "p", format = "pdf", original = "pdf-url")
        assertEquals("full-url" to "jpg", com.example.spendsync.data.bills.BillShare.fileFor(img))
        assertEquals("pdf-url" to "pdf", com.example.spendsync.data.bills.BillShare.fileFor(pdf))
    }

    @Test fun emailCardStateSurvivesTheListForgettingTheCard() {
        val key = "m1:email:0"
        EmailCardMemory.reset(key)
        assertEquals(EmailCardState.Idle, EmailCardMemory.state(key))
        EmailCardMemory.set(key, EmailCardState.Sent("j***@x.com", 1, 0, 0))
        assertEquals(EmailCardState.Sent("j***@x.com", 1, 0, 0), EmailCardMemory.state(key)) // scrolled away and back
        EmailCardMemory.dismiss(key)
        assertTrue(EmailCardMemory.dismissed(key))
        EmailCardMemory.reset(key) // "Email to me" tapped again: a fresh card
        assertFalse(EmailCardMemory.dismissed(key)); assertEquals(EmailCardState.Idle, EmailCardMemory.state(key))
    }
}
