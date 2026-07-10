package com.example.spendsync.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Extension property — one DataStore per app process. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session_prefs")

/**
 * A user-added transaction category. [iconId] is an Iconify "prefix:name"
 * string (e.g. "mdi:pizza") when the user picked one from the icon search;
 * null for older entries saved before icon picking existed, which fall back
 * to a generic local icon.
 */
data class PersistedCategory(val name: String, val iconId: String?)

private fun parsePersistedCategories(raw: String?): List<PersistedCategory> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(",").filter { it.isNotBlank() }.map { entry ->
        val parts = entry.split("||", limit = 2)
        PersistedCategory(name = parts[0], iconId = parts.getOrNull(1)?.takeIf { it.isNotBlank() })
    }
}

private fun serializePersistedCategories(categories: List<PersistedCategory>): String =
    categories.joinToString(",") { cat ->
        if (cat.iconId != null) "${cat.name}||${cat.iconId}" else cat.name
    }

/**
 * Persists the Better Auth session token to DataStore so the app can
 * restore the session after a cold start without forcing the user to
 * sign in again.
 */
class SessionDataStore(private val context: Context) {

    companion object {
        private val KEY_SESSION_TOKEN = stringPreferencesKey("session_token")
        private val KEY_USER_EMAIL    = stringPreferencesKey("user_email")
        private val KEY_USER_NAME     = stringPreferencesKey("user_name")
        private val KEY_USER_ID       = stringPreferencesKey("user_id")
        private val KEY_USER_CREATED_AT = stringPreferencesKey("user_created_at")

        // Preference settings
        private val KEY_DARK_MODE           = booleanPreferencesKey("settings_dark_mode")
        private val KEY_NOTIFICATIONS       = booleanPreferencesKey("settings_notifications")
        private val KEY_BIOMETRICS          = booleanPreferencesKey("settings_biometrics")
        private val KEY_AUTO_BACKUP         = booleanPreferencesKey("settings_auto_backup")
        private val KEY_ACCENT_COLOR        = stringPreferencesKey("settings_accent_color")
        private val KEY_LANGUAGE            = stringPreferencesKey("settings_language")
        private val KEY_CURRENCY            = stringPreferencesKey("settings_currency")
        private val KEY_DATE_FORMAT         = stringPreferencesKey("settings_date_format")
        private val KEY_SECURITY_PIN        = stringPreferencesKey("settings_security_pin")

        // Custom transaction categories added via the icon picker — comma-joined
        // "name" or "name||iconId" entries (see PersistedCategory).
        private val KEY_CUSTOM_INCOME_CATEGORIES  = stringPreferencesKey("custom_income_categories")
        private val KEY_CUSTOM_EXPENSE_CATEGORIES = stringPreferencesKey("custom_expense_categories")
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    val sessionToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SESSION_TOKEN]
    }

    val userEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_EMAIL]
    }

    val userName: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_NAME]
    }

    val userId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_ID]
    }

    val userCreatedAt: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_CREATED_AT]
    }

    // Settings flows
    val darkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DARK_MODE] ?: false
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATIONS] ?: true
    }

    val biometricLock: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BIOMETRICS] ?: false
    }

    val autoBackup: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_BACKUP] ?: true
    }

    val accentColor: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCENT_COLOR] ?: "Brand Blue"
    }

    val language: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "English"
    }

    val currency: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CURRENCY] ?: "USD"
    }

    val dateFormat: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DATE_FORMAT] ?: "DD / MM / YYYY"
    }

    val securityPin: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SECURITY_PIN] ?: ""
    }

    val customIncomeCategories: Flow<List<PersistedCategory>> = context.dataStore.data.map { prefs ->
        parsePersistedCategories(prefs[KEY_CUSTOM_INCOME_CATEGORIES])
    }

    val customExpenseCategories: Flow<List<PersistedCategory>> = context.dataStore.data.map { prefs ->
        parsePersistedCategories(prefs[KEY_CUSTOM_EXPENSE_CATEGORIES])
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    suspend fun saveSession(
        token: String,
        userId: String,
        email: String,
        name: String?,
        createdAt: String?,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SESSION_TOKEN] = token
            prefs[KEY_USER_ID]       = userId
            prefs[KEY_USER_EMAIL]    = email
            prefs[KEY_USER_NAME]     = name ?: ""
            prefs[KEY_USER_CREATED_AT] = createdAt ?: ""
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SESSION_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_USER_CREATED_AT)
        }
    }

    // Update settings functions
    suspend fun updateUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = name
        }
    }

    suspend fun updateUserEmail(email: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_EMAIL] = email
        }
    }

    suspend fun updateDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = enabled
        }
    }

    suspend fun updateNotifications(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS] = enabled
        }
    }

    suspend fun updateBiometrics(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRICS] = enabled
        }
    }

    suspend fun updateAutoBackup(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_BACKUP] = enabled
        }
    }

    suspend fun updateAccentColor(color: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCENT_COLOR] = color
        }
    }

    suspend fun updateLanguage(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = lang
        }
    }

    suspend fun updateCurrency(curr: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENCY] = curr
        }
    }

    suspend fun updateDateFormat(format: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DATE_FORMAT] = format
        }
    }

    suspend fun updateSecurityPin(pin: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SECURITY_PIN] = pin
        }
    }

    suspend fun addCustomIncomeCategory(name: String, iconId: String? = null) {
        context.dataStore.edit { prefs ->
            val current = parsePersistedCategories(prefs[KEY_CUSTOM_INCOME_CATEGORIES])
            if (current.none { it.name == name }) {
                prefs[KEY_CUSTOM_INCOME_CATEGORIES] = serializePersistedCategories(current + PersistedCategory(name, iconId))
            }
        }
    }

    suspend fun addCustomExpenseCategory(name: String, iconId: String? = null) {
        context.dataStore.edit { prefs ->
            val current = parsePersistedCategories(prefs[KEY_CUSTOM_EXPENSE_CATEGORIES])
            if (current.none { it.name == name }) {
                prefs[KEY_CUSTOM_EXPENSE_CATEGORIES] = serializePersistedCategories(current + PersistedCategory(name, iconId))
            }
        }
    }
}
