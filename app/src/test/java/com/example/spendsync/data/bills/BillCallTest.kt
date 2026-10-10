package com.example.spendsync.data.bills

import org.junit.Assert.assertEquals
import org.junit.Test

class BillCallTest {
    @Test fun clientErrorsArePermanentExceptTimeoutsAndRateLimits() {
        assertEquals(true, BillCall.isPermanent(400))
        assertEquals(true, BillCall.isPermanent(409))
        assertEquals(true, BillCall.isPermanent(422))
        assertEquals(false, BillCall.isPermanent(408))
        assertEquals(false, BillCall.isPermanent(429))
        assertEquals(false, BillCall.isPermanent(500))
        assertEquals(false, BillCall.isPermanent(503))
    }
}
