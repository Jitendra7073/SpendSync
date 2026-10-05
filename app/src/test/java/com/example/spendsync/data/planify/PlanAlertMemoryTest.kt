package com.example.spendsync.data.planify

import com.example.spendsync.data.planify.PlanMath.Level
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanAlertMemoryTest {
    @Test
    fun remembersPerMonthAndBucketAndSurvivesSaving() {
        val m = PlanAlertMemory.parse(null)
        m.set("2026-10", "Eating out", PlanAlertMemory.Entry(Level.Close, null))
        m.set("2026-10", "Transport", PlanAlertMemory.Entry(Level.Over, "2026-10-05"))
        val again = PlanAlertMemory.parse(m.serialize())
        assertEquals(Level.Close, again.get("2026-10", "Eating out").level)
        assertEquals(PlanAlertMemory.Entry(Level.Over, "2026-10-05"), again.get("2026-10", "Transport"))
        assertEquals(Level.None, again.get("2026-11", "Eating out").level) // another month starts clean
        assertEquals(Level.None, again.get("2026-10", "Fun").level)
    }

    @Test
    fun resettingToNothingForgetsTheRow() {
        val m = PlanAlertMemory.parse(null)
        m.set("2026-10", "Food", PlanAlertMemory.Entry(Level.Close, null))
        m.set("2026-10", "Food", PlanAlertMemory.Entry(Level.None, null))
        assertEquals("", m.serialize())
    }

    @Test
    fun oldMonthsAreDropped() {
        val m = PlanAlertMemory.parse(null)
        m.set("2026-07", "Food", PlanAlertMemory.Entry(Level.Over, "2026-07-30"))
        m.set("2026-10", "Food", PlanAlertMemory.Entry(Level.Close, null))
        m.keepMonths(setOf("2026-09", "2026-10"))
        assertEquals(Level.None, m.get("2026-07", "Food").level)
        assertEquals(Level.Close, m.get("2026-10", "Food").level)
    }

    @Test
    fun categoriesWithOddCharactersAndGarbageInputDoNotBreakIt() {
        val m = PlanAlertMemory.parse(null)
        m.set("2026-10", "Tea\tand\ncake", PlanAlertMemory.Entry(Level.Close, null))
        assertEquals(Level.Close, PlanAlertMemory.parse(m.serialize()).get("2026-10", "Tea and cake").level)
        assertEquals("", PlanAlertMemory.parse("nonsense without tabs\n\u0000\n").serialize()) // ignored, no crash
        assertEquals("", PlanAlertMemory.parse("2026-10\tFood\tNOT_A_LEVEL\t").serialize())
    }

    @Test
    fun dailyCapCountsPerDay() {
        assertEquals(0, PlanDailyCap.countFor(null, "2026-10-05"))
        val one = PlanDailyCap.bump(null, "2026-10-05")
        assertEquals(1, PlanDailyCap.countFor(one, "2026-10-05"))
        assertEquals(2, PlanDailyCap.countFor(PlanDailyCap.bump(one, "2026-10-05"), "2026-10-05"))
        assertEquals(0, PlanDailyCap.countFor(one, "2026-10-06")) // a new day starts at zero
        assertEquals(1, PlanDailyCap.countFor(PlanDailyCap.bump(one, "2026-10-06"), "2026-10-06"))
    }
}
