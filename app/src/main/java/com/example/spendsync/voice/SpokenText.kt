package com.example.spendsync.voice

import com.example.spendsync.ui.assistant.markdownToPlain
import com.example.spendsync.utils.maskAmountsInText
import java.lang.Character.UnicodeScript
import java.util.Locale

private const val MASK = "₹★★★★★"
private val NOT_SPOKEN = Regex("[•◦|★>—#]")
private val SPACES = Regex("[ \\t]+")

/**
 * What the voice says for an answer: the same privacy rule as the screen ([maskAmountsInText]), with
 * hidden amounts said as [hiddenPhrase] instead of "rupee star star…", and no markdown symbols.
 */
fun toSpeech(text: String, maskingEnabled: Boolean, unlocked: Boolean, hiddenPhrase: String): String {
    val masked = maskAmountsInText(text, maskingEnabled, unlocked).text.replace(MASK, hiddenPhrase)
    return markdownToPlain(masked)
        .replace(NOT_SPOKEN, " ")
        .lines()
        .joinToString("\n") { it.replace(SPACES, " ").trim() }
        .trim()
}

/** New finished sentences since the last call; [reset] when the reply was replaced and speech must start over. */
data class Feed(val reset: Boolean, val sentences: List<String>)

/**
 * Cuts a streaming reply into sentences so speaking can start before the answer is complete. A sentence
 * ends at a newline, or at . ? ! । … followed by a space (so "₹1,250.50" never splits) once it holds a
 * letter (so "1." of a numbered list stays with its item).
 */
class SentenceFeed {
    private var seen = ""

    fun update(text: String): Feed = take(text, final = false)

    /** The reply is complete: whatever is left is the last sentence. */
    fun finish(text: String): Feed = take(text, final = true)

    private fun take(text: String, final: Boolean): Feed {
        val reset = !text.startsWith(seen)
        if (reset) seen = ""
        val out = mutableListOf<String>()
        var start = seen.length
        for (i in start until text.length) {
            val c = text[i]
            val end = when {
                c == '\n' -> i
                c in ".?!।…" && i + 1 < text.length && text[i + 1].isWhitespace() &&
                    text.substring(start, i + 1).any { it.isLetter() } -> i + 1
                else -> continue
            }
            text.substring(start, end).trim().takeIf { s -> s.any { it.isLetterOrDigit() } }?.let(out::add)
            start = if (c == '\n') i + 1 else end
        }
        if (final) {
            text.substring(start).trim().takeIf { s -> s.any { it.isLetterOrDigit() } }?.let(out::add)
            start = text.length
        }
        seen = text.substring(0, start)
        return Feed(reset, out)
    }
}

private val LANGUAGE_SCRIPT = mapOf(
    "hi" to UnicodeScript.DEVANAGARI, "mr" to UnicodeScript.DEVANAGARI, "ne" to UnicodeScript.DEVANAGARI,
    "bn" to UnicodeScript.BENGALI, "ta" to UnicodeScript.TAMIL, "te" to UnicodeScript.TELUGU,
    "gu" to UnicodeScript.GUJARATI, "kn" to UnicodeScript.KANNADA, "ml" to UnicodeScript.MALAYALAM,
    "pa" to UnicodeScript.GURMUKHI, "ur" to UnicodeScript.ARABIC, "ar" to UnicodeScript.ARABIC,
)

private val SCRIPT_LOCALE = mapOf(
    UnicodeScript.DEVANAGARI to "hi-IN", UnicodeScript.BENGALI to "bn-IN", UnicodeScript.TAMIL to "ta-IN",
    UnicodeScript.TELUGU to "te-IN", UnicodeScript.GUJARATI to "gu-IN", UnicodeScript.KANNADA to "kn-IN",
    UnicodeScript.MALAYALAM to "ml-IN", UnicodeScript.GURMUKHI to "pa-IN", UnicodeScript.ARABIC to "ur-IN",
    UnicodeScript.LATIN to "en-IN",
)

/**
 * The assistant answers in whatever language the user speaks, so the voice follows the reply's script:
 * the [preferred] language when its script matches, otherwise the usual language for that script.
 */
fun speechLocaleFor(text: String, preferred: Locale): Locale {
    val script = text.filter(Char::isLetter)
        .groupingBy { UnicodeScript.of(it.code) }.eachCount()
        .maxByOrNull { it.value }?.key ?: return preferred
    if ((LANGUAGE_SCRIPT[preferred.language] ?: UnicodeScript.LATIN) == script) return preferred
    return SCRIPT_LOCALE[script]?.let(Locale::forLanguageTag) ?: preferred
}
