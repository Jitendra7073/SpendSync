package com.example.spendsync.notifications

import com.example.spendsync.notifications.CaptureSource.EMAIL
import com.example.spendsync.notifications.CaptureSource.SMS
import com.example.spendsync.notifications.CaptureSource.UPI
import com.example.spendsync.notifications.TransactionDirection.CREDIT
import com.example.spendsync.notifications.TransactionDirection.DEBIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** A corpus of realistic (anonymised) Indian bank / UPI messages and what Smart capture must read from each. */
class CaptureParserTest {

    private val today = LocalDate.of(2026, 10, 15)
    private fun parse(source: CaptureSource, sender: String, text: String) = CaptureParser.parse(source, sender, text, today)

    private fun payment(
        source: CaptureSource, sender: String, text: String,
        amount: Double, direction: TransactionDirection,
        payee: String? = null, ref: String? = null, last4: String? = null, balance: Double? = null, date: LocalDate? = null,
    ) {
        val r = parse(source, sender, text)
        val msg = "for: $text\ngot: $r"
        assertEquals(msg, null, r?.ignoreReason)
        assertEquals(msg, amount, r?.amount)
        assertEquals(msg, direction, r?.direction)
        assertEquals(msg, payee, r?.payee)
        assertEquals(msg, ref, r?.refNo)
        assertEquals(msg, last4, r?.last4)
        assertEquals(msg, balance, r?.balance)
        assertEquals(msg, date, r?.date)
    }

    private fun ignored(source: CaptureSource, sender: String, text: String, reason: String) =
        assertEquals("for: $text", reason, parse(source, sender, text)?.ignoreReason)

    private fun dropped(source: CaptureSource, sender: String, text: String) = assertNull("for: $text", parse(source, sender, text))

    // ── Bank SMS ────────────────────────────────────────────────────────────

    @Test fun `SBI UPI debit`() = payment(
        SMS, "VM-SBIUPI",
        "Dear UPI user A/C X0790 debited by 30.00 on date 15Oct26 trf to MANISH DINESHPRA Refno 838274000171 If not u? call-1800111109 for other services-18001234-SBI",
        30.0, DEBIT, payee = "MANISH DINESHPRA", ref = "838274000171", last4 = "0790",
    )

    @Test fun `HDFC UPI debit to a UPI id`() = payment(
        SMS, "AX-HDFCBK-S",
        "Sent Rs.500.00\nFrom HDFC Bank A/C *1234\nTo zomato@icici\nOn 15/10/26\nRef 628112345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
        500.0, DEBIT, payee = "zomato@icici", ref = "628112345678", last4 = "1234",
    )

    @Test fun `ICICI credit from a company, delivered a day late`() = payment(
        SMS, "JD-ICICIT",
        "Dear Customer, Acct XX345 is credited with Rs 25,000.00 on 14-Oct-26 from ACME TECHNOLOGIES PVT. UPI:628155555555-ICICI Bank.",
        25000.0, CREDIT, payee = "ACME TECHNOLOGIES PVT", ref = "628155555555", last4 = "345", date = LocalDate.of(2026, 10, 14),
    )

    @Test fun `ICICI debit names the payee before 'credited'`() = payment(
        SMS, "VM-ICICIT",
        "ICICI Bank Acct XX345 debited for Rs 240.00 on 15-Oct-26; RAMESH KUMAR credited. UPI:628177777777. Call 18002662 for dispute.",
        240.0, DEBIT, payee = "RAMESH KUMAR", ref = "628177777777", last4 = "345",
    )

    @Test fun `Axis card spend keeps the amount, not the card limit`() = payment(
        SMS, "AD-AXISBK",
        "Spent INR 1,299.00 Axis Bank Card no. XX4321 15-10-26 12:30:11 IST AMAZON PAY IN Avl Limit: INR 48,700.00 Not you? SMS BLOCK 4321 to 919951860002",
        1299.0, DEBIT, last4 = "4321",
    )

    @Test fun `Kotak UPI debit with AC abbreviation`() = payment(
        SMS, "VM-KOTAKB",
        "Sent Rs.250.00 from Kotak Bank AC X9876 to swiggy@axisbank on 15-10-26.UPI Ref 628199999999. Not you, https://kotak.com/KBANKT/Fraud",
        250.0, DEBIT, payee = "swiggy@axisbank", ref = "628199999999", last4 = "9876",
    )

    @Test fun `SBI NEFT credit with balance and lakh commas`() = payment(
        SMS, "BZ-SBIINB",
        "Your A/C XXXXX1234 Credited INR 50,000.00 on 13/10/26 -Deposit by transfer from ACME CORP. Avl Bal INR 1,20,345.67-SBI",
        50000.0, CREDIT, payee = "ACME CORP", last4 = "1234", balance = 120345.67, date = LocalDate.of(2026, 10, 13),
    )

    @Test fun `refund is a credit and names the merchant`() = payment(
        SMS, "VM-HDFCBK",
        "Refund of Rs 349.00 from FLIPKART has been credited to your A/c XX1234 on 15-10-26",
        349.0, CREDIT, payee = "FLIPKART", last4 = "1234",
    )

    @Test fun `ATM withdrawal`() = payment(
        SMS, "VM-PNBSMS",
        "Rs.2000 withdrawn at ATM S1AW000123 from A/c XX1234 on 15Oct26. Avl Bal Rs.8,000",
        2000.0, DEBIT, payee = "ATM S1AW000123", last4 = "1234", balance = 8000.0,
    )

    @Test fun `bare bank short code as sender`() = payment(
        SMS, "HDFCBK",
        "Rs.120.00 debited from a/c no. XXXXXXXX5566 on 15-10-26 to CHAI POINT. Ref 628123123123",
        120.0, DEBIT, payee = "CHAI POINT", ref = "628123123123", last4 = "5566",
    )

    @Test fun `full account number gives its last 4`() = payment(
        SMS, "VM-BOBTXN",
        "Rs.75 debited from A/c no. 50100123456789 to BIG BAZAAR. Ref No: 628100000123",
        75.0, DEBIT, payee = "BIG BAZAAR", ref = "628100000123", last4 = "6789",
    )

    // ── Bank emails (Gmail / Outlook notification text) ─────────────────────

    @Test fun `HDFC email for the same UPI payment as the SMS`() = payment(
        EMAIL, "HDFC Bank InstaAlerts",
        "You have done a UPI txn. Check details!\nDear Customer, Rs.500.00 has been debited from account 1234 to VPA zomato@icici ZOMATO LTD on 15-10-26. Your UPI transaction reference number is 628112345678.",
        500.0, DEBIT, payee = "zomato@icici", ref = "628112345678", last4 = "1234",
    )

    @Test fun `Axis credit card email`() = payment(
        EMAIL, "Axis Bank Alerts",
        "Transaction alert for your Axis Bank Credit Card\nINR 649.00 spent on credit card no. XX4321 at SWIGGY on 15-10-2026 10:12:45 IST.",
        649.0, DEBIT, payee = "SWIGGY", last4 = "4321",
    )

    @Test fun `bank footer email address is not taken as a UPI id`() {
        val r = parse(EMAIL, "ICICI Bank", "Your account XX345 has been debited with INR 99.00 towards NETFLIX. Write to customer.care@icicibank.com")
        assertEquals("NETFLIX", r?.payee)
    }

    // ── UPI apps ────────────────────────────────────────────────────────────

    @Test fun `Google Pay payment`() = payment(UPI, "Google Pay", "You paid ₹250 to Ramesh Kumar", 250.0, DEBIT, payee = "Ramesh Kumar")
    @Test fun `PhonePe money received`() = payment(UPI, "PhonePe", "Received ₹1,200 from Rahul Sharma", 1200.0, CREDIT, payee = "Rahul Sharma")
    @Test fun `Paytm payment`() = payment(UPI, "Paytm", "Paid Rs.99 to Netflix", 99.0, DEBIT, payee = "Netflix")

    // ── Kept in history, never added ────────────────────────────────────────

    @Test fun `failed payment`() = ignored(
        SMS, "VM-ICICIB",
        "Your UPI txn of Rs.200.00 to merchant@ybl has failed. Amount if debited will be refunded in 3-5 days. Ref 628100000001", "failed",
    )
    @Test fun `mandate reminder`() = ignored(SMS, "VM-HDFCBK", "Rs 499 will be debited from your A/c XX1234 on 20-10-26 towards Netflix mandate. -HDFC", "future")
    @Test fun `card bill due`() = ignored(SMS, "VM-HDFCBK", "Minimum amount due of Rs 2,500 for your HDFC Bank Credit Card XX1111 is due on 25-Oct", "future")
    @Test fun `money request`() = ignored(UPI, "PhonePe", "Rahul has requested Rs.500 from you on PhonePe. Pay now", "request")
    @Test fun `loan offer from a bank sender`() = ignored(
        SMS, "VM-HDFCBK", "Get a Pre-approved Personal Loan of Rs 5,00,000 at 10.5% p.a. Apply now: hdfc.bank.in/xyz", "promo",
    )
    @Test fun `cashback ad without an account`() = ignored(EMAIL, "Paytm", "Win cashback up to ₹100 on UPI. Offer valid today", "promo")

    // ── Never stored ────────────────────────────────────────────────────────

    @Test fun `one-time codes are dropped`() = dropped(SMS, "VM-HDFCBK", "123456 is your OTP for txn of Rs 500 at AMAZON. Do not share. -HDFC Bank")
    @Test fun `personal SMS from a contact is not read`() = dropped(SMS, "Mom", "Sent you 500 rs for groceries")
    @Test fun `balance-only alert has no payment amount`() = dropped(SMS, "VM-HDFCBK", "Avl bal in A/c XX1234 is INR 12,345.00 as on 15-10-26. -HDFC Bank")
    @Test fun `email without any bank marker`() = dropped(EMAIL, "Newsletter", "You paid too much for your last holiday! Save ₹5,000 now")
    @Test fun `no amount at all`() = dropped(SMS, "VM-HDFCBK", "Your account was credited successfully.")

    // ── Amount spellings ────────────────────────────────────────────────────

    @Test fun `amount spellings`() {
        fun amount(text: String) = parse(UPI, "x", text)?.amount
        assertEquals(30.0, amount("Rs.30 debited"))
        assertEquals(1200.0, amount("Rs 1,200 debited"))
        assertEquals(500.0, amount("INR 500 debited"))
        assertEquals(30.5, amount("Rs30.50 debited"))
        assertEquals(30.0, amount("A/C debited by 30.00. Call 24 hrs 18001234 for other services"))
    }
}
