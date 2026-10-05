package com.example.spendsync.data.planify

import com.example.spendsync.data.planify.PlanMath.AlertDecision
import com.example.spendsync.data.planify.PlanMath.Level
import com.example.spendsync.data.remote.model.BucketDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanMathTest {
    private fun bucket(kind: String = "spend", limit: Double = 1000.0, spent: Double = 0.0, state: String = "ok") = BucketDto(
        id = "b", category = "Food", name = "Food", kind = kind, limit = limit, spent = spent, sortOrder = 0, rollover = false,
        remaining = limit - spent, percent = if (limit > 0) spent / limit * 100 else 0.0, state = state,
        paceRatio = null, projected = null, runsOutOnDay = null, paceWarning = false,
    )

    @Test
    fun levelsFollowTheSoftLimit() {
        assertEquals(Level.None, PlanMath.levelOf("spend", 1000.0, 790.0))
        assertEquals(Level.Close, PlanMath.levelOf("spend", 1000.0, 800.0))
        assertEquals(Level.Reached, PlanMath.levelOf("spend", 1000.0, 1000.0)) // at the limit is reached, not over
        assertEquals(Level.Over, PlanMath.levelOf("spend", 1000.0, 1000.01))
    }

    @Test
    fun billsSavingsAndZeroLimitsNeverAlert() {
        assertEquals(Level.None, PlanMath.levelOf("fixed", 12000.0, 20000.0))
        assertEquals(Level.None, PlanMath.levelOf("savings", 6000.0, 9000.0))
        assertEquals(Level.None, PlanMath.levelOf("spend", 0.0, 50.0))
    }

    @Test
    fun previewShowsWhatOneMoreSpendDoes() {
        val eatingOut = bucket(limit = 2000.0, spent = 1640.0, state = "close")
        val after = PlanMath.afterSpend(eatingOut, 420.0)
        assertEquals(2060.0, after.spent, 0.001)
        assertEquals(-60.0, after.remaining, 0.001)
        assertEquals(60.0, after.overBy, 0.001)
        assertEquals(Level.Over, after.level)

        val calm = PlanMath.afterSpend(bucket(limit = 2000.0, spent = 500.0), 100.0)
        assertEquals(Level.None, calm.level)
        assertEquals(1400.0, calm.remaining, 0.001)
        assertEquals(0.0, calm.overBy, 0.001)
    }

    @Test
    fun eachLevelIsAnnouncedOnce() {
        assertEquals(AlertDecision(Level.Close, Level.Close, null), PlanMath.decideAlert(Level.Close, Level.None, null, "2026-10-05"))
        assertEquals(AlertDecision(null, Level.Close, null), PlanMath.decideAlert(Level.Close, Level.Close, null, "2026-10-06")) // already told
        assertEquals(AlertDecision(Level.Reached, Level.Reached, null), PlanMath.decideAlert(Level.Reached, Level.Close, null, "2026-10-07"))
        // a big spend jumps straight to Over: one alert, the highest level
        assertEquals(AlertDecision(Level.Over, Level.Over, "2026-10-08"), PlanMath.decideAlert(Level.Over, Level.None, null, "2026-10-08"))
    }

    @Test
    fun overRepeatsAtMostOncePerDay() {
        assertNull(PlanMath.decideAlert(Level.Over, Level.Over, "2026-10-08", "2026-10-08").fire) // same day: quiet
        assertEquals(Level.Over, PlanMath.decideAlert(Level.Over, Level.Over, "2026-10-08", "2026-10-09").fire) // next day, still going
    }

    @Test
    fun spendingFallingBackResetsTheMemory() {
        // a refund or an edit brought it back under the limit...
        val down = PlanMath.decideAlert(Level.Close, Level.Over, "2026-10-08", "2026-10-09")
        assertNull(down.fire)
        assertEquals(Level.Close, down.notified)
        // ...so crossing the line again can warn again
        assertEquals(Level.Reached, PlanMath.decideAlert(Level.Reached, down.notified, down.overDay, "2026-10-10").fire)
        assertEquals(AlertDecision(null, Level.None, null), PlanMath.decideAlert(Level.None, Level.Over, "2026-10-08", "2026-10-09"))
    }

    @Test
    fun salaryDetection() {
        assertTrue(PlanMath.looksLikeSalary("credit", 45000.0, "Salary", "ACME", null, 5000.0))
        assertTrue(PlanMath.looksLikeSalary("credit", 45000.0, "Other", "ACME PAYROLL PVT LTD", null, 5000.0))
        assertTrue(PlanMath.looksLikeSalary("credit", 12000.0, "Other", "Bank", "October Salary", 5000.0))
        assertFalse(PlanMath.looksLikeSalary("credit", 300.0, "Salary", "ACME", null, 5000.0)) // too small
        assertFalse(PlanMath.looksLikeSalary("debit", 45000.0, "Salary", "ACME", null, 5000.0)) // not a credit
        assertFalse(PlanMath.looksLikeSalary("credit", 45000.0, "Gift", "Friend", "birthday", 5000.0)) // big, but not a salary
        assertFalse(PlanMath.looksLikeSalary("credit", 45000.0, "Other", "Salaryman Cafe refund", null, 5000.0)) // the word must stand alone
    }

    @Test
    fun monthHelpers() {
        assertEquals("2026-10", PlanMath.monthKey(LocalDate.of(2026, 10, 5)))
        assertEquals("2027-01", PlanMath.nextMonth("2026-12"))
        assertEquals("2025-12", PlanMath.previousMonth("2026-01"))
        assertEquals(27, PlanMath.daysLeft(LocalDate.of(2026, 10, 5))) // 31 - 5 + 1
        assertEquals(1, PlanMath.daysLeft(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun verdictsForTheUi() {
        assertEquals(PlanMath.Verdict.OnTrack, PlanMath.verdict(bucket(spent = 100.0)))
        assertEquals(PlanMath.Verdict.Close, PlanMath.verdict(bucket(spent = 850.0, state = "close")))
        assertEquals(PlanMath.Verdict.Reached, PlanMath.verdict(bucket(spent = 1000.0, state = "close")))
        assertEquals(PlanMath.Verdict.Over, PlanMath.verdict(bucket(spent = 1200.0, state = "over")))
        assertEquals(PlanMath.Verdict.Paid, PlanMath.verdict(bucket(kind = "fixed", spent = 1000.0, state = "paid")))
        assertEquals(PlanMath.Verdict.Saved, PlanMath.verdict(bucket(kind = "savings", spent = 1000.0, state = "saved")))
    }
}
