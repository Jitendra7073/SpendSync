package com.example.spendsync.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.spendsync.data.assistant.AssistantPrefs
import com.example.spendsync.data.assistant.parseToolSet
import com.example.spendsync.data.assistant.serializeToolSet
import com.example.spendsync.notifications.PendingCapture
import com.example.spendsync.notifications.parsePendingCaptures
import com.example.spendsync.notifications.serializePendingCaptures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

/** Planify preferences. Defaults: alerts on, no daily summary, salary prompt for credits of 5,000 or more. */
data class PlanifySettings(val alerts: Boolean = true, val daily: Boolean = false, val salaryMin: Int = 5000)

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

internal fun parseAutoCapturePackages(raw: String?): Set<String> =
    raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

internal fun serializeAutoCapturePackages(packages: Set<String>): String =
    packages.joinToString(",")

/**
 * Persists the Better Auth session token to DataStore so the app can
 * restore the session after a cold start without forcing the user to
 * sign in again.
 */
class SessionDataStore(private val context: Context) {

    /** For background work that has to be scheduled from outside the store. */
    val appContext: Context get() = context.applicationContext

    companion object {
        private val KEY_SESSION_TOKEN = stringPreferencesKey("session_token")
        private val KEY_USER_EMAIL    = stringPreferencesKey("user_email")
        private val KEY_USER_NAME     = stringPreferencesKey("user_name")
        private val KEY_USER_ID       = stringPreferencesKey("user_id")
        private val KEY_USER_CREATED_AT = stringPreferencesKey("user_created_at")

        // Preference settings
        private val KEY_ASSISTANT_CONSENT = booleanPreferencesKey("assistant_consent")
        private val KEY_SETTINGS_DIRTY      = stringSetPreferencesKey("settings_pending_sync")
        private val KEY_DARK_MODE           = booleanPreferencesKey("settings_dark_mode")
        private val KEY_THEME_MODE          = stringPreferencesKey("settings_theme_mode")
        private val KEY_EMAIL_NOTIFICATIONS = booleanPreferencesKey("settings_email_notifications")
        private val KEY_NOTIFICATIONS       = booleanPreferencesKey("settings_notifications")
        private val KEY_AUTO_BACKUP         = booleanPreferencesKey("settings_auto_backup")
        private val KEY_ACCENT_COLOR        = stringPreferencesKey("settings_accent_color")
        private val KEY_LANGUAGE            = stringPreferencesKey("settings_language")
        private val KEY_DATE_FORMAT         = stringPreferencesKey("settings_date_format")

        // Custom transaction categories added via the icon picker — comma-joined
        // "name" or "name||iconId" entries (see PersistedCategory).
        private val KEY_CUSTOM_INCOME_CATEGORIES  = stringPreferencesKey("custom_income_categories")
        private val KEY_CUSTOM_EXPENSE_CATEGORIES = stringPreferencesKey("custom_expense_categories")

        private val KEY_AUTO_CAPTURE_ENABLED  = booleanPreferencesKey("auto_capture_enabled")
        private val KEY_AUTO_CAPTURE_PACKAGES = stringPreferencesKey("auto_capture_packages")
        private val KEY_ASSISTANT_MODEL = stringPreferencesKey("assistant_model")
        private val KEY_ASSISTANT_STYLE = stringPreferencesKey("assistant_style")
        private val KEY_ASSISTANT_TONE = stringPreferencesKey("assistant_tone")
        private val KEY_ASSISTANT_INSTRUCTIONS = stringPreferencesKey("assistant_instructions")
        private val KEY_ASSISTANT_DISABLED_TOOLS = stringPreferencesKey("assistant_disabled_tools")
        private val KEY_PLANIFY_ALERTS = booleanPreferencesKey("planify_alerts")
        private val KEY_PLANIFY_DAILY = booleanPreferencesKey("planify_daily")
        private val KEY_PLANIFY_SALARY_MIN = intPreferencesKey("planify_salary_min")
        private val KEY_PLAN_ALERT_MEMORY = stringPreferencesKey("plan_alert_memory")
        private val KEY_PLAN_DAILY_COUNT = stringPreferencesKey("plan_daily_count")
        private val KEY_PLAN_SALARY_PROMPTED = stringPreferencesKey("plan_salary_prompted")

        private val KEY_PENDING_CAPTURES = stringPreferencesKey("pending_captures")

        private val KEY_AMOUNT_MASKING_ENABLED = booleanPreferencesKey("amount_masking_enabled")
        private val KEY_AMOUNT_VISIBILITY_DURATION_SECONDS = intPreferencesKey("amount_visibility_duration_seconds")
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
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

    /** "System" | "Light" | "Dark". Falls back to the legacy [darkMode] flag for users who never picked one. */
    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: if (prefs[KEY_DARK_MODE] == true) "Dark" else "System"
    }

    /** Settings changed on this device whose server copy hasn't been confirmed yet (see SettingsSynchronizer). */
    val settingsPendingSync: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SETTINGS_DIRTY] ?: emptySet()
    }

    /** Has the user agreed to let the assistant use their data? Device-local on purpose. */
    val assistantConsent: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ASSISTANT_CONSENT] ?: false
    }

    val emailNotifications: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_EMAIL_NOTIFICATIONS] ?: true
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATIONS] ?: true
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

    val dateFormat: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DATE_FORMAT] ?: "DD / MM / YYYY"
    }

    val customIncomeCategories: Flow<List<PersistedCategory>> = context.dataStore.data.map { prefs ->
        parsePersistedCategories(prefs[KEY_CUSTOM_INCOME_CATEGORIES])
    }

    val customExpenseCategories: Flow<List<PersistedCategory>> = context.dataStore.data.map { prefs ->
        parsePersistedCategories(prefs[KEY_CUSTOM_EXPENSE_CATEGORIES])
    }

    val autoCaptureEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_CAPTURE_ENABLED] ?: false
    }

    val autoCapturePackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        parseAutoCapturePackages(prefs[KEY_AUTO_CAPTURE_PACKAGES])
    }

    /** What the user chose in Settings -> Assistant. Synced to the backend as one unit. */
    val assistantPrefs: Flow<AssistantPrefs> = context.dataStore.data.map { prefs ->
        AssistantPrefs(
            model = prefs[KEY_ASSISTANT_MODEL] ?: "auto",
            style = prefs[KEY_ASSISTANT_STYLE] ?: "balanced",
            tone = prefs[KEY_ASSISTANT_TONE] ?: "friendly",
            instructions = prefs[KEY_ASSISTANT_INSTRUCTIONS] ?: "",
            disabledTools = parseToolSet(prefs[KEY_ASSISTANT_DISABLED_TOOLS]),
        )
    }

    val pendingCaptures: Flow<List<PendingCapture>> = context.dataStore.data.map { prefs ->
        parsePendingCaptures(prefs[KEY_PENDING_CAPTURES])
    }

    val amountMaskingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AMOUNT_MASKING_ENABLED] ?: false
    }

    val amountVisibilityDurationSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_AMOUNT_VISIBILITY_DURATION_SECONDS] ?: 60
    }

    val pinHash: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PIN_HASH]
    }

    val pinSalt: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_PIN_SALT]
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
            // Sign-out is the account boundary: a queued capture (or the
            // auto-capture opt-in) must not carry over to the next account.
            prefs.remove(KEY_PENDING_CAPTURES)
            prefs.remove(KEY_AUTO_CAPTURE_ENABLED)
            prefs.remove(KEY_AUTO_CAPTURE_PACKAGES)
            prefs.remove(KEY_PIN_HASH)
            prefs.remove(KEY_PIN_SALT)
            // Unsynced edits belong to the account that made them.
            prefs.remove(KEY_SETTINGS_DIRTY)
            // Alert memory belongs to the account that earned it.
            prefs.remove(KEY_PLAN_ALERT_MEMORY)
            prefs.remove(KEY_PLAN_DAILY_COUNT)
            prefs.remove(KEY_PLAN_SALARY_PROMPTED)
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

    suspend fun updateAssistantConsent(agreed: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ASSISTANT_CONSENT] = agreed }
    }

    suspend fun markSettingsPending(fields: Set<String>) {
        context.dataStore.edit { prefs -> prefs[KEY_SETTINGS_DIRTY] = (prefs[KEY_SETTINGS_DIRTY] ?: emptySet()) + fields }
    }

    suspend fun clearSettingsPending(fields: Set<String>) {
        context.dataStore.edit { prefs -> prefs[KEY_SETTINGS_DIRTY] = (prefs[KEY_SETTINGS_DIRTY] ?: emptySet()) - fields }
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
            prefs[KEY_DARK_MODE] = mode == "Dark"
        }
    }

    suspend fun updateEmailNotifications(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_EMAIL_NOTIFICATIONS] = enabled
        }
    }

    suspend fun updateNotifications(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS] = enabled
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

    suspend fun updateDateFormat(format: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DATE_FORMAT] = format
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

    suspend fun updateAutoCaptureEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_CAPTURE_ENABLED] = enabled }
    }

    suspend fun updateAutoCapturePackages(packages: Set<String>) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_CAPTURE_PACKAGES] = serializeAutoCapturePackages(packages) }
    }

    // ── Planify ──────────────────────────────────────────────────────────────

    /** What the user chose in Settings -> Planify. Synced to the account (the alert memory below is not). */
    val planifySettings: Flow<PlanifySettings> = context.dataStore.data.map { prefs ->
        PlanifySettings(
            alerts = prefs[KEY_PLANIFY_ALERTS] ?: true,
            daily = prefs[KEY_PLANIFY_DAILY] ?: false,
            salaryMin = prefs[KEY_PLANIFY_SALARY_MIN] ?: 5000,
        )
    }

    suspend fun updatePlanifySettings(s: PlanifySettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PLANIFY_ALERTS] = s.alerts
            prefs[KEY_PLANIFY_DAILY] = s.daily
            prefs[KEY_PLANIFY_SALARY_MIN] = s.salaryMin
        }
    }

    suspend fun planAlertMemoryRaw(): String = context.dataStore.data.first()[KEY_PLAN_ALERT_MEMORY].orEmpty()
    suspend fun savePlanAlertMemory(raw: String) { context.dataStore.edit { it[KEY_PLAN_ALERT_MEMORY] = raw } }
    suspend fun planDailyCountRaw(): String = context.dataStore.data.first()[KEY_PLAN_DAILY_COUNT].orEmpty()
    suspend fun savePlanDailyCount(raw: String) { context.dataStore.edit { it[KEY_PLAN_DAILY_COUNT] = raw } }

    /** Months for which the "plan this month?" salary prompt has already been shown. */
    suspend fun salaryPrompted(month: String): Boolean =
        month in context.dataStore.data.first()[KEY_PLAN_SALARY_PROMPTED].orEmpty().split(',')
    suspend fun markSalaryPrompted(month: String) {
        context.dataStore.edit { prefs ->
            val all = prefs[KEY_PLAN_SALARY_PROMPTED].orEmpty().split(',').filter { it.isNotBlank() }.toMutableSet().apply { add(month) }
            prefs[KEY_PLAN_SALARY_PROMPTED] = all.sorted().takeLast(6).joinToString(",")
        }
    }

    suspend fun updateAssistantPrefs(p: AssistantPrefs) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ASSISTANT_MODEL] = p.model
            prefs[KEY_ASSISTANT_STYLE] = p.style
            prefs[KEY_ASSISTANT_TONE] = p.tone
            prefs[KEY_ASSISTANT_INSTRUCTIONS] = p.instructions
            prefs[KEY_ASSISTANT_DISABLED_TOOLS] = serializeToolSet(p.disabledTools)
        }
    }

    suspend fun addPendingCapture(capture: PendingCapture) {
        context.dataStore.edit { prefs ->
            val current = parsePendingCaptures(prefs[KEY_PENDING_CAPTURES])
            prefs[KEY_PENDING_CAPTURES] = serializePendingCaptures(current + capture)
        }
    }

    suspend fun removePendingCapture(capture: PendingCapture) {
        context.dataStore.edit { prefs ->
            val current = parsePendingCaptures(prefs[KEY_PENDING_CAPTURES])
            prefs[KEY_PENDING_CAPTURES] = serializePendingCaptures(current - capture)
        }
    }

    suspend fun updateAmountMaskingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AMOUNT_MASKING_ENABLED] = enabled }
    }

    suspend fun updateAmountVisibilityDurationSeconds(seconds: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_AMOUNT_VISIBILITY_DURATION_SECONDS] = seconds }
    }

    suspend fun setPin(hash: String, salt: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_HASH] = hash
            prefs[KEY_PIN_SALT] = salt
        }
    }

    /** Wipes locally cached/added data (custom categories) without touching the session or preferences. */
    suspend fun clearLocalData() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CUSTOM_INCOME_CATEGORIES)
            prefs.remove(KEY_CUSTOM_EXPENSE_CATEGORIES)
        }
    }
}
