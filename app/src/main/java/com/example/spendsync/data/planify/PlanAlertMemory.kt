package com.example.spendsync.data.planify

import com.example.spendsync.data.planify.PlanMath.Level

/**
 * Remembers, per month and bucket, the highest alert already shown and the last day an "over" alert fired.
 * Stored as plain text in DataStore: one line per bucket, tab separated. Old months are dropped on save.
 */
class PlanAlertMemory private constructor(private val rows: MutableMap<Pair<String, String>, Entry>) {
    data class Entry(val level: Level, val overDay: String?)

    fun get(month: String, category: String): Entry = rows[month to category] ?: Entry(Level.None, null)

    fun set(month: String, category: String, entry: Entry) {
        if (entry.level == Level.None && entry.overDay == null) rows.remove(month to category) else rows[month to category] = entry
    }

    /** Keeps only the given months so the stored text never grows without limit. */
    fun keepMonths(months: Set<String>) { rows.keys.retainAll { it.first in months } }

    fun serialize(): String =
        rows.entries.joinToString("\n") { (k, v) -> listOf(k.first, clean(k.second), v.level.name, v.overDay.orEmpty()).joinToString("\t") }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    companion object {
        fun parse(raw: String?): PlanAlertMemory {
            val rows = mutableMapOf<Pair<String, String>, Entry>()
            raw.orEmpty().lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val p = line.split('\t')
                if (p.size >= 3) {
                    val level = Level.entries.firstOrNull { it.name == p[2] } ?: return@forEach
                    rows[p[0] to p[1]] = Entry(level, p.getOrNull(3)?.takeIf { it.isNotEmpty() })
                }
            }
            return PlanAlertMemory(rows)
        }
    }
}

/** "2026-10-05:2" means two Planify notifications have been shown on that day. At most [LIMIT] a day. */
object PlanDailyCap {
    const val LIMIT = 3

    fun countFor(raw: String?, today: String): Int {
        val p = raw.orEmpty().split(':')
        return if (p.size == 2 && p[0] == today) p[1].toIntOrNull() ?: 0 else 0
    }

    fun bump(raw: String?, today: String): String = "$today:${countFor(raw, today) + 1}"
}
