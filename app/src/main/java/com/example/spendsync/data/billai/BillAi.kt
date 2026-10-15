package com.example.spendsync.data.billai

import com.example.spendsync.data.remote.model.BillAiPrefsDto
import com.example.spendsync.data.remote.model.BillReadDto
import com.example.spendsync.utils.formatInr
import java.time.LocalDate
import kotlin.math.abs

/** Pure Bill AI helpers: pre-fill, mismatch, notes. Every number shown comes from parsed fields. */
object BillAi {
    const val MIN_CONFIDENCE = 0.5
    private const val MASK = "★★★★★"

    data class Prefill(val amount: Double?, val note: String?, val date: LocalDate?, val category: String?)

    /** Only for confident reads; never a future date; category only if the user has it (case-insensitive). */
    fun prefill(read: BillReadDto, today: LocalDate, categories: List<String>): Prefill? {
        if (read.confidence < MIN_CONFIDENCE) return null
        val date = read.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { if (it.isAfter(today)) today else it }
        val category = read.category?.let { c -> categories.firstOrNull { it.equals(c, ignoreCase = true) } }
        val p = Prefill(read.total?.takeIf { it > 0 }, read.merchant, date, category)
        return if (p.amount == null && p.note == null && p.date == null && p.category == null) null else p
    }

    /** More than ₹1 apart (rounding is not a mismatch). */
    fun differs(recorded: Double, total: Double?): Boolean = total != null && abs(recorded - total) > 1.0

    private val placeholder = Regex("""\{(total|subtotal|tax|discount|fees|item:(\d+))\}""")

    fun fillNote(note: String, read: BillReadDto, masked: Boolean): String = placeholder.replace(note) { m ->
        val v: Double? = when (m.groupValues[1]) {
            "total" -> read.total; "subtotal" -> read.subtotal; "tax" -> read.tax; "discount" -> read.discount; "fees" -> read.fees
            else -> read.items.getOrNull(m.groupValues[2].toInt() - 1)?.amount
        }
        when { v == null -> "—"; masked -> MASK; else -> formatInr(v) }
    }

    fun prefs(language: String, style: String, tone: String) = BillAiPrefsDto(language, style, tone)

    private val anyNumber = Regex("""(?:[₹$€£]\s?)?\p{Nd}[\p{Nd},.]*""")

    /** Model-written text (summary, item names) may contain amounts: while masked, every number is hidden. */
    fun maskText(text: String, masked: Boolean): String = if (!masked) text else anyNumber.replace(text, MASK)
}
