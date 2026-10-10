package com.example.spendsync.data.bills

import org.junit.Assert.assertEquals
import org.junit.Test

class BillImagesTest {
    @Test fun smallImagesKeepTheirSize() = assertEquals(1200 to 800, BillImages.targetSize(1200, 800))
    @Test fun longestSideIsCapped() {
        assertEquals(2400 to 1800, BillImages.targetSize(4000, 3000))
        assertEquals(1800 to 2400, BillImages.targetSize(3000, 4000))
    }
    @Test fun extremeStripsNeverReachZero() = assertEquals(2400 to 1, BillImages.targetSize(100_000, 10))
}
