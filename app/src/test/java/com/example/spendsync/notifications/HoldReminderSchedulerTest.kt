package com.example.spendsync.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HoldReminderSchedulerTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `computes positive delay for a future date`() {
        val now = ZonedDateTime.of(2026, 8, 3, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val target = LocalDate.of(2026, 8, 5)

        val delay = computeReminderDelayMillis(target, now, zone)

        val expectedTarget = ZonedDateTime.of(2026, 8, 5, HOLD_REMINDER_HOUR, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expectedTarget - now, delay)
    }

    @Test
    fun `clamps to zero for a date in the past`() {
        val now = ZonedDateTime.of(2026, 8, 10, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val target = LocalDate.of(2026, 8, 3)

        val delay = computeReminderDelayMillis(target, now, zone)

        assertEquals(0L, delay)
    }

    @Test
    fun `same-day before reminder hour gives a positive same-day delay`() {
        val now = ZonedDateTime.of(2026, 8, 5, 6, 0, 0, 0, zone).toInstant().toEpochMilli()
        val target = LocalDate.of(2026, 8, 5)

        val delay = computeReminderDelayMillis(target, now, zone)

        val expectedTarget = ZonedDateTime.of(2026, 8, 5, HOLD_REMINDER_HOUR, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expectedTarget - now, delay)
    }
}
