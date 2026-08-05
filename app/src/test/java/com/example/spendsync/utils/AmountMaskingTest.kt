package com.example.spendsync.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountMaskingTest {

    @Test
    fun `amount above threshold and not visible is masked`() {
        assertTrue(shouldMaskAmount(1500.0, isVisible = false))
    }

    @Test
    fun `amount above threshold and visible is not masked`() {
        assertFalse(shouldMaskAmount(1500.0, isVisible = true))
    }

    @Test
    fun `amount exactly at threshold is not masked`() {
        assertFalse(shouldMaskAmount(1000.0, isVisible = false))
    }

    @Test
    fun `amount below threshold is never masked regardless of visibility`() {
        assertFalse(shouldMaskAmount(999.0, isVisible = false))
        assertFalse(shouldMaskAmount(999.0, isVisible = true))
    }
}
