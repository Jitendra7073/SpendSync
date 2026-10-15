package com.example.spendsync.voice

import com.example.spendsync.ui.i18n.AppLanguage
import java.util.Locale

/**
 * Voice settings. Stored on this phone only: installed voices differ from phone to phone, so a synced
 * voice name would mean nothing on another device.
 */
data class VoicePrefs(
    /** BCP-47 tag to listen in; "" = the app language. */
    val language: String = "",
    /** A TextToSpeech voice name; "" = the phone's default voice for the reply's language. */
    val voice: String = "",
    /** Speaking speed in percent. */
    val speed: Int = 100,
    /** After an answer, listen again without a tap. */
    val keepListening: Boolean = true,
) {
    fun listenLocale(app: AppLanguage): Locale =
        if (language.isNotBlank()) Locale.forLanguageTag(language)
        else Locale.forLanguageTag(if (app == AppLanguage.English) "en-IN" else app.tag)
}

val VOICE_SPEEDS = listOf(75, 100, 125, 150)

/** Languages offered for listening, shown in their own script (like the app's language list). */
val VOICE_LANGUAGES = listOf(
    "en-IN" to "English (India)",
    "en-US" to "English (US)",
    "hi-IN" to "हिन्दी · Hinglish",
    "bn-IN" to "বাংলা",
    "ta-IN" to "தமிழ்",
    "te-IN" to "తెలుగు",
    "mr-IN" to "मराठी",
    "gu-IN" to "ગુજરાતી",
    "kn-IN" to "ಕನ್ನಡ",
    "ml-IN" to "മലയാളം",
    "pa-IN" to "ਪੰਜਾਬੀ",
    "ur-IN" to "اردو",
    "es-ES" to "Español",
    "fr-FR" to "Français",
    "de-DE" to "Deutsch",
)
