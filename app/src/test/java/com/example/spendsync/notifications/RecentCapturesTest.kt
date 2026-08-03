package com.example.spendsync.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentCapturesTest {

    @Test
    fun `not a duplicate when buffer is empty`() {
        val recentCaptures = RecentCaptures()
        assertFalse(recentCaptures.isDuplicate(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L))
    }

    @Test
    fun `same amount and direction within the window is a duplicate`() {
        val recentCaptures = RecentCaptures(windowMillis = 300_000L)
        recentCaptures.record(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L)

        assertTrue(recentCaptures.isDuplicate(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L + 60_000L))
    }

    @Test
    fun `same amount and direction outside the window is not a duplicate`() {
        val recentCaptures = RecentCaptures(windowMillis = 300_000L)
        recentCaptures.record(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L)

        assertFalse(recentCaptures.isDuplicate(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L + 400_000L))
    }

    @Test
    fun `different amount is not a duplicate`() {
        val recentCaptures = RecentCaptures(windowMillis = 300_000L)
        recentCaptures.record(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L)

        assertFalse(recentCaptures.isDuplicate(31.0, TransactionDirection.DEBIT, nowMillis = 1_000L))
    }

    @Test
    fun `different direction is not a duplicate`() {
        val recentCaptures = RecentCaptures(windowMillis = 300_000L)
        recentCaptures.record(30.0, TransactionDirection.DEBIT, nowMillis = 1_000L)

        assertFalse(recentCaptures.isDuplicate(30.0, TransactionDirection.CREDIT, nowMillis = 1_000L))
    }
}
