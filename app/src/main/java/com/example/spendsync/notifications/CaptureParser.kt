package com.example.spendsync.notifications

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale

enum class TransactionDirection { DEBIT, CREDIT }

/** Where a message came from. [wire] is the value the server and the settings use. */
enum class CaptureSource(val wire: String) { SMS("sms"), EMAIL("email"), UPI("upi") }

/**
 * What one bank message says. [ignoreReason] set = a money message that is not a payment (an offer, a failed
 * payment, a future debit, a money request): it is kept in the history but never added.
 */
data class CaptureFound(
    val amount: Double?,
    val direction: TransactionDirection?,
    val payee: String? = null,
    val refNo: String? = null,
    val last4: String? = null,
    val balance: Double? = null,
    /** The day written in the message, when it is 1–3 days before it arrived (a delayed SMS). */
    val date: LocalDate? = null,
    val ignoreReason: String? = null,
)

/**
 * Reads Indian bank SMS, bank emails (as the Gmail/Outlook notification shows them) and UPI-app alerts.
 * One keyword/regex heuristic rather than per-bank templates (formats vary too much to enumerate).
 * [parse] returns null for anything that must not be stored at all: one-time codes, personal chats,
 * and texts with no amount in them.
 */
object CaptureParser {

    private val I = RegexOption.IGNORE_CASE

    private val OTP = Regex("""\b(otp|one[\s-]?time\s+pass(word|code)?|verification\s+code|security\s+code)\b""", I)

    /** DLT header ("VM-HDFCBK", "AX-ICICIT-S") or a bare bank short code ("HDFCBK"). */
    private val DLT_SENDER = Regex("""^([A-Z]{2}-)?[A-Z0-9]{5,8}(-[SPTG])?$""")
    private val ACCOUNT = Regex("""\b(a/c|acct|account|card|ac)\b""", I)
    private val ACCOUNT_OR_UPI = Regex("""\b(a/c|acct|account|card|ac|upi|vpa|imps|neft|rtgs|utr)\b""", I)

    private val FAILED = Regex("""\b(failed|declined|unsuccessful|could not be (processed|completed)|was not successful|has been rejected)\b""", I)
    private val FUTURE = Regex(
        """\b(will be (debited|deducted|charged|auto[\s-]?debited)|is due|due (on|date|by)|payment reminder|scheduled (for|on)|mandate (is )?(created|registered|set up)|upcoming (debit|payment)|pay before|minimum amount due)\b""",
        I,
    )
    private val REQUEST = Regex("""\b(has requested|requested (money|you|for|rs|inr)|collect request|payment request)\b""", I)
    private val PROMO = Regex(
        """\b(pre-?approved|offers?|cashback (of )?up ?to|eligible for|loan (of|up ?to|amount)|apply now|limited period|coupon|discount|won|win|reward points?)\b""",
        I,
    )

    private val DEBIT = Regex(
        """\b(debited|debit of|spent|paid|sent|withdrawn|withdrawal|purchased?|charged|transferred to|trf to|deducted|used for)\b""",
        I,
    )
    private val CREDIT = Regex("""\b(credited|received|deposited|refund(ed)?|reversal|reversed|added to your)\b""", I)

    private const val NUMBER = """(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)"""
    // \b before Rs/INR so "hrs"/"customers" can't anchor a match and swallow a phone number.
    private val CURRENCY_AMOUNT = Regex("""(?:₹|\bRs\.?|\bINR)\s?$NUMBER""", I)
    private val KEYWORD_AMOUNT = Regex("""\b(?:debited|credited|paid|received)\s+(?:by|with|for|of)?\s*$NUMBER\b""", I)
    private val BALANCE = Regex("""(?:avl\.?\s*bal(?:ance)?|available\s+balance|\bbal(?:ance)?)\s*(?:is|:|-)?\s*(?:₹|Rs\.?|INR)?\s?$NUMBER""", I)
    private val NEAR_BALANCE = Regex("""bal(ance)?|limit""", I)

    private val REF = Regex(
        """\b(?:upi\s*ref(?:erence)?\s*(?:no\.?|number|id)?|ref(?:erence)?\s*(?:no\.?|number|id|#)?|refno|txn\s*(?:id|no\.?|ref)|transaction\s*(?:id|ref(?:erence)?)\s*(?:no\.?|number)?|utr\s*(?:no\.?)?|rrn|imps\s*ref)\s*(?:is)?\s*[:\-#.]?\s*([A-Z0-9]{6,})""",
        I,
    )
    private val UPI_PATH_REF = Regex("""\bUPI[/:-](\d{10,})""", I)

    /** UPI ids have a dot-free handle ("@okicici", "@ybl"); email addresses ("@hdfcbank.net") don't count. */
    private val VPA = Regex("""\b([a-z0-9][a-z0-9._\-]+@[a-z]{2,})\b(?![.\-])""", I)

    // A name ends at the next keyword, punctuation or line break.
    private const val END = """(?=\s+(?:refno|ref|on|via|for|from|to|using|upi|avl|bal|thru|through|has|is|was|of|txn|info)\b|[.(,;:\n]|$)"""
    private const val NOT_A_NAME = """(?!(?:vpa|a/c|ac|your|account|card|neft|imps|upi|transfer|you)\b)"""
    /** Who got the money on a debit … */
    private val PAID_TO = listOf(
        Regex("""\b(?:trf to|transferred to|paid to|sent to|to)\s+$NOT_A_NAME([A-Za-z][A-Za-z .&'-]{1,40}?)$END""", I),
        Regex("""\b(?:at|towards)\s+$NOT_A_NAME([A-Za-z][A-Za-z0-9 .&'-]{1,40}?)$END""", I),
        Regex(""";\s*([A-Za-z][A-Za-z .&'-]{1,40}?)\s+credited\b""", I), // ICICI: "…debited for Rs 240; RAMESH KUMAR credited"
    )
    /** … and who sent it on a credit. */
    private val PAID_BY = listOf(Regex("""\b(?:from|by)\s+$NOT_A_NAME([A-Za-z][A-Za-z .&'-]{1,40}?)$END""", I))

    private val ACCOUNT_NUMBER = Regex("""^\W{0,3}(?:no\.?|number|ending(?:\s+(?:with|in))?)?\s*[:.]?\s*([xX*•]*\d{3,})""", I)

    private val DATE = Regex("""\b(\d{1,2})[-/ ]?([A-Za-z]{3}|\d{1,2})[-/ ]?(\d{4}|\d{2})\b""")
    private val MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    fun parse(source: CaptureSource, sender: String, text: String, received: LocalDate = LocalDate.now()): CaptureFound? {
        if (OTP.containsMatchIn(text)) return null
        val bankish = when (source) {
            CaptureSource.UPI -> true
            CaptureSource.SMS -> DLT_SENDER.matches(sender.trim()) || ACCOUNT.containsMatchIn(text)
            CaptureSource.EMAIL -> ACCOUNT_OR_UPI.containsMatchIn(text)
        }
        if (!bankish) return null
        val amount = amountIn(text) ?: return null

        fun ignored(reason: String) = CaptureFound(amount, null, ignoreReason = reason)
        if (FAILED.containsMatchIn(text)) return ignored("failed")
        if (FUTURE.containsMatchIn(text)) return ignored("future")
        if (REQUEST.containsMatchIn(text)) return ignored("request")
        val direction = directionIn(text) ?: return ignored(if (PROMO.containsMatchIn(text)) "promo" else "not_payment")
        // "Cashback up to ₹100 credited…" with no account, UPI or reference behind it is still an ad.
        if (PROMO.containsMatchIn(text) && source != CaptureSource.UPI && !ACCOUNT_OR_UPI.containsMatchIn(text)) return ignored("promo")

        return CaptureFound(
            amount = amount,
            direction = direction,
            payee = payeeIn(text, direction),
            refNo = refIn(text),
            last4 = last4In(text),
            balance = BALANCE.find(text)?.groupValues?.get(1)?.toMoney(),
            date = dateIn(text, received),
        )
    }

    private fun String.toMoney() = replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }

    /** The first amount that is not the balance or the card limit. */
    private fun amountIn(text: String): Double? {
        val currency = CURRENCY_AMOUNT.findAll(text).firstOrNull { m ->
            !NEAR_BALANCE.containsMatchIn(text.substring(maxOf(0, m.range.first - 22), m.range.first))
        }
        return (currency?.groupValues?.get(1) ?: KEYWORD_AMOUNT.find(text)?.groupValues?.get(1))?.toMoney()
    }

    /** Earliest keyword wins: "debited from A/c X and credited to B" is a debit. */
    private fun directionIn(text: String): TransactionDirection? {
        val d = DEBIT.find(text)?.range?.first
        val c = CREDIT.find(text)?.range?.first
        return when {
            d == null && c == null -> null
            c == null || (d != null && d <= c) -> TransactionDirection.DEBIT
            else -> TransactionDirection.CREDIT
        }
    }

    private fun refIn(text: String): String? =
        (REF.findAll(text).map { it.groupValues[1] } + UPI_PATH_REF.findAll(text).map { it.groupValues[1] })
            .firstOrNull { it.any(Char::isDigit) }

    /** "A/c XX1234", "a/c no. XXXXXXXX1234", "Card ending 5678", "A/C X0790", "a/c *4321": the number right after the word. */
    private fun last4In(text: String): String? = ACCOUNT.findAll(text).firstNotNullOfOrNull { m ->
        val window = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 24))
        ACCOUNT_NUMBER.find(window)?.groupValues?.get(1)?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length >= 3 }
    }

    private fun payeeIn(text: String, direction: TransactionDirection): String? {
        // The earliest mention wins: the payee sits next to the amount, footers ("write to …") come last.
        val named = (if (direction == TransactionDirection.DEBIT) PAID_TO else PAID_BY)
            .mapNotNull { it.find(text) }
            .minByOrNull { it.range.first }
            ?.groupValues?.get(1)?.trim()?.trimEnd('.', '-', ' ')
            ?.takeIf { it.length >= 2 }
        return named ?: VPA.find(text)?.groupValues?.get(1)
    }

    /** Only a day 1–3 days before arrival counts; anything else (card expiry, due dates) is ignored. */
    private fun dateIn(text: String, received: LocalDate): LocalDate? {
        for (m in DATE.findAll(text)) {
            val (d, mo, y) = m.destructured
            val month = mo.toIntOrNull()
                ?: runCatching { MONTH.parse(mo.lowercase().replaceFirstChar(Char::uppercase)).get(ChronoField.MONTH_OF_YEAR) }.getOrNull()
                ?: continue
            val date = runCatching { LocalDate.of(y.toInt().let { if (it < 100) 2000 + it else it }, month, d.toInt()) }.getOrNull() ?: continue
            if (date == received) return null
            if (date.isBefore(received) && !date.isBefore(received.minusDays(3))) return date
        }
        return null
    }
}
