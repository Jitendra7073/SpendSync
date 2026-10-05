package com.example.spendsync.data.contacts

import java.net.URLEncoder

/**
 * Builds the links that open WhatsApp, the SMS app and the email app with a message already written. Nothing is
 * sent from here or from our server: the person taps Send in their own app.
 */
object Outreach {
    /** Country calling codes by region for the default when a contact's number has none. */
    private val callingCodes = mapOf(
        "IN" to "91", "US" to "1", "CA" to "1", "GB" to "44", "AU" to "61", "NZ" to "64", "DE" to "49", "FR" to "33",
        "ES" to "34", "IT" to "39", "AE" to "971", "SA" to "966", "SG" to "65", "MY" to "60", "PK" to "92", "BD" to "880",
        "NP" to "977", "LK" to "94", "ZA" to "27", "NG" to "234", "KE" to "254", "BR" to "55", "MX" to "52", "ID" to "62",
        "PH" to "63", "JP" to "81", "KR" to "82", "NL" to "31", "IE" to "353", "CH" to "41", "SE" to "46",
    )

    /** The calling code for a region such as "IN"; India when the region is unknown, since that is who this app is for. */
    fun callingCodeFor(region: String?): String = callingCodes[region?.uppercase()] ?: "91"

    /**
     * A phone number in the form WhatsApp links need: international, digits only, no "+", no leading zeros, no
     * spaces or dashes. Returns null when it cannot be a real number.
     */
    fun normalizePhone(raw: String, defaultCountryCode: String = "91"): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 7) return null
        val full = when {
            trimmed.startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            digits.startsWith("0") -> defaultCountryCode + digits.trimStart('0')
            digits.length <= 10 -> defaultCountryCode + digits // a national number typed without its country code
            else -> digits // already carries a country code, just without the "+"
        }
        return full.takeIf { it.length in 8..15 }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** `https://wa.me/<number>?text=<message>`; without a number WhatsApp lets the user choose the chat. */
    fun whatsAppUrl(digits: String?, text: String): String = "https://wa.me/${digits.orEmpty()}?text=${enc(text)}"

    /** `smsto:+<number>`; the body is passed as the `sms_body` extra, not in the link. */
    fun smsUri(digits: String): String = "smsto:+$digits"

    /** `mailto:` with subject and body percent-encoded (spaces as %20, which every email app understands). */
    fun mailtoUri(email: String?, subject: String, body: String): String =
        "mailto:${email.orEmpty()}?subject=${enc(subject)}&body=${enc(body)}"
}
