package com.example.spendsync.ui.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Languages the app ships in. [storedName] is what is saved on the device and in the user's
 * account (it was already the format of the backend `language` column), [nativeName] is how the
 * language names itself in the picker so people can always find theirs.
 *
 * To add a language: add `res/values-xx/strings.xml`, add one entry here, and add the same
 * name to `SUPPORTED_LANGUAGES` in the backend's `settings.types.ts`.
 */
enum class AppLanguage(val storedName: String, val tag: String, val nativeName: String) {
    English("English", "en", "English"),
    Hindi("Hindi", "hi", "हिन्दी"),
    Spanish("Spanish", "es", "Español"),
    French("French", "fr", "Français"),
    German("German", "de", "Deutsch");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromStored(name: String?): AppLanguage = entries.firstOrNull { it.storedName == name } ?: English
    }
}

/**
 * The single source of truth for the app's language.
 *
 * - [apply] switches it (called at startup and whenever the setting changes).
 * - [string] / [tr] read a string in the current language from ANY code — composables,
 *   notifications, repositories, exports — so nothing is ever left in the old language.
 * - [current] is Compose state, so every composable that reads a string recomposes the moment
 *   the language changes. It also sets the JVM default locale, so month and weekday names
 *   follow the language too.
 */
object LanguageManager {
    @Volatile private var appContext: Context? = null
    @Volatile private var localizedContext: Context? = null
    private var languageState by mutableStateOf(AppLanguage.English)

    /** Reading this inside a composable subscribes it to language changes. */
    val current: AppLanguage get() = languageState

    fun apply(base: Context, language: AppLanguage) {
        val app = base.applicationContext
        appContext = app
        Locale.setDefault(language.locale)
        val config = Configuration(app.resources.configuration).apply {
            setLocale(language.locale)
            setLayoutDirection(language.locale)
        }
        localizedContext = app.createConfigurationContext(config)
        languageState = language
    }

    /** A string in a specific language, without changing the app's language (the guide's "translate" menu). */
    fun stringIn(language: AppLanguage, @StringRes id: Int, vararg args: Any): String {
        val app = appContext ?: error("LanguageManager.apply() has not run yet")
        val config = Configuration(app.resources.configuration).apply { setLocale(language.locale) }
        val ctx = app.createConfigurationContext(config)
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

    fun string(@StringRes id: Int, vararg args: Any): String {
        languageState // subscribe the caller (if composing) to language changes
        val ctx = localizedContext ?: appContext ?: error("LanguageManager.apply() has not run yet")
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }
}

/** Short alias for [LanguageManager.string]: `tr(R.string.save)`. */
fun tr(@StringRes id: Int, vararg args: Any): String = LanguageManager.string(id, *args)
