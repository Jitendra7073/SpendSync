package com.example.spendsync.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpEventTest {
    private val json = """{"type":"ui_action","action":{"type":"followup","followup":{"person":"Asha","direction":"owed_to_me","amount":1500,"dueDate":"2026-10-05","overdueDays":3,"channel":"whatsapp","tone":"gentle","context":"for the trip"}}}"""

    @Test
    fun aFollowUpActionBecomesACardNotAnAction() {
        val e = AssistantEventParser.parse(json) as AssistantEvent.FollowUpStarted
        assertEquals(FollowUpProposal("Asha", "owed_to_me", 1500.0, "2026-10-05", 3, "whatsapp", "gentle", "for the trip"), e.followUp)
        val s = reduceReply(ReplyState(), e)
        assertEquals(1, s.followUpCards.size)
        assertTrue(s.actions.isEmpty()) // it is a card, not an "Open screen" button
    }

    @Test
    fun malformedFollowUpsAreIgnoredNeverFatal() {
        assertNull(AssistantEventParser.parse("""{"type":"ui_action","action":{"type":"followup","followup":{"person":"A","direction":"sideways","amount":5}}}"""))
        assertNull(AssistantEventParser.parse("""{"type":"ui_action","action":{"type":"followup","followup":{"direction":"owed_to_me","amount":5}}}"""))
    }
}
