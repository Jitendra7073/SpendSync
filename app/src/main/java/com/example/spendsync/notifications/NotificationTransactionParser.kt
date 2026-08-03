package com.example.spendsync.notifications

enum class TransactionDirection { DEBIT, CREDIT }

data class ParsedTransaction(
    val amount: Double,
    val direction: TransactionDirection,
    val payee: String?,
    val refNumber: String?,
)

/**
 * Generic regex/keyword heuristic for extracting a transaction from a
 * notification's raw text (title + text concatenated). Deliberately not a
 * per-bank template system — formats vary too much to enumerate. Returns
 * null whenever amount or direction can't be confidently found, rather
 * than guessing.
 */
object NotificationTransactionParser {

    private val DEBIT_KEYWORDS = Regex("""\b(debited|paid|spent)\b""", RegexOption.IGNORE_CASE)
    private val CREDIT_KEYWORDS = Regex("""\b(credited|received)\b""", RegexOption.IGNORE_CASE)
    private val CURRENCY_AMOUNT = Regex("""(?:₹|Rs\.?|INR)\s?([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val KEYWORD_AMOUNT = Regex("""\b(?:by|of)\s+([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val REF_NUMBER = Regex(
        """(?:Refno|Ref\s?No\.?|UPI\s?Ref|Txn\s?ID)\s*[:\-]?\s*(\w+)""",
        RegexOption.IGNORE_CASE,
    )
    private val PAYEE_PATTERNS = listOf(
        Regex("""\btrf to\s+([A-Za-z][A-Za-z ]*?)(?=\s+Refno|\s+on\s+date|\s+via|\s+for|\.|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+([A-Za-z][A-Za-z ]*?)(?=\s+Refno|\s+on\s+date|\s+via|\s+for|\.|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+([A-Za-z][A-Za-z ]*?)(?=\s+Refno|\s+on\s+date|\s+via|\s+for|\.|$)""", RegexOption.IGNORE_CASE),
    )

    fun parse(text: String): ParsedTransaction? {
        val direction = extractDirection(text) ?: return null
        val amount = extractAmount(text) ?: return null
        return ParsedTransaction(
            amount = amount,
            direction = direction,
            payee = extractPayee(text),
            refNumber = REF_NUMBER.find(text)?.groupValues?.get(1),
        )
    }

    private fun extractDirection(text: String): TransactionDirection? {
        val debit = DEBIT_KEYWORDS.find(text)
        val credit = CREDIT_KEYWORDS.find(text)
        return when {
            debit != null && credit == null -> TransactionDirection.DEBIT
            credit != null && debit == null -> TransactionDirection.CREDIT
            debit != null && credit != null ->
                if (debit.range.first <= credit.range.first) TransactionDirection.DEBIT else TransactionDirection.CREDIT
            else -> null
        }
    }

    private fun extractAmount(text: String): Double? {
        val raw = CURRENCY_AMOUNT.find(text)?.groupValues?.get(1)
            ?: KEYWORD_AMOUNT.find(text)?.groupValues?.get(1)
            ?: return null
        return raw.replace(",", "").toDoubleOrNull()
    }

    private fun extractPayee(text: String): String? {
        for (pattern in PAYEE_PATTERNS) {
            val match = pattern.find(text)?.groupValues?.get(1)?.trim()
            if (!match.isNullOrBlank()) return match
        }
        return null
    }
}
