package com.example.spendsync.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutreachTest {
    @Test
    fun phoneNumbersBecomeInternationalDigits() {
        assertEquals("919876543210", Outreach.normalizePhone("98765 43210"))
        assertEquals("919876543210", Outreach.normalizePhone("098765-43210"))
        assertEquals("919876543210", Outreach.normalizePhone("+91 98765 43210"))
        assertEquals("919876543210", Outreach.normalizePhone("0091 9876543210"))
        assertEquals("919876543210", Outreach.normalizePhone("(91) 98765-43210"))
        assertEquals("447911123456", Outreach.normalizePhone("+44 7911 123456"))
        assertEquals("14155551234", Outreach.normalizePhone("(415) 555-1234", "1"))
    }

    @Test
    fun nonsenseIsRejected() {
        assertNull(Outreach.normalizePhone(""))
        assertNull(Outreach.normalizePhone("abc"))
        assertNull(Outreach.normalizePhone("12345"))
        assertNull(Outreach.normalizePhone("+1234567890123456789")) // longer than any real number
    }

    @Test
    fun linksAreEncodedForRupeesNewlinesAndEmoji() {
        val text = "Hi Asha 🙏\nCould you return ₹1,500 by 5 Oct?"
        val url = Outreach.whatsAppUrl("919876543210", text)
        assertEquals("https://wa.me/919876543210?text=Hi%20Asha%20%F0%9F%99%8F%0ACould%20you%20return%20%E2%82%B91%2C500%20by%205%20Oct%3F", url)
        assertEquals("https://wa.me/?text=Hi", Outreach.whatsAppUrl(null, "Hi")) // no number: WhatsApp asks who to send to
        assertEquals("smsto:+919876543210", Outreach.smsUri("919876543210"))
        assertEquals("mailto:a@b.com?subject=Reminder%20%26%20thanks&body=Line%201%0ALine%202", Outreach.mailtoUri("a@b.com", "Reminder & thanks", "Line 1\nLine 2"))
    }

    @Test
    fun regionsMapToCallingCodes() {
        assertEquals("91", Outreach.callingCodeFor("IN"))
        assertEquals("44", Outreach.callingCodeFor("gb"))
        assertEquals("91", Outreach.callingCodeFor(null)) // unknown region falls back to India
    }
}
