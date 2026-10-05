package com.example.spendsync.data.planify

import kotlin.math.ceil
import kotlin.math.max

/**
 * "Would this plan have worked?" Replays the user's own past months against a draft plan with plain arithmetic.
 * A month "held" when no bucket that has history went over its limit. Buckets without history (a new goal, an
 * estimate) are left out rather than guessed. With fewer than two months it says it cannot test properly.
 */
object BackTest {
    data class Row(val category: String, val kind: String, val limit: Double)

    data class Breach(val category: String, val monthsOver: Int, val peak: Double, val limit: Double)

    data class Outcome(
        val monthsTested: Int,
        val monthsHeld: Int,
        val breaches: List<Breach>,
        /** Everyday spending limits spread over the days of the month. */
        val perDay: Double,
        /** True with under two months of history: the result is shown as "can't test yet", not as good or bad. */
        val tooLittle: Boolean,
    )

    fun run(rows: List<Row>, monthly: Map<String, List<Double>>, monthCount: Int, daysInMonth: Int): Outcome {
        fun series(category: String) = monthly.entries.firstOrNull { it.key.equals(category, true) }?.value
        val tested = rows.filter { it.kind != "savings" && series(it.category) != null }
        val held = (0 until monthCount).count { m -> tested.all { (series(it.category)?.getOrNull(m) ?: 0.0) <= it.limit + 0.5 } }
        val breaches = tested.mapNotNull { r ->
            val s = series(r.category)!!.take(monthCount)
            val over = s.count { it > r.limit + 0.5 }
            if (over > 0) Breach(r.category, over, s.max(), r.limit) else null
        }.sortedByDescending { it.monthsOver }
        val perDay = rows.filter { it.kind == "spend" }.sumOf { it.limit } / max(1, daysInMonth)
        return Outcome(monthCount, held, breaches, perDay, tooLittle = monthCount < 2 || tested.isEmpty())
    }

    /** A limit that would have covered the peak, rounded up to a tidy 50. */
    fun fixLimit(b: Breach): Double = ceil(b.peak / 50.0) * 50.0

    /**
     * Raises one bucket's limit and pays for it so the plan still adds up to [income]: savings give first (down to
     * zero), then the other everyday buckets, largest first. Returns the new limit for every category.
     */
    fun raise(limits: Map<String, Double>, kinds: Map<String, String>, category: String, newLimit: Double, income: Double): Map<String, Double> {
        val out = LinkedHashMap(limits)
        out[category] = newLimit
        var excess = out.values.sum() - income
        fun take(keys: List<String>) {
            for (k in keys) {
                if (excess <= 0.5) return
                val cut = minOf(out[k] ?: 0.0, excess)
                out[k] = (out[k] ?: 0.0) - cut
                excess -= cut
            }
        }
        take(out.keys.filter { kinds[it] == "savings" }.sortedByDescending { out[it] })
        take(out.keys.filter { it != category && kinds[it] == "spend" }.sortedByDescending { out[it] })
        return out
    }
}
