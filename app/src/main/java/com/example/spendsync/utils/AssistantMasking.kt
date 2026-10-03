package com.example.spendsync.utils

/** Result of hiding large amounts in a chat message. */
data class MaskedText(val text: String, val hidSomething: Boolean)

private val RUPEE_AMOUNT = Regex("""₹\s?(\d[\d,]*(?:\.\d+)?)""")

/**
 * The same privacy rule as every other screen, applied to prose: while "Hide large amounts" is on and
 * locked, any ₹ amount above the masking threshold is replaced with ₹★★★★★. The real text is kept
 * (and stored); only what is drawn changes, so unlocking reveals it instantly.
 */
fun maskAmountsInText(text: String, maskingEnabled: Boolean, unlocked: Boolean): MaskedText {
    if (!maskingEnabled || unlocked) return MaskedText(text, false)
    var hid = false
    val out = RUPEE_AMOUNT.replace(text) { m ->
        val value = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@replace m.value
        if (shouldMaskAmount(value, isVisible = false)) {
            hid = true
            "₹★★★★★"
        } else m.value
    }
    return MaskedText(out, hid)
}
