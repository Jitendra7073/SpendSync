package com.example.spendsync.ui.bills

import com.example.spendsync.data.bills.QueuedBill
import com.example.spendsync.data.remote.model.BillDto
import org.junit.Assert.assertEquals
import org.junit.Test

class BillTilesTest {
    private fun bill(id: String, pos: Int) = BillDto(id, "t", pos, "ready", "jpg", null, "thumb-$id", "full-$id", "blur-$id", listOf("full-$id"), null)
    private fun q(key: String, pos: Int?, replaces: String? = null) = QueuedBill(key, "t", "/$key", "image/jpeg", 1, pos, replaces)

    @Test fun serverAndQueuedPagesAreOrderedAndReplacementsSitOnTheirPage() {
        val tiles = mergeTiles(listOf(bill("a", 0), bill("b", 1)), listOf(q("k1", null), q("k2", null, replaces = "a")), mapOf("k1" to 0.4f))
        assertEquals(listOf(0, 1, 2), tiles.map { it.position })
        assertEquals("k2", (tiles[0] as BillTile.Remote).replacing?.clientKey)
        assertEquals(0.4f, (tiles[2] as BillTile.Local).progress)
    }

    @Test fun maskedBillsNeverUseTheClearUrlOnOldAndroid() {
        val b = bill("a", 0)
        assertEquals("blur-a", billImageUrl(b, masked = true, sdk = 30, thumb = true))
        assertEquals("blur-a", billImageUrl(b, masked = true, sdk = 30, thumb = false))
        assertEquals("thumb-a", billImageUrl(b, masked = true, sdk = 31, thumb = true)) // blurred on the device
        assertEquals("full-a", billImageUrl(b, masked = false, sdk = 30, thumb = false))
    }
}
