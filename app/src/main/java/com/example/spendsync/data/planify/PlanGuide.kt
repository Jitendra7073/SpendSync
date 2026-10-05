package com.example.spendsync.data.planify

import com.example.spendsync.data.remote.model.SuggestionDto
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Turns a handful of multiple-choice answers plus the user's own history into a plan. Every number comes from
 * their transactions, a percentage they picked, or an amount they typed, so it can be checked and nothing is
 * invented. Each bucket remembers where its number came from ([Item.basis]) so the screen can say so.
 * The result always adds up to the money being planned, so nothing is left unassigned.
 */
object PlanGuide {
    data class Goal(val name: String, val percent: Int)

    data class Answers(
        val keepFixed: Boolean = true,
        val savePercent: Int = 10,
        /** Change to everyday limits against what was usually spent: -30, -15, 0 or +10. */
        val everydayChange: Int = 0,
        val bufferPercent: Int = 0,
        /** Per-category change in percent chosen in the AI guide, e.g. "Eating out" to -15. */
        val changes: Map<String, Int> = emptyMap(),
        /** The money to plan with, when the guide had the user confirm it against their records. */
        val income: Double? = null,
        /** Plan on this many percent less than the income, because it varies from month to month. */
        val haircut: Int = 0,
        /** Named savings goals, each funded with a share of the income. */
        val goals: List<Goal> = emptyList(),
        /** Regular bills the user told us about that are not in their history: name to monthly amount. */
        val commitments: Map<String, Double> = emptyMap(),
        /** Monthly amounts the user stated for a category (when there is little history): category to amount. */
        val estimates: Map<String, Double> = emptyMap(),
    )

    /** [basis]: history, estimate, commitment, goal, choice (a share of income picked), cushion or leftover. */
    data class Item(val category: String, val kind: String, val limit: Double, val average: Double, val basis: String = "history", val months: Int = 0)

    data class Result(val items: List<Item>, val addedToSavings: Double, val income: Double = 0.0)

    private fun round50(v: Double) = (v / 50.0).roundToLong() * 50.0

    /** Applies one option's effect, clamped to the same ranges the server allows. Unknown kinds do nothing. */
    fun apply(a: Answers, type: String, value: Double?, category: String?, name: String? = null): Answers = when (type) {
        "savePercent" -> a.copy(savePercent = (value ?: 0.0).toInt().coerceIn(0, 40))
        "setIncome" -> if (value != null && value > 0) a.copy(income = value) else a
        "incomeHaircut" -> a.copy(haircut = (value ?: 0.0).toInt().coerceIn(0, 30))
        "keepFixed" -> a.copy(keepFixed = (value ?: 1.0) != 0.0)
        "bufferPercent" -> a.copy(bufferPercent = (value ?: 0.0).toInt().coerceIn(0, 15))
        "goalMonthly" -> if (name.isNullOrBlank() || value == null) a else a.copy(goals = a.goals.filterNot { it.name.equals(name, true) } + Goal(name.trim(), value.toInt().coerceIn(2, 30)))
        "commitment" -> if (name.isNullOrBlank() || value == null || value <= 0) a else a.copy(commitments = a.commitments + (name.trim() to value))
        "estimate" -> if (category.isNullOrBlank() || value == null || value < 0) a else a.copy(estimates = a.estimates + (category.trim() to value))
        "categoryChange" -> if (category.isNullOrBlank() || value == null) a else a.copy(changes = a.changes + (category to value.toInt().coerceIn(-50, 30)))
        else -> a
    }

    fun fixedTotal(s: SuggestionDto) = s.items.filter { it.kind == "fixed" }.sumOf { it.limit }
    fun hasFixed(s: SuggestionDto) = s.items.any { it.kind == "fixed" }
    fun hasEveryday(s: SuggestionDto) = s.items.any { it.kind == "spend" }

    fun build(s: SuggestionDto, plannedIncome: Double, a: Answers): Result {
        val income = (a.income ?: plannedIncome) * (1 - a.haircut / 100.0)
        if (income <= 0.0) return Result(emptyList(), 0.0, income)
        val months = s.monthsUsed
        fun named(map: Map<String, *>, c: String) = map.keys.any { it.equals(c, true) }

        // A bill the user typed replaces the same bill found in the history.
        val fixed = (if (a.keepFixed) s.items.filter { it.kind == "fixed" && !named(a.commitments, it.category) }.map { Item(it.category, "fixed", it.limit, it.average, "history", months) } else emptyList()) +
            a.commitments.map { (n, v) -> Item(n, "fixed", v, 0.0, "commitment") }

        // Typed estimates replace the history figure for that category.
        var spend = s.items.filter { it.kind == "spend" && !named(a.estimates, it.category) }.map {
            val change = (1 + a.everydayChange / 100.0) * (1 + (a.changes.entries.firstOrNull { c -> c.key.equals(it.category, true) }?.value ?: 0) / 100.0)
            Item(it.category, "spend", round50(it.limit * change).coerceAtLeast(50.0), it.average, "history", months)
        } + a.estimates.filter { it.value > 0 }.map { (c, v) -> Item(c, "spend", round50(v).coerceAtLeast(50.0), 0.0, "estimate") }

        var save = round50(income * a.savePercent / 100.0)
        var goals = a.goals.filterNot { it.name.equals("Savings", true) }.map { Item(it.name, "savings", round50(income * it.percent / 100.0), 0.0, "goal") }
        a.goals.firstOrNull { it.name.equals("Savings", true) }?.let { save += round50(income * it.percent / 100.0) }
        val buffer = round50(income * a.bufferPercent / 100.0)

        // Bills are promised money, savings and goals are choices: everyday limits give way first, then savings.
        val fixedSum = fixed.sumOf { it.limit }
        var room = income - fixedSum - save - goals.sumOf { it.limit } - buffer
        if (room < 0) {
            val cut = -room
            val saveCut = minOf(save, cut)
            save -= saveCut
            val left = cut - saveCut
            if (left > 0 && goals.isNotEmpty()) {
                val g = goals.sumOf { it.limit }
                goals = goals.map { it.copy(limit = round50(max(0.0, it.limit - left * it.limit / g))) }
            }
            room = max(0.0, income - fixedSum - save - goals.sumOf { it.limit } - buffer)
        }
        val spendSum = spend.sumOf { it.limit }
        if (spendSum > room) {
            val f = if (spendSum > 0) room / spendSum else 0.0
            spend = spend.map { it.copy(limit = round50(it.limit * f)) }.filter { it.limit > 0 }
        }

        val items = (fixed + spend).toMutableList()
        if (buffer > 0) {
            val i = items.indexOfFirst { it.category == "Other" }
            if (i >= 0) items[i] = items[i].copy(limit = items[i].limit + buffer) else items += Item("Other", "spend", buffer, 0.0, "cushion")
        }
        items += goals

        // Whatever is still unplanned goes to savings, so every rupee has a job.
        val diff = income - (items.sumOf { it.limit } + save)
        var added = 0.0
        if (diff > 0.5) { added = diff; save += diff }
        else if (diff < -0.5) {
            // rounding pushed us over: take it back from the largest everyday bucket
            val k = items.indices.filter { items[it].kind == "spend" }.maxByOrNull { items[it].limit }
            if (k != null) items[k] = items[k].copy(limit = max(0.0, items[k].limit + diff)) else save = max(0.0, save + diff)
        }
        if (save > 0.5) items += Item("Savings", "savings", save, 0.0, if (added > 0.5 && a.savePercent == 0) "leftover" else "choice")
        return Result(items.filter { it.limit > 0.5 }, added, income)
    }
}
