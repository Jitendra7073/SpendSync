package com.example.spendsync.ui.bills

import com.example.spendsync.data.bills.QueuedBill
import com.example.spendsync.data.remote.model.BillDto

sealed interface BillTile {
    val position: Int
    data class Remote(val bill: BillDto, val replacing: QueuedBill?, val progress: Float?) : BillTile { override val position get() = bill.position }
    data class Local(val item: QueuedBill, val progress: Float?, override val position: Int) : BillTile
}

/** Server pages in order, a replacement shown on the page it replaces, new uploads after them. */
fun mergeTiles(server: List<BillDto>, queued: List<QueuedBill>, progress: Map<String, Float>): List<BillTile> {
    val remote = server.sortedBy { it.position }.map { b ->
        val r = queued.find { it.replaces == b.id }
        BillTile.Remote(b, r, r?.let { progress[it.clientKey] })
    }
    var next = (server.maxOfOrNull { it.position } ?: -1) + 1
    val local = queued.filter { it.replaces == null }.map { BillTile.Local(it, progress[it.clientKey], it.position ?: next++) }
    return (remote + local).sortedBy { it.position }
}

/** Below Android 12 the phone can't blur, so a masked bill only ever loads the server-blurred image. */
fun billImageUrl(bill: BillDto, masked: Boolean, sdk: Int, thumb: Boolean): String = when {
    masked && sdk < 31 -> bill.blurred
    thumb -> bill.thumb
    else -> bill.full
}
