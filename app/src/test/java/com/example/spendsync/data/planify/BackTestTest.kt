package com.example.spendsync.data.planify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackTestTest {
    private val monthly = mapOf(
        "Food" to listOf(4000.0, 4200.0, 6000.0),
        "Rent" to listOf(12000.0, 12000.0, 12000.0),
        "Fun" to listOf(1000.0, 1500.0, 1200.0),
    )

    @Test
    fun countsMonthsWhereEverythingStayedInside() {
        val rows = listOf(BackTest.Row("Food", "spend", 5000.0), BackTest.Row("Rent", "fixed", 12000.0), BackTest.Row("Fun", "spend", 2000.0))
        val r = BackTest.run(rows, monthly, 3, 30)
        assertEquals(2, r.monthsHeld) // only September's Food (6,000) broke the 5,000 limit
        assertEquals(1, r.breaches.size)
        assertEquals("Food", r.breaches[0].category)
        assertEquals(6000.0, r.breaches[0].peak, 0.0)
        assertFalse(r.tooLittle)
        assertEquals(7000.0 / 30, r.perDay, 0.001) // everyday limits only, bills are not "spend"
    }

    @Test
    fun lookupIsCaseInsensitiveAndNewBucketsAreLeftOut() {
        val rows = listOf(BackTest.Row("food", "spend", 9999.0), BackTest.Row("Trip", "savings", 5000.0), BackTest.Row("Gym", "spend", 1.0))
        val r = BackTest.run(rows, monthly, 3, 30)
        assertEquals(3, r.monthsHeld) // Gym has no history so it cannot break anything; savings are never tested
        assertTrue(r.breaches.isEmpty())
    }

    @Test
    fun saysItCannotTestWithTooLittleHistory() {
        assertTrue(BackTest.run(listOf(BackTest.Row("Food", "spend", 100.0)), mapOf("Food" to listOf(5000.0)), 1, 30).tooLittle)
        assertTrue(BackTest.run(listOf(BackTest.Row("Gym", "spend", 100.0)), monthly, 3, 30).tooLittle) // nothing to test against
    }

    @Test
    fun fixLimitRoundsThePeakUp() {
        assertEquals(6000.0, BackTest.fixLimit(BackTest.Breach("Food", 1, 5951.0, 5000.0)), 0.0)
    }

    @Test
    fun raisingOneLimitIsPaidForBySavingsThenEverydaySpending() {
        val limits = linkedMapOf("Food" to 5000.0, "Fun" to 3000.0, "Savings" to 2000.0)
        val kinds = mapOf("Food" to "spend", "Fun" to "spend", "Savings" to "savings")
        val a = BackTest.raise(limits, kinds, "Food", 6000.0, 10000.0)
        assertEquals(10000.0, a.values.sum(), 0.001)
        assertEquals(1000.0, a["Savings"]!!, 0.001) // savings gave the 1,000
        val b = BackTest.raise(limits, kinds, "Food", 8000.0, 10000.0)
        assertEquals(10000.0, b.values.sum(), 0.001)
        assertEquals(0.0, b["Savings"]!!, 0.001)
        assertEquals(2000.0, b["Fun"]!!, 0.001) // then the other everyday bucket gave 1,000
    }
}
