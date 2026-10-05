package com.example.spendsync.data.planify

import com.example.spendsync.data.remote.model.SuggestionDto
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Turns a handful of multiple-choice answers plus the user's own history into a plan. Every number comes from
 * their transactions or from a percentage they picked, so it can be checked and nothing is invented.
 * The result always adds up to the money being planned, so nothing is left unassigned.
 */
object PlanGuide {
    data class Answers(
        val keepFixed: Boolean = true,
        val savePercent: Int = 10,
        /** Change to everyday limits against what was usually spent: -30, -15, 0 or +10. */
        val everydayChange: Int = 0,
        val bufferPercent: Int = 0,
        /** Per-category change in percent chosen in the AI guide, e.g. "Eating out" to -15. */
        val changes: Map<String, Int> = emptyMap(),
    )

    data class Item(val category: String, val kind: String, val limit: Double, val average: Double)

    data class Result(val items: List<Item>, val addedToSavings: Double)

    private fun round50(v: Double) = (v / 50.0).roundToLong() * 50.0

    /** Applies one option's effect, clamped to the same ranges the server allows. Unknown kinds do nothing. */
    fun apply(a: Answers, type: String, value: Double?, category: String?): Answers = when (type) {
        "savePercent" -> a.copy(savePercent = (value ?: 0.0).toInt().coerceIn(0, 40))
        "keepFixed" -> a.copy(keepFixed = (value ?: 1.0) != 0.0)
        "bufferPercent" -> a.copy(bufferPercent = (value ?: 0.0).toInt().coerceIn(0, 15))
        "categoryChange" -> if (category.isNullOrBlank() || value == null) a else a.copy(changes = a.changes + (category to value.toInt().coerceIn(-50, 30)))
        else -> a
    }

    fun fixedTotal(s: SuggestionDto) = s.items.filter { it.kind == "fixed" }.sumOf { it.limit }
    fun hasFixed(s: SuggestionDto) = s.items.any { it.kind == "fixed" }
    fun hasEveryday(s: SuggestionDto) = s.items.any { it.kind == "spend" }

    fun build(s: SuggestionDto, income: Double, a: Answers): Result {
        if (income <= 0.0) return Result(emptyList(), 0.0)
        val fixed = if (a.keepFixed) s.items.filter { it.kind == "fixed" }.map { Item(it.category, "fixed", it.limit, it.average) } else emptyList()
        var spend = s.items.filter { it.kind == "spend" }.map { Item(it.category, "spend", round50(it.limit * (1 + a.everydayChange / 100.0) * (1 + (a.changes.entries.firstOrNull { c -> c.key.equals(it.category, true) }?.value ?: 0) / 100.0)).coerceAtLeast(50.0), it.average) }
        var save = round50(income * a.savePercent / 100.0)
        val buffer = round50(income * a.bufferPercent / 100.0)

        // Bills are promised money and savings is a choice, so everyday limits give way first, then savings.
        val fixedSum = fixed.sumOf { it.limit }
        var room = income - fixedSum - save - buffer
        if (room < 0) { save = max(0.0, save + room); room = max(0.0, income - fixedSum - save - buffer) }
        val spendSum = spend.sumOf { it.limit }
        if (spendSum > room) {
            val f = if (spendSum > 0) room / spendSum else 0.0
            spend = spend.map { it.copy(limit = round50(it.limit * f)) }.filter { it.limit > 0 }
        }

        val items = (fixed + spend).toMutableList()
        if (buffer > 0) {
            val i = items.indexOfFirst { it.category == "Other" }
            if (i >= 0) items[i] = items[i].copy(limit = items[i].limit + buffer) else items += Item("Other", "spend", buffer, 0.0)
        }

        // Whatever is still unplanned goes to savings, so every rupee has a job.
        var planned = items.sumOf { it.limit } + save
        var diff = income - planned
        var added = 0.0
        if (diff > 0.5) { added = diff; save += diff }
        else if (diff < -0.5) {
            // rounding pushed us over: take it back from the largest everyday bucket
            val k = items.indices.filter { items[it].kind == "spend" }.maxByOrNull { items[it].limit }
            if (k != null) items[k] = items[k].copy(limit = max(0.0, items[k].limit + diff)) else save = max(0.0, save + diff)
        }
        if (save > 0.5) items += Item("Savings", "savings", save, 0.0)
        return Result(items.filter { it.limit > 0.5 }, added)
    }
}
