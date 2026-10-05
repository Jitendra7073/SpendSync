package com.example.spendsync.data.holds

import com.example.spendsync.R
import com.example.spendsync.data.local.HoldContact
import com.example.spendsync.ui.i18n.AppLanguage
import com.example.spendsync.ui.i18n.LanguageManager
import com.example.spendsync.utils.formatInr
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Where a follow-up is sent. Every channel opens the person's own app; nothing is sent from here. */
enum class Channel(val id: String) { WhatsApp("whatsapp"), Sms("sms"), Email("email") }

enum class Tone(val id: String) { Gentle("gentle"), Friendly("friendly"), Firm("firm") }

/** The facts a follow-up is about: who, how much, when, and which way the money goes. */
data class FollowUpTarget(
    val personName: String,
    /** "owed_to_me" (they owe the user) or "owed_by_me". */
    val direction: String,
    val amount: Double,
    /** YYYY-MM-DD */
    val dueDate: String,
    val overdueDays: Int = 0,
) {
    val owedToMe: Boolean get() = direction == "owed_to_me"

    companion object {
        /** From a list of holds with one person: amounts add up, the earliest due date leads. */
        fun fromHolds(personName: String, direction: String, amount: Double, earliestDue: LocalDate, today: LocalDate = LocalDate.now()) =
            FollowUpTarget(personName, direction, amount, earliestDue.toString(), java.time.temporal.ChronoUnit.DAYS.between(earliestDue, today).toInt().coerceAtLeast(0))
    }
}

/**
 * The offline backup for the AI draft: a fixed, translated template. Used when the AI is unavailable, when its draft
 * failed the server's checks, or when the user has not allowed the assistant. The same words every time, so they can
 * always be checked against the hold.
 */
object FollowUpTemplates {
    fun text(t: FollowUpTarget, tone: Tone, language: AppLanguage): String {
        val res = when (t.owedToMe to tone) {
            true to Tone.Gentle -> R.string.fu_tpl_owed_to_me_gentle
            true to Tone.Friendly -> R.string.fu_tpl_owed_to_me_friendly
            true to Tone.Firm -> R.string.fu_tpl_owed_to_me_firm
            false to Tone.Gentle -> R.string.fu_tpl_owed_by_me_gentle
            false to Tone.Friendly -> R.string.fu_tpl_owed_by_me_friendly
            else -> R.string.fu_tpl_owed_by_me_firm
        }
        return LanguageManager.stringIn(language, res, firstName(t.personName), formatInr(t.amount), dateText(t.dueDate, language))
    }

    fun subject(t: FollowUpTarget, language: AppLanguage): String =
        LanguageManager.stringIn(language, if (t.owedToMe) R.string.fu_subject_owed_to_me else R.string.fu_subject_owed_by_me, formatInr(t.amount))

    fun firstName(name: String) = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifBlank { name }

    private fun dateText(iso: String, language: AppLanguage): String =
        runCatching { LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", language.locale)) }.getOrDefault(iso.take(10))
}

/** What the phone needs before it can open a channel: a phone number for WhatsApp and SMS, an address for email. */
fun HoldContact?.canUse(channel: Channel): Boolean = when (channel) {
    Channel.WhatsApp, Channel.Sms -> !this?.phone.isNullOrBlank()
    Channel.Email -> !this?.email.isNullOrBlank()
}
