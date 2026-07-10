package com.example.spendsync.data.repository

import com.example.spendsync.data.local.SessionDataStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Pulls the signed-in user's settings from the backend and writes them into
 * [SessionDataStore] — the app keeps reading from DataStore as the single
 * source of truth, this just keeps it in sync with the server copy after
 * sign-in / sign-up / session restore. Best-effort: on failure the existing
 * local values are left untouched so the app stays usable offline.
 */
suspend fun hydrateSettingsFromBackend(
    financeRepository: FinanceRepository,
    sessionDataStore: SessionDataStore,
) {
    val result = financeRepository.getSettings()
    if (result is AuthResult.Success) {
        val settings = result.data
        sessionDataStore.updateDarkMode(settings.darkMode)
        sessionDataStore.updateNotifications(settings.pushNotifications)
        sessionDataStore.updateAutoBackup(settings.autoBackup)
        sessionDataStore.updateAccentColor(settings.accentColor)
        sessionDataStore.updateLanguage(settings.language)
        sessionDataStore.updateCurrency(settings.currency)
        sessionDataStore.updateDateFormat(settings.dateFormat)
    }
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
