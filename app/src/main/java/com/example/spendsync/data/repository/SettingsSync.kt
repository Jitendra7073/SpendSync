package com.example.spendsync.data.repository

import com.example.spendsync.data.assistant.AssistantPrefs
import com.example.spendsync.data.assistant.parseToolSet
import com.example.spendsync.data.assistant.serializeToolSet
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.local.parseAutoCapturePackages
import com.example.spendsync.data.local.serializeAutoCapturePackages
import com.example.spendsync.data.remote.model.UpdateSettingsRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One entry per user-changeable preference that lives on the account (the PIN itself never does). */
enum class SettingField { Theme, Accent, Language, DateFormat, Push, Email, Backup, Masking, MaskingSeconds, AutoCapture, AutoCapturePackages, Assistant, Planify }

/**
 * Keeps preferences identical across devices without ever losing an edit.
 *
 * Every change is written to DataStore first (the UI reads only from there), flagged as
 * "pending", then pushed. The flag is cleared only after the server confirms, so an edit
 * made offline is retried on the next launch / sign-in instead of being silently dropped.
 * [pull] never overwrites a field that still has an unconfirmed local edit.
 */
class SettingsSynchronizer(
    private val store: SessionDataStore,
    private val finance: FinanceRepository,
    private val isSignedIn: suspend () -> Boolean = { true },
) {
    /** Record a local change and try to push it. Returns false when it is still waiting to sync. */
    suspend fun changed(vararg fields: SettingField): Boolean {
        if (!isSignedIn()) return true // guests stay device-local by design
        store.markSettingsPending(fields.map { it.name }.toSet())
        return flush()
    }

    /** Push every pending field with its current local value. */
    suspend fun flush(): Boolean {
        val pending = store.settingsPendingSync.first()
            .mapNotNull { name -> SettingField.entries.firstOrNull { it.name == name } }
        if (pending.isEmpty()) return true
        val request = buildRequest(pending.toSet())
        return when (finance.updateSettings(request)) {
            is AuthResult.Success -> {
                store.clearSettingsPending(pending.map { it.name }.toSet())
                true
            }
            is AuthResult.Error -> false
        }
    }

    /** Push anything pending, then bring the account's settings onto this device. */
    suspend fun pull() {
        flush()
        val result = finance.getSettings(forceRefresh = true)
        if (result !is AuthResult.Success) return
        val s = result.data
        val pending = store.settingsPendingSync.first()
        fun free(f: SettingField) = f.name !in pending

        if (free(SettingField.Theme)) {
            if (s.themeMode != null) store.updateThemeMode(s.themeMode) else store.updateDarkMode(s.darkMode)
        }
        if (free(SettingField.Push)) store.updateNotifications(s.pushNotifications)
        if (free(SettingField.Email)) store.updateEmailNotifications(s.emailNotifications)
        if (free(SettingField.Backup)) store.updateAutoBackup(s.autoBackup)
        if (free(SettingField.Accent)) store.updateAccentColor(s.accentColor)
        if (free(SettingField.Language)) store.updateLanguage(s.language)
        if (free(SettingField.DateFormat)) store.updateDateFormat(s.dateFormat)
        if (free(SettingField.Masking)) s.amountMaskingEnabled?.let { store.updateAmountMaskingEnabled(it) }
        if (free(SettingField.MaskingSeconds)) s.amountVisibilitySeconds?.let { store.updateAmountVisibilityDurationSeconds(it) }
        if (free(SettingField.AutoCapture)) s.autoCaptureEnabled?.let { store.updateAutoCaptureEnabled(it) }
        if (free(SettingField.AutoCapturePackages)) s.autoCapturePackages?.let { store.updateAutoCapturePackages(parseAutoCapturePackages(it)) }
        if (free(SettingField.Planify) && (s.planifyAlerts != null || s.planifyDaily != null || s.planifySalaryMin != null)) {
            val cur = store.planifySettings.first()
            val merged = com.example.spendsync.data.local.PlanifySettings(
                alerts = s.planifyAlerts ?: cur.alerts, daily = s.planifyDaily ?: cur.daily, salaryMin = s.planifySalaryMin ?: cur.salaryMin,
            )
            store.updatePlanifySettings(merged)
            com.example.spendsync.notifications.PlanDailyWorker.sync(store.appContext, merged.daily)
        }
        if (free(SettingField.Assistant) && s.assistantModel != null) {
            store.updateAssistantPrefs(
                AssistantPrefs(
                    model = s.assistantModel,
                    style = s.assistantStyle ?: "balanced",
                    tone = s.assistantTone ?: "friendly",
                    instructions = s.assistantInstructions ?: "",
                    disabledTools = parseToolSet(s.assistantDisabledTools),
                ),
            )
        }
    }

    private suspend fun buildRequest(fields: Set<SettingField>): UpdateSettingsRequest {
        val theme = store.themeMode.first()
        val assistant = store.assistantPrefs.first().takeIf { SettingField.Assistant in fields }
        val planify = store.planifySettings.first().takeIf { SettingField.Planify in fields }
        return UpdateSettingsRequest(
            planifyAlerts = planify?.alerts,
            planifyDaily = planify?.daily,
            planifySalaryMin = planify?.salaryMin,
            assistantModel = assistant?.model,
            assistantStyle = assistant?.style,
            assistantTone = assistant?.tone,
            assistantInstructions = assistant?.instructions,
            assistantDisabledTools = assistant?.let { serializeToolSet(it.disabledTools) },
            themeMode = theme.takeIf { SettingField.Theme in fields },
            darkMode = (theme == "Dark").takeIf { SettingField.Theme in fields },
            accentColor = store.accentColor.first().takeIf { SettingField.Accent in fields },
            language = store.language.first().takeIf { SettingField.Language in fields },
            dateFormat = store.dateFormat.first().takeIf { SettingField.DateFormat in fields },
            pushNotifications = store.notificationsEnabled.first().takeIf { SettingField.Push in fields },
            emailNotifications = store.emailNotifications.first().takeIf { SettingField.Email in fields },
            autoBackup = store.autoBackup.first().takeIf { SettingField.Backup in fields },
            amountMaskingEnabled = store.amountMaskingEnabled.first().takeIf { SettingField.Masking in fields },
            amountVisibilitySeconds = store.amountVisibilityDurationSeconds.first().takeIf { SettingField.MaskingSeconds in fields },
            autoCaptureEnabled = store.autoCaptureEnabled.first().takeIf { SettingField.AutoCapture in fields },
            autoCapturePackages = serializeAutoCapturePackages(store.autoCapturePackages.first())
                .takeIf { SettingField.AutoCapturePackages in fields },
        )
    }
}

/** Called after sign-in / sign-up / session restore. Best-effort: on failure local values stay as they are. */
suspend fun hydrateSettingsFromBackend(
    financeRepository: FinanceRepository,
    sessionDataStore: SessionDataStore,
) {
    SettingsSynchronizer(sessionDataStore, financeRepository).pull()
}

/**
 * Fired once at app start (alongside [hydrateSettingsFromBackend]) so every
 * tab's first-visit data is already sitting in [FinanceRepository]'s cache by
 * the time the user navigates there — no spinner on the first tap.
 *
 * The date bounds here are deliberately the same "today"-anchored values each
 * screen computes for its own *default* view (this month's transactions,
 * this month's dashboard summary) so the cache key matches exactly and the
 * screen's own fetch is a cache hit, not a second network call. If the user
 * has changed the date filter by the time they land on a tab, that screen's
 * own fetch just runs normally for the new range.
 */
suspend fun warmFinanceCache(financeRepository: FinanceRepository) {
    val today = LocalDate.now()
    val monthStart = today.withDayOfMonth(1)
    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
    val startStr = "${monthStart}T00:00:00.000Z"
    val endStr = "${monthEnd}T23:59:59.999Z"
    val activeMonth = today.toString().slice(0..6) // "YYYY-MM"

    coroutineScope {
        launch { financeRepository.getTransactions(startDate = startStr, endDate = endStr, limit = 500) }
        launch { financeRepository.getDashboardSummary(month = activeMonth) }
        launch { financeRepository.getBudgets(month = activeMonth) }
        launch { financeRepository.getCategories() }
    }
}
