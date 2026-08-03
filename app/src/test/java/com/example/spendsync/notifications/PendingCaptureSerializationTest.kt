package com.example.spendsync.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingCaptureSerializationTest {

    @Test
    fun `parses null as empty list`() {
        assertTrue(parsePendingCaptures(null).isEmpty())
    }

    @Test
    fun `parses blank string as empty list`() {
        assertTrue(parsePendingCaptures("").isEmpty())
    }

    @Test
    fun `round trips a list of pending captures`() {
        val captures = listOf(
            PendingCapture(30.0, TransactionDirection.DEBIT, "MANISH DINESHPRA", "com.phonepe.app", 1_000L),
            PendingCapture(500.0, TransactionDirection.CREDIT, null, "com.google.android.apps.messaging", 2_000L),
        )

        val serialized = serializePendingCaptures(captures)
        val parsed = parsePendingCaptures(serialized)

        assertEquals(captures, parsed)
    }
}
