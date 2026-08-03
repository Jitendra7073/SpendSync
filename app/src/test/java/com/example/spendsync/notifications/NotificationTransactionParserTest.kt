package com.example.spendsync.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTransactionParserTest {

    @Test
    fun `parses a real bank UPI debit SMS`() {
        val text = "Dear UPI user A/C X0790 debited by 30.00 on date 03Aug26 trf to " +
            "MANISH DINESHPRA Refno 838274000171 If not u? call-1800111109 for other services-18001234-SBI"

        val result = NotificationTransactionParser.parse(text)

        assertEquals(30.00, result?.amount)
        assertEquals(TransactionDirection.DEBIT, result?.direction)
        assertEquals("MANISH DINESHPRA", result?.payee)
        assertEquals("838274000171", result?.refNumber)
    }

    @Test
    fun `parses a UPI app payment notification with currency symbol`() {
        val result = NotificationTransactionParser.parse("You paid ₹500 to Swiggy")

        assertEquals(500.0, result?.amount)
        assertEquals(TransactionDirection.DEBIT, result?.direction)
        assertEquals("Swiggy", result?.payee)
        assertNull(result?.refNumber)
    }

    @Test
    fun `parses a credit notification with a payer name`() {
        val result = NotificationTransactionParser.parse("Received ₹1200 from Rahul Sharma via UPI")

        assertEquals(1200.0, result?.amount)
        assertEquals(TransactionDirection.CREDIT, result?.direction)
        assertEquals("Rahul Sharma", result?.payee)
    }

    @Test
    fun `does not mistake a phone number for the amount when 'rs' appears inside a word`() {
        val result = NotificationTransactionParser.parse(
            "A/C debited by 30.00. Call 24 hrs 18001234 for other services"
        )

        assertEquals(30.00, result?.amount)
    }

    @Test
    fun `extracts amounts across currency prefix spellings`() {
        assertEquals(30.0, NotificationTransactionParser.parse("Rs.30 debited")?.amount)
        assertEquals(1200.0, NotificationTransactionParser.parse("Rs 1,200 debited")?.amount)
        assertEquals(500.0, NotificationTransactionParser.parse("INR 500 debited")?.amount)
        assertEquals(30.50, NotificationTransactionParser.parse("Rs30.50 debited")?.amount)
    }

    @Test
    fun `returns null when there is no direction keyword`() {
        assertNull(NotificationTransactionParser.parse("Your OTP is 493821, do not share it."))
    }

    @Test
    fun `returns null when there is no amount`() {
        assertNull(NotificationTransactionParser.parse("Your account was credited successfully."))
    }
}
