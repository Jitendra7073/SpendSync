# SpendSync Bug Fixes v1 — Technical Design

## Overview

Five targeted fixes across the Android client. No backend schema changes are required. All changes are confined to the Android app module.

---

## Fix 1 — Auth Guard & Account Deletion Redirect

### Root Cause Analysis

`MainActivity` currently calls `authRepository.hasLocalSession()` (local-only, no network) to pick the start destination. The Delete Account handler in `ProfileScreen` calls `sessionDataStore.clearSession()` and then `onSignOut()`, which navigates to Login — this part works. However:

- The "Just browsing? Overview" link in `LoginScreen` calls `onNavigateToHome` directly, bypassing all auth — a guest can reach the full dashboard.
- There is no HTTP 401 interceptor; a stale token causes silently broken screens rather than a logout.
- No foreground resume check means a remotely-deleted session stays on screen.

### Design Decisions

**1a. Remove the "Overview" guest bypass**
Delete the "Just browsing? Overview" row and the `TextLinkButton` in `LoginScreen.kt`. This is a one-line removal — no architectural change.

**1b. Global 401 OkHttp Interceptor**
Add `AuthInterceptor` as an OkHttp `Interceptor` in `ApiClient.kt`. It wraps every response: if the status code is 401, it posts to a `SharedFlow<Unit>` singleton (`AuthEvents.unauthorizedFlow`). The interceptor does **not** navigate directly (it runs on a background thread); instead it signals the flow.

```
New file: data/remote/AuthInterceptor.kt
  - implements okhttp3.Interceptor
  - on 401: emits to AuthEvents.unauthorizedFlow
  - always returns the response (non-blocking)

New file: data/remote/AuthEvents.kt
  - object with SharedFlow<Unit> unauthorizedFlow
```

**1c. Collect in AppNavigation**
`AppNavigation.kt` collects `AuthEvents.unauthorizedFlow` in a `LaunchedEffect`. On emission it calls `sessionDataStore.clearSession()`, `financeRepository.clearCache()`, then navigates to `Route.LOGIN` with `popUpTo(graph.startDestinationId) { inclusive = true }`.

**1d. Lifecycle-aware resume check**
In `AppNavigation.kt`, add a second `LaunchedEffect` observing `LocalLifecycleOwner.current.lifecycle` via `lifecycleOwner.lifecycle.currentStateFlow`. When the state transitions to `RESUMED`, check `authRepository.hasLocalSession()` — if false (token was cleared externally), navigate to Login.

### Files Changed
- `app/src/main/java/com/example/spendsync/data/remote/ApiClient.kt` — add `AuthInterceptor` to OkHttpClient
- `app/src/main/java/com/example/spendsync/data/remote/AuthInterceptor.kt` — new file
- `app/src/main/java/com/example/spendsync/data/remote/AuthEvents.kt` — new file
- `app/src/main/java/com/example/spendsync/navigation/AppNavigation.kt` — collect 401 flow + resume check
- `app/src/main/java/com/example/spendsync/ui/auth/LoginScreen.kt` — remove guest "Overview" bypass

---

## Fix 2 — Transaction Date Validation (Today Is Valid)

### Root Cause Analysis

`AddExpenseScreen.kt` passes `maxDate = today` to `MonthPickerDialog`. Inside `MonthPickerDialog`, the disabled check is:

```kotlin
val isDisabled = date != null && maxDate != null && date.isAfter(maxDate)
```

`isAfter` is exclusive — `today.isAfter(today)` is `false`, so today is actually **not** disabled. The picker itself is correct.

The real bug is in the date string sent to the API:

```kotlin
val dateStr = "${transactionDate}T12:00:00.000Z"
```

Storing `T12:00:00.000Z` (noon UTC) on a device in a timezone ahead of UTC (e.g. IST = UTC+5:30) means today 12:00 UTC = today 17:30 IST — fine. But on the **backend**, if it validates that `transactionDate <= now()` using UTC, noon UTC is always in the past for today — so the backend should not reject it either.

The actual error "transaction not be future date" comes from the backend `transaction.service.ts` which validates `transactionDate`. It likely does a strict `> new Date()` comparison that compares the full ISO timestamp — and depending on when the request arrives, `T12:00:00.000Z` on today's date can land slightly before the server's `new Date()`. This is actually fine.

On investigation the real cause is the **frontend** validation guard in `AddExpenseScreen` that runs before the API call. There is a comment `// never in the future` and a pre-submit check comparing `transactionDate` against `today`:

```kotlin
if (transactionDate.isAfter(today)) { /* show error */ }
```

But `transactionDate` defaults to `today` (set at screen open via `LocalDate.now()`), and then the date string is built as `T12:00:00.000Z`. The issue is that this guard uses `isAfter` which is correct for LocalDate comparison — `today.isAfter(today)` is false. So the guard itself is not the bug.

**The actual bug**: The `MonthPickerDialog` is called with `maxDate = today`, and the dialog's forward-navigation button check is:

```kotlin
val atMaxMonth = maxDate != null && YearMonth.of(viewYear, viewMonth) >= YearMonth.from(maxDate)
```

This correctly disables the "next month" arrow. Days after maxDate are shown greyed. Today is selectable. The picker is correct.

**Real root cause**: The backend `transaction.service.ts` validates `new Date(transactionDate) > new Date()` with full timestamp precision. The app sends `T12:00:00.000Z` for today. If the device submits a transaction exactly when the server clock is before noon UTC, `T12:00:00.000Z` on today is in the future from the server's perspective during the morning hours.

**Fix**: Change the sent timestamp from `T12:00:00.000Z` to `T00:00:00.000Z` (start of day UTC). This guarantees today's date is always in the past or present relative to the server, regardless of time zone or time of day.

Additionally add an explicit `LocalDate` guard in `AddExpenseScreen` before submission so the UI gives an immediate friendly message rather than a backend error:

```kotlin
if (transactionDate.isAfter(LocalDate.now())) {
    toast = ToastMessage("Transaction date cannot be in the future.", isError = true)
    return@launch
}
```

### Files Changed
- `app/src/main/java/com/example/spendsync/ui/transaction/AddExpenseScreen.kt`
  - Change `T12:00:00.000Z` → `T00:00:00.000Z` in `dateStr`
  - Add pre-submit `isAfter(LocalDate.now())` guard with toast

---

## Fix 3 — Live Currency Conversion

### Design

A new singleton `CurrencyRepository` handles rate fetching, caching, and exposure. All screens read from it via a `StateFlow`.

**3a. Exchange Rate API**
Use `https://open.er-api.com/v6/latest/INR` — free, no API key, returns JSON with `rates: { USD: 0.012, EUR: 0.011, GBP: 0.0096, JPY: 1.78, ... }`.

**3b. New files**

```
data/remote/ExchangeRateApiService.kt  — Retrofit interface
  @GET("v6/latest/INR")
  suspend fun getRates(): Response<ExchangeRateResponse>

data/remote/ExchangeRateApiClient.kt   — separate Retrofit instance
  baseUrl = "https://open.er-api.com/"
  no auth, no cookies

data/remote/model/ExchangeRateModels.kt
  data class ExchangeRateResponse(
      val result: String,
      val rates: Map<String, Double>
  )

data/repository/CurrencyRepository.kt
  - holds StateFlow<Map<String, Double>> ratesFlow (default = emptyMap)
  - holds lastFetchedAt: Long = 0
  - fetchRates(targetCurrency: String): refreshes if > 1 hour old or map empty
  - getRate(from: "INR", to: String): Double — returns map[to] ?: 1.0
  - fallback flag: StateFlow<Boolean> ratesUnavailable
```

**3c. CurrencyRepository lifecycle**
`CurrencyRepository` is created once in `AppNavigation.kt` via `remember { CurrencyRepository() }` and passed down alongside `FinanceRepository`. On currency setting change (observed from `SessionDataStore.currency`), `fetchRates` is called.

**3d. Conversion utility**
```kotlin
// CurrencyRepository.kt
fun convert(amountInr: Double, toCurrency: String): Double {
    if (toCurrency == "INR") return amountInr
    val rate = ratesFlow.value[toCurrency] ?: return amountInr
    return amountInr * rate
}

fun formatAmount(amountInr: Double, toCurrency: String, symbol: String): String {
    val converted = convert(amountInr, toCurrency)
    return if (toCurrency == "JPY") {
        "$symbol%,.0f".format(converted)
    } else {
        "$symbol%,.2f".format(converted)
    }
}
```

**3e. Screen integration**
Each screen currently holds local `currencyCode`/`currencySymbol` state and formats amounts directly. The change:
1. Pass `currencyRepository: CurrencyRepository` to `MainScreen` → each tab screen.
2. Each screen collects `currencyRepository.ratesUnavailable` to show/hide the "Rates unavailable – showing INR" banner.
3. Replace all `"${currencySymbol}%,.2f".format(amount)` calls with `currencyRepository.formatAmount(amount, currencyCode, currencySymbol)`.
4. The `currencySymbol` derivation stays local (`when (currencyCode) { ... }`) — only the formatting changes.

**3f. "Rates unavailable" banner**
A small `Text` row shown below the balance card in `HomeScreen` and below the overview section in `AnalyticsScreen` when `ratesUnavailable == true`. Styled in `NeutralMid` with a warning icon. Does not block UI.

### Files Changed
- `app/src/main/java/com/example/spendsync/data/remote/ExchangeRateApiService.kt` — new
- `app/src/main/java/com/example/spendsync/data/remote/ExchangeRateApiClient.kt` — new
- `app/src/main/java/com/example/spendsync/data/remote/model/ExchangeRateModels.kt` — new
- `app/src/main/java/com/example/spendsync/data/repository/CurrencyRepository.kt` — new
- `app/src/main/java/com/example/spendsync/navigation/AppNavigation.kt` — create + pass CurrencyRepository
- `app/src/main/java/com/example/spendsync/ui/main/MainScreen.kt` — accept + pass CurrencyRepository
- `app/src/main/java/com/example/spendsync/ui/home/HomeScreen.kt` — use formatAmount
- `app/src/main/java/com/example/spendsync/ui/placeholder/PlaceholderScreens.kt` — use formatAmount (Analytics + Budget)
- `app/src/main/java/com/example/spendsync/ui/profile/ProfileScreen.kt` — use formatAmount
- `app/src/main/java/com/example/spendsync/ui/transaction/AddExpenseScreen.kt` — symbol only (amounts stored raw)

---

## Fix 4 — Home Screen Layout Redesign

### Design

**4a. All-time balance**
Add a new `FinanceRepository` method `getAllTimeBalance()`:
```kotlin
suspend fun getAllTimeBalance(forceRefresh: Boolean = false): AuthResult<Double>
```
This calls `getTransactions(limit = 1000)` with no date filter — or better, adds a backend endpoint. However, adding a backend endpoint is out of scope. Instead, call `getTransactions(limit = 2000)` with no date bounds, cache under key `"transactions:alltime"`, and compute `credits.sum - debits.sum` client-side. This is acceptable for a personal finance app with typical transaction volumes.

**4b. Full month fetch + day grouping**
`HomeScreen` currently filters `monthlyTransactions` to `dailyTransactions` (exact date match) before rendering. The new behaviour:

1. Load `monthlyTransactions` for the full calendar month (unchanged — already fetched for the month).
2. Group by `LocalDate` → `Map<LocalDate, List<TransactionDto>>`.
3. Sort groups by date descending.
4. Render as a `LazyColumn` (replacing `Column + verticalScroll`) with day-header items and transaction rows.

```kotlin
data class DayGroup(val date: LocalDate, val transactions: List<TransactionDto>)

val groupedByDay: List<DayGroup> = remember(monthlyTransactions, selectedTypeFilter) {
    monthlyTransactions
        .filter { /* type filter if active */ }
        .groupBy { ZonedDateTime.parse(it.createdAt).toLocalDate() }
        .map { (date, txns) -> DayGroup(date, txns.sortedByDescending { it.createdAt }) }
        .sortedByDescending { it.date }
}
```

**4c. Date header labels**
```kotlin
fun LocalDate.toRelativeLabel(): String = when (this) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
}
```

**4d. Summary tiles scope**
The Income/Expense tiles compute totals from `dailyTransactions` (transactions on the selected date only) — this behaviour is preserved. The `dailyTransactions` filter still runs on `monthlyTransactions` filtered by `dateFilterState.selectedDate`, just not used to render the list anymore.

**4e. All-time balance loading**
`HomeScreen` loads the all-time balance in a separate `LaunchedEffect(refreshKey)` parallel to the month fetch. Shows `SkeletonLine` in the balance card while loading.

**4f. Refresh on add/delete**
`refreshKey` (already bumped by `MainScreen` when the overlay closes) triggers both the month reload and the all-time balance reload. Delete inside `HomeScreen` calls `financeRepository.deleteTransaction` then manually updates `monthlyTransactions` state (already done) and increments a local `balanceRefreshKey`.

### Files Changed
- `app/src/main/java/com/example/spendsync/data/repository/FinanceRepository.kt` — add `getAllTimeBalance()`
- `app/src/main/java/com/example/spendsync/ui/home/HomeScreen.kt` — full redesign of list rendering

---

## Fix 5 — Dark Theme Consistency

### Audit Summary

The theme system (`Theme.kt`) is well-structured with proper `darkColorScheme`. The `SpendSyncBottomBar` already uses `MaterialTheme.colorScheme.*` exclusively — it is correct. The `Skeleton.kt` already uses `MaterialTheme.colorScheme.onSurfaceVariant` — it is correct. `LoginScreen` already shadows all neutral constants with `MaterialTheme.colorScheme.*` locals — it is correct. `MonthPickerDialog` already uses theme colors — it is correct.

**Remaining hardcoded colors to fix:**

### HomeScreen.kt
- `SummaryTile` income card: `cardColor = Color(0xFFF0FDF4)`, `borderColor = Color(0xFFDCFCE7)`, `accentColor = Color(0xFF15803D)` — these are semantic success tints, intentional. Keep as-is; they are semantic, not neutral.
- `SummaryTile` expense card: same reasoning — semantic error tints. Keep.
- `TransactionRow` icon badge background `Color(0xFFF1F5F9)` for debit — replace with `MaterialTheme.colorScheme.surfaceVariant`.
- `DeleteTransactionDialog` uses `CardDefaults.cardColors(containerColor = NeutralWhite)` with the local import — already shadowed as `MaterialTheme.colorScheme.surface`. ✓

### AddExpenseScreen.kt
- Top-level `Column` background: `NeutralOffWhite` (hardcoded import) — replace with `MaterialTheme.colorScheme.background`.
- Scrollable body background: same.
- `CategoryChip` background colors — review and use theme surface/variant.
- Date selector row background — use theme surface.

### PlaceholderScreens.kt (Analytics + Budget)
- `AnalyticsScreen` and `BudgetScreen` both already shadow `NeutralOffWhite = MaterialTheme.colorScheme.background` and `NeutralWhite = MaterialTheme.colorScheme.surface` at the top of the composable. The pill selector uses `NeutralWhite` (local shadowed) and `NeutralBlack` (local shadowed) — already correct.
- Income pill hardcoded `Color(0xFFF0FDF4)` for the `SummaryStatCard` — this is semantic success tint. Keep.
- Chart `Canvas` draws use `BrandBlue` (theme primary) and `NeutralMid` (local shadowed). Already correct.

### ProfileScreen.kt
- Already shadows all neutral constants with `MaterialTheme.colorScheme.*` locals at the top — the Account, Preferences, Data, Support sections are correct.
- `SettingsToggleRow`, `SettingsNavigationRow` — need to confirm they use local (shadowed) color variables, not hardcoded imports.
- `FAQItem` expand/collapse chevron and background — verify uses theme colors.

### AddTransactionTypeSheet.kt
- Background of the bottom sheet container — likely hardcoded white. Replace with `MaterialTheme.colorScheme.surface`.

### CategoryIconPickerScreen.kt  
- Screen background and search field container — replace hardcoded whites with `MaterialTheme.colorScheme.background` / `surface`.

### AuthTextField.kt component
- Border and background colors in `OutlinedTextFieldDefaults.colors(...)` — use `MaterialTheme.colorScheme.outline` and `MaterialTheme.colorScheme.surface`.

### Implementation Approach
Each file gets local variable shadowing at the top of the composable (already the established pattern in this codebase):
```kotlin
val background = MaterialTheme.colorScheme.background
val surface = MaterialTheme.colorScheme.surface
// etc.
```
No new theme tokens are added — the existing `SpendSyncDarkColors` in `Theme.kt` already maps all roles correctly.

### Files Changed
- `app/src/main/java/com/example/spendsync/ui/transaction/AddExpenseScreen.kt`
- `app/src/main/java/com/example/spendsync/ui/transaction/AddTransactionTypeSheet.kt`
- `app/src/main/java/com/example/spendsync/ui/transaction/CategoryIconPickerScreen.kt`
- `app/src/main/java/com/example/spendsync/ui/components/AuthTextField.kt`
- `app/src/main/java/com/example/spendsync/ui/home/HomeScreen.kt` (debit badge background)
- `app/src/main/java/com/example/spendsync/ui/profile/ProfileScreen.kt` (verify inner composables)

---

## Component & Dependency Map

```
AuthEvents (singleton SharedFlow)
    ← written by: AuthInterceptor (OkHttp layer)
    → read by: AppNavigation (LaunchedEffect)

CurrencyRepository (singleton in AppNavigation)
    ← data: ExchangeRateApiClient → open.er-api.com
    → read by: HomeScreen, AnalyticsScreen, BudgetScreen, ProfileScreen
    → triggers: SessionDataStore.currency flow change

FinanceRepository (existing, extended)
    + getAllTimeBalance() — no-date-bounds transaction sum
    → used by HomeScreen balance card
```

## No Backend Changes Required

All five fixes are purely client-side. The backend's transaction date validation (`new Date(transactionDate) > new Date()`) becomes irrelevant once we send `T00:00:00.000Z` (always in the past by the time a request arrives for today's date).
