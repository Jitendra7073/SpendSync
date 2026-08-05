package com.example.spendsync.notifications

import java.time.LocalDate
import java.time.ZoneId

internal const val HOLD_REMINDER_HOUR = 9

/**
 * Delay from [nowMillis] to 9 AM local time on [targetDate]. Clamped to
 * zero for a target that's already passed — WorkManager rejects a negative
 * initialDelay, and firing "late" for an overdue hold is fine, it's not
 * meant to be exact-time-critical.
 */
internal fun computeReminderDelayMillis(
    targetDate: LocalDate,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val targetMillis = targetDate.atTime(HOLD_REMINDER_HOUR, 0).atZone(zoneId).toInstant().toEpochMilli()
    return (targetMillis - nowMillis).coerceAtLeast(0L)
}
