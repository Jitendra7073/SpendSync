package com.example.spendsync.data.billai

import com.example.spendsync.data.remote.model.BillItemDto
import com.example.spendsync.data.remote.model.BillReadDto
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BillAiTest {
    private fun read(total: Double? = 2150.0, conf: Double = 0.9, date: String? = "2026-10-09", cat: String? = "Shopping") = BillReadDto(
        merchant = "Zara", date = date, currency = "INR", total = total, subtotal = 2000.0, tax = 150.0, discount = null, fees = 49.0,
        items = listOf(BillItemDto("Shirt", 1.0, 1200.0, 1200.0)), paymentMethod = null, billNumber = null, category = cat,
        confidence = conf, summary = "s", notes = emptyList(), checks = null,
    )
    private val today = LocalDate.of(2026, 10, 11)

    @Test fun prefillUsesConfidentReadsOnly() {
        val p = BillAi.prefill(read(), today, listOf("Food", "shopping"))!!
        assertEquals(2150.0, p.amount); assertEquals("Zara", p.note); assertEquals(LocalDate.of(2026, 10, 9), p.date); assertEquals("shopping", p.category)
        assertNull(BillAi.prefill(read(conf = 0.4), today, listOf("Food")))
    }
    @Test fun prefillNeverDatesInTheFutureAndDropsUnknownCategories() {
        val p = BillAi.prefill(read(date = "2026-12-01", cat = "Yachts"), today, listOf("Food"))!!
        assertEquals(today, p.date); assertNull(p.category)
    }
    @Test fun mismatchIgnoresRounding() {
        assertFalse(BillAi.differs(2000.0, 2000.9)); assertTrue(BillAi.differs(2000.0, 2150.0)); assertFalse(BillAi.differs(2000.0, null))
    }
    @Test fun notesGetRealNumbersOrStarsWhenMasked() {
        assertEquals("Delivery ₹49.00 of ₹2,150.00", BillAi.fillNote("Delivery {fees} of {total}", read(), masked = false))
        assertEquals("Delivery ★★★★★ of ★★★★★", BillAi.fillNote("Delivery {fees} of {total}", read(), masked = true))
        assertEquals("Item 1 cost ₹1,200.00", BillAi.fillNote("Item 1 cost {item:1}", read(), masked = false))
        assertEquals("— here", BillAi.fillNote("{item:7} here", read(), masked = false))
    }

    @Test fun maskedBillTextHidesEveryNumber() {
        assertEquals("★★★★★ at Zara, ★★★★★ items", BillAi.maskText("₹2,150 at Zara, 3 items", masked = true))
        assertEquals("★★★★★ बिल", BillAi.maskText("४९ बिल", masked = true))
        assertEquals("₹2,150 at Zara", BillAi.maskText("₹2,150 at Zara", masked = false))
    }
}
