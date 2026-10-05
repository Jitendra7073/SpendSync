package com.example.spendsync.data.planify

import com.example.spendsync.data.remote.model.SuggestedItemDto
import com.example.spendsync.data.remote.model.SuggestionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanGuideTest {
    private fun item(c: String, kind: String, limit: Double) = SuggestedItemDto(c, c, kind, limit, limit, 0)
    private val history = SuggestionDto(
        items = listOf(item("Rent", "fixed", 12000.0), item("Groceries", "spend", 6000.0), item("Eating out", "spend", 3000.0)),
        suggestedIncome = 40000.0, monthsUsed = 3,
    )

    @Test
    fun everyRupeeGetsAJobAndNothingExceedsTheIncome() {
        listOf(0, 10, 20, 30).forEach { save ->
            listOf(-30, -15, 0, 10).forEach { change ->
                listOf(0, 5, 10).forEach { buf ->
                    val r = PlanGuide.build(history, 40000.0, PlanGuide.Answers(true, save, change, buf))
                    assertEquals("save=$save change=$change buf=$buf", 40000.0, r.items.sumOf { it.limit }, 1.0)
                    assertTrue(r.items.all { it.limit > 0 })
                }
            }
        }
    }

    @Test
    fun savingsAmountFollowsThePercentage() {
        val r = PlanGuide.build(history, 40000.0, PlanGuide.Answers(true, 20, -30, 0))
        assertTrue(r.items.first { it.category == "Savings" }.limit >= 8000.0) // 20% of 40,000, plus anything left over
        assertEquals(12000.0, r.items.first { it.category == "Rent" }.limit, 0.0)
    }

    @Test
    fun skippingBillsDropsThem() {
        val r = PlanGuide.build(history, 40000.0, PlanGuide.Answers(false, 10, 0, 0))
        assertTrue(r.items.none { it.category == "Rent" })
    }

    @Test
    fun smallIncomeShrinksEverydayLimitsBeforeBills() {
        val r = PlanGuide.build(history, 16000.0, PlanGuide.Answers(true, 10, 0, 0))
        assertEquals(12000.0, r.items.first { it.category == "Rent" }.limit, 0.0)
        assertTrue(r.items.sumOf { it.limit } <= 16001.0)
    }

    @Test
    fun noHistoryStillGivesASavingsBucketAndNoIncomeGivesNothing() {
        val empty = SuggestionDto(emptyList(), 0.0, 0)
        val r = PlanGuide.build(empty, 30000.0, PlanGuide.Answers(true, 10, 0, 5))
        assertEquals(30000.0, r.items.sumOf { it.limit }, 1.0)
        assertTrue(PlanGuide.build(history, 0.0, PlanGuide.Answers()).items.isEmpty())
    }

    @Test
    fun aiEffectsAreClampedAndApplied() {
        val a = PlanGuide.apply(PlanGuide.Answers(), "savePercent", 99.0, null)
        assertEquals(40, a.savePercent)
        val b = PlanGuide.apply(a, "categoryChange", -90.0, "Eating out")
        assertEquals(-50, b.changes["Eating out"])
        assertEquals(b, PlanGuide.apply(b, "somethingNew", 5.0, null)) // unknown effects are ignored
        val r = PlanGuide.build(history, 40000.0, PlanGuide.Answers(true, 10, 0, 0, mapOf("eating out" to -50)))
        assertEquals(1500.0, r.items.first { it.category == "Eating out" }.limit, 50.0) // 3,000 halved, case-insensitive
    }

    @Test
    fun billsGoalsEstimatesAndHaircutAllShapeThePlanAndStillAddUp() {
        var a = PlanGuide.Answers(savePercent = 10)
        a = PlanGuide.apply(a, "commitment", 9000.0, null, "Insurance")
        a = PlanGuide.apply(a, "goalMonthly", 5.0, null, "Emergency fund")
        a = PlanGuide.apply(a, "estimate", 4000.0, "Groceries", null)
        a = PlanGuide.apply(a, "incomeHaircut", 10.0, null, null)
        val r = PlanGuide.build(history, 40000.0, a)
        assertEquals(36000.0, r.income, 0.0) // 10% less because the income varies
        assertEquals(36000.0, r.items.sumOf { it.limit }, 1.0)
        assertEquals("commitment", r.items.first { it.category == "Insurance" }.basis)
        assertEquals("goal", r.items.first { it.category == "Emergency fund" }.basis)
        assertEquals("estimate", r.items.first { it.category == "Groceries" }.basis) // typed figure replaced the history one
        assertEquals(4000.0, r.items.first { it.category == "Groceries" }.limit, 50.0)
        assertEquals("history", r.items.first { it.category == "Rent" }.basis)
    }

    @Test
    fun aTypedBillReplacesTheSameBillFromHistoryAndGoalsGiveWayBeforeBills() {
        var a = PlanGuide.apply(PlanGuide.Answers(savePercent = 0), "commitment", 13000.0, null, "rent")
        a = PlanGuide.apply(a, "goalMonthly", 30.0, null, "Trip")
        val r = PlanGuide.build(history, 20000.0, a)
        assertEquals(1, r.items.count { it.category.equals("Rent", true) })
        assertEquals(13000.0, r.items.first { it.category.equals("Rent", true) }.limit, 0.0)
        assertTrue(r.items.sumOf { it.limit } <= 20001.0)
    }

    @Test
    fun badEffectsAreIgnored() {
        val a = PlanGuide.Answers()
        assertEquals(a, PlanGuide.apply(a, "commitment", null, null, "Rent"))
        assertEquals(a, PlanGuide.apply(a, "estimate", -5.0, "Groceries", null))
        assertEquals(a, PlanGuide.apply(a, "goalMonthly", 10.0, null, " "))
    }
}
