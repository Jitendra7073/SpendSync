package com.example.spendsync.data.remote

import com.example.spendsync.data.planify.PlanMath
import com.example.spendsync.data.remote.model.PlanViewDto
import com.example.spendsync.data.remote.model.SuggestionDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app must not crash when the server it talks to has not been updated yet and omits newer fields. */
class PlanDtoCompatTest {
    private val gson = Gson()

    @Test
    fun planFromAnOlderServerHasEmptyAliasesAndMatches() {
        val json = """{"month":"2026-10","exists":true,"hasIncome":true,"income":1000,"carryOver":0,
          "status":{"daysInMonth":31,"day":5,"daysLeft":27,"monthProgressPercent":16,"available":1000,"planned":500,"leftToPlan":500,
          "spentInPlan":0,"buckets":[],"counts":{"ok":0,"close":0,"over":0},"safeToSpendToday":10,"unplanned":[],"unplannedTotal":0}}"""
        val plan = gson.fromJson(json, PlanViewDto::class.java)
        assertTrue(plan.aliases.isEmpty())
        assertTrue(plan.matches.isEmpty())
        assertNull(PlanMath.bucketFor(plan, "Food"))
    }

    @Test
    fun suggestionFromAnOlderServerStillReadsSafely() {
        val s = gson.fromJson("""{"items":[{"category":"Food","name":"Food","kind":"spend","limit":100,"average":100,"sortOrder":0}],"suggestedIncome":5000,"monthsUsed":2}""", SuggestionDto::class.java)
        assertEquals("", s.carryLabel)
        assertEquals("", s.incomeLabel)
        assertEquals("none", s.incomeSource)
        assertEquals("good", s.confidence)
        assertTrue(s.monthlySpend.isEmpty())
        assertEquals("average", s.items[0].reason)
    }
}
