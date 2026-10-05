package com.example.spendsync.data.planify

import com.example.spendsync.data.remote.model.BucketDto
import com.example.spendsync.data.remote.model.PlanViewDto
import java.time.LocalDate
import java.time.YearMonth

/**
 * Planify rules that run on the phone. The server does the heavy maths (see backend `planify/status.ts`); this
 * covers what must work instantly and offline: how a spend would change a bucket, which alert to show and when,
 * and whether a credit looks like a salary. Pure functions, no Android, fully unit-tested.
 */
object PlanMath {
    const val CLOSE_AT = 80.0

    /** How worried the user should be about a bucket, lowest to highest. */
    enum class Level(val rank: Int) { None(0), Close(1), Reached(2), Over(3) }

    private fun r2(x: Double) = Math.round(x * 100.0) / 100.0

    /** Only everyday ("spend") buckets raise alerts. Bills and savings are shown, not nagged about. */
    fun levelOf(kind: String, limit: Double, spent: Double): Level {
        if (kind != "spend" || limit <= 0.0) return Level.None
        val s = r2(spent)
        return when {
            s > limit -> Level.Over
            s >= limit -> Level.Reached
            s / limit * 100.0 >= CLOSE_AT -> Level.Close
            else -> Level.None
        }
    }

    fun levelOf(b: BucketDto): Level = levelOf(b.kind, b.limit, b.spent)

    /** What a bucket would look like after one more spend: shown on Add expense before the user saves. */
    data class After(val spent: Double, val remaining: Double, val percent: Double, val level: Level, val overBy: Double)

    fun afterSpend(b: BucketDto, amount: Double): After {
        val spent = r2(b.spent + amount)
        val remaining = r2(b.limit - spent)
        val percent = if (b.limit > 0) minOf(999.0, r2(spent / b.limit * 100.0)) else if (spent > 0) 999.0 else 0.0
        return After(spent, remaining, percent, levelOf(b.kind, b.limit, spent), overBy = if (remaining < 0) -remaining else 0.0)
    }

    fun bucketFor(plan: PlanViewDto?, category: String): BucketDto? =
        plan?.status?.buckets?.firstOrNull { it.category.equals(category, ignoreCase = true) }

    /** Result of deciding whether to notify: what to show (or null), and what to remember for next time. */
    data class AlertDecision(val fire: Level?, val notified: Level, val overDay: String?)

    /**
     * Each level is announced once per bucket per month. If spending falls back (an edit or a refund), the memory
     * steps down so crossing the line again can warn again. "Over" can repeat, at most once a day.
     */
    fun decideAlert(current: Level, notified: Level, lastOverDay: String?, today: String): AlertDecision = when {
        current == Level.None -> AlertDecision(null, Level.None, null)
        current.rank < notified.rank -> AlertDecision(null, current, if (current == Level.Over) lastOverDay else null)
        current.rank > notified.rank -> AlertDecision(current, current, if (current == Level.Over) today else lastOverDay)
        current == Level.Over && lastOverDay != today -> AlertDecision(Level.Over, Level.Over, today)
        else -> AlertDecision(null, notified, lastOverDay)
    }

    /** True for a credit that is probably a salary: big enough, and named or categorised like one. */
    fun looksLikeSalary(type: String, amount: Double, category: String, merchant: String, note: String?, minAmount: Double): Boolean {
        if (type != "credit" || amount < minAmount) return false
        if (category.equals("Salary", ignoreCase = true)) return true
        return SALARY_WORDS.containsMatchIn("$merchant ${note.orEmpty()}")
    }
    private val SALARY_WORDS = Regex("""\b(salary|payroll|stipend|wages?|ctc)\b""", RegexOption.IGNORE_CASE)

    fun monthKey(date: LocalDate): String = YearMonth.from(date).toString() // "2026-10"

    fun nextMonth(month: String): String = YearMonth.parse(month).plusMonths(1).toString()

    fun previousMonth(month: String): String = YearMonth.parse(month).minusMonths(1).toString()

    /** Today still counts, so on the last day there is 1 day left. */
    fun daysLeft(today: LocalDate): Int = today.lengthOfMonth() - today.dayOfMonth + 1

    /** One short line about how a bucket is doing, as a stable code the UI turns into words. */
    enum class Verdict { OnTrack, Close, Reached, Over, Paid, Saved }

    fun verdict(b: BucketDto): Verdict = when (b.state) {
        "over" -> Verdict.Over
        "close" -> if (levelOf(b) == Level.Reached) Verdict.Reached else Verdict.Close
        "paid" -> Verdict.Paid
        "saved" -> Verdict.Saved
        else -> if (levelOf(b) == Level.Reached) Verdict.Reached else Verdict.OnTrack
    }
}
