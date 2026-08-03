package com.example.spendsync.notifications

/**
 * In-memory, service-lifetime dedup buffer. Covers the case where a single
 * real-world payment fires two notifications (e.g. the bank's SMS and the
 * UPI app's own confirmation) — the second one within [windowMillis] of a
 * matching amount+direction is treated as a duplicate.
 */
class RecentCaptures(private val windowMillis: Long = 300_000L) {

    private data class Signature(val amount: Double, val direction: TransactionDirection, val timestampMillis: Long)

    private val recent = mutableListOf<Signature>()

    fun isDuplicate(amount: Double, direction: TransactionDirection, nowMillis: Long): Boolean {
        recent.removeAll { nowMillis - it.timestampMillis > windowMillis }
        return recent.any { it.amount == amount && it.direction == direction }
    }

    fun record(amount: Double, direction: TransactionDirection, nowMillis: Long) {
        recent.add(Signature(amount, direction, nowMillis))
    }
}
