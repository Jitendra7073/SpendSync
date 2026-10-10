package com.example.spendsync.data.bills

import com.example.spendsync.data.remote.model.BillReservationDto
import org.junit.Assert.*
import org.junit.Test

class BillQueueRulesTest {
    private val base = QueuedBill(clientKey = "k", txId = "t", localPath = "/x", mime = "image/jpeg", bytes = 10, position = null, replaces = null)

    @Test fun newItemNeedsAReservation() = assertTrue(BillQueueRules.needsSign(base, now = 0))

    @Test fun freshSignatureIsKeptAndOldOneRenewed() {
        val signed = base.copy(billId = "b", params = mapOf("a" to "1"), uploadUrl = "u", signedAt = 1_000)
        assertFalse(BillQueueRules.needsSign(signed, now = 1_000 + BillQueueRules.RESIGN_AFTER_MS - 1))
        assertTrue(BillQueueRules.needsSign(signed, now = 1_000 + BillQueueRules.RESIGN_AFTER_MS))
    }

    @Test fun alreadyUploadedNeverResigns() {
        val up = base.copy(billId = "b", uploaded = Uploaded("p", 1, "s", "jpg", 10, null, null, null), signedAt = 0)
        assertFalse(BillQueueRules.needsSign(up, now = Long.MAX_VALUE))
    }

    @Test fun readyReservationFinishesTheItem() {
        assertNull(BillQueueRules.afterReserve(base, BillReservationDto("b", "ready", null, null), now = 5))
        val pending = BillQueueRules.afterReserve(base, BillReservationDto("b", "pending", "u", mapOf("k" to "v")), now = 5)!!
        assertEquals("b", pending.billId); assertEquals(5L, pending.signedAt); assertEquals(QueuedBill.State.Waiting, pending.state)
    }

    @Test fun failingKeepsTheFileForRetry() {
        val f = BillQueueRules.failed(base, "nope")
        assertEquals(QueuedBill.State.Failed, f.state); assertEquals("nope", f.error); assertEquals("/x", f.localPath)
    }

    @Test fun anExpiredReservationIsReservedAgain() {
        val signed = base.copy(billId = "b", params = mapOf("a" to "1"), uploadUrl = "u", signedAt = 5, state = QueuedBill.State.Failed, error = "x")
        val reset = BillQueueRules.afterSignGone(signed)
        assertNull(reset.billId); assertNull(reset.params); assertNull(reset.uploadUrl)
        assertEquals(QueuedBill.State.Waiting, reset.state)
        assertTrue(BillQueueRules.needsSign(reset, now = 6))
    }
}
