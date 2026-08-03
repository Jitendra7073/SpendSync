# SpendSync Bug Fixes v1 — Implementation Tasks

## Task 1: Auth Guard — Remove Guest Bypass & Add 401 Interceptor

- [x] 1.1 Create `AuthEvents.kt` — singleton `object AuthEvents` with `val unauthorizedFlow: MutableSharedFlow<Unit>`
- [x] 1.2 Create `AuthInterceptor.kt` — `Interceptor` that emits to `AuthEvents.unauthorizedFlow` on HTTP 401, then returns the response unchanged
- [x] 1.3 Update `ApiClient.kt` — add `AuthInterceptor` to `OkHttpClient.Builder()` before the logging interceptor
- [x] 1.4 Update `AppNavigation.kt` — add `LaunchedEffect` that collects `AuthEvents.unauthorizedFlow`, calls `sessionDataStore.clearSession()` + `financeRepository.clearCache()`, then navigates to `Route.LOGIN` with full back-stack clear
- [x] 1.5 Update `AppNavigation.kt` — add lifecycle RESUMED observer; if `authRepository.hasLocalSession()` returns false on resume, navigate to Login
- [x] 1.6 Update `LoginScreen.kt` — remove the "Just browsing? Overview" `TextLinkButton` row that calls `onNavigateToHome` without authentication

## Task 2: Transaction Date Fix

- [x] 2.1 Update `AddExpenseScreen.kt` — change `val dateStr = "${transactionDate}T12:00:00.000Z"` to `"${transactionDate}T00:00:00.000Z"`
- [x] 2.2 Update `AddExpenseScreen.kt` — add pre-submit guard in the save button's `onClick` lambda: if `transactionDate.isAfter(LocalDate.now())` show toast "Transaction date cannot be in the future." and return early

## Task 3: Live Currency Conversion

- [x] 3.1 Create `ExchangeRateModels.kt` — `data class ExchangeRateResponse(@SerializedName("rates") val rates: Map<String, Double>, @SerializedName("result") val result: String)`
- [x] 3.2 Create `ExchangeRateApiService.kt` — Retrofit interface with `@GET("v6/latest/INR") suspend fun getRates(): Response<ExchangeRateResponse>`
- [x] 3.3 Create `ExchangeRateApiClient.kt` — standalone Retrofit instance, `baseUrl = "https://open.er-api.com/"`, Gson converter, 15s timeouts, no cookies
- [x] 3.4 Create `CurrencyRepository.kt` — `StateFlow<Map<String, Double>>` for rates, `StateFlow<Boolean>` for `ratesUnavailable`, 1-hour in-memory TTL, `fetchRates()` coroutine, `convert(amountInr, toCurrency)` and `formatAmount(amountInr, toCurrency, symbol)` functions
- [x] 3.5 Update `AppNavigation.kt` — instantiate `CurrencyRepository` with `remember`, collect `sessionDataStore.currency` changes and call `currencyRepository.fetchRates(currency)` reactively; pass to `MainScreen`
- [x] 3.6 Update `MainScreen.kt` — accept and pass `currencyRepository: CurrencyRepository` to `HomeScreen`, `AnalyticsScreen`, `BudgetScreen`, and `ProfileScreen`
- [x] 3.7 Update `HomeScreen.kt` — accept `currencyRepository`, replace `"%,.2f"` format calls with `currencyRepository.formatAmount(...)`, add "Rates unavailable" banner when `ratesUnavailable == true`
- [x] 3.8 Update `PlaceholderScreens.kt` (AnalyticsScreen + BudgetScreen) — same formatAmount replacement + rates unavailable banner
- [x] 3.9 Update `ProfileScreen.kt` — same formatAmount replacement for stats strip amounts

## Task 4: Home Screen Layout Redesign

- [x] 4.1 Update `FinanceRepository.kt` — add `getAllTimeBalance(forceRefresh: Boolean = false)` that calls `getTransactions(limit = 2000)` with no date bounds, caches under key `"transactions:alltime"`, and returns `AuthResult<Double>` (credits sum minus debits sum)
- [x] 4.2 Update `HomeScreen.kt` — add `allTimeBalance: Double?` state and `isBalanceLoading: Boolean` state; load via `LaunchedEffect(refreshKey)` calling `getAllTimeBalance()`
- [x] 4.3 Update `HomeScreen.kt` — display `SkeletonLine` in the balance card header while `isBalanceLoading == true`; display `allTimeBalance` once loaded (formatted with `formatAmount`)
- [x] 4.4 Update `HomeScreen.kt` — replace the `dailyTransactions` list render with `groupedByDay: List<DayGroup>` built from `monthlyTransactions` grouped by `LocalDate` and sorted descending
- [x] 4.5 Update `HomeScreen.kt` — add `DayGroup` data class and `toRelativeLabel()` extension on `LocalDate` ("Today" / "Yesterday" / "EEE, d MMM")
- [x] 4.6 Update `HomeScreen.kt` — replace `Column + verticalScroll + forEach` transaction list with a `LazyColumn`, rendering each `DayGroup` as a sticky date header item followed by its transaction row items
- [x] 4.7 Update `HomeScreen.kt` — keep `dailyTransactions` (filtered by selected date) powering the Income/Expense summary tiles only; the type-filter (CREDIT/DEBIT) applies to `groupedByDay` not `dailyTransactions`
- [x] 4.8 Update `HomeScreen.kt` — ensure delete and edit actions trigger both `monthlyTransactions` state update and `allTimeBalance` reload

## Task 5: Dark Theme Consistency

- [x] 5.1 Update `AddExpenseScreen.kt` — replace all hardcoded `NeutralOffWhite` (import) background references with `MaterialTheme.colorScheme.background`; add local shadows for `surface`, `onBackground`, `onSurfaceVariant` at composable top
- [x] 5.2 Update `AddExpenseScreen.kt` — inside `CategoryChip` composable, replace any hardcoded background/border colors with `MaterialTheme.colorScheme.surfaceVariant` / `outline`
- [x] 5.3 Update `AddTransactionTypeSheet.kt` — replace hardcoded sheet container background with `MaterialTheme.colorScheme.surface`; verify all text and icon colors use theme roles
- [x] 5.4 Update `CategoryIconPickerScreen.kt` — replace hardcoded screen background and search field container colors with `MaterialTheme.colorScheme.background` and `surface`
- [x] 5.5 Update `AuthTextField.kt` — replace hardcoded `OutlinedTextFieldDefaults.colors(...)` border/background values with `MaterialTheme.colorScheme.outline`, `surface`, `onSurface`
- [x] 5.6 Update `HomeScreen.kt` — replace `TransactionRow` debit icon badge background `Color(0xFFF1F5F9)` with `MaterialTheme.colorScheme.surfaceVariant`
- [x] 5.7 Update `ProfileScreen.kt` — audit `SettingsToggleRow`, `SettingsNavigationRow`, `FAQItem`, `AccountInfoRow` private composables to ensure all use locally-shadowed theme colors, not hardcoded hex values; fix any remaining hardcoded neutrals
- [ ] 5.8 Smoke-test every screen in both light and dark mode: verify no white cards on dark background, no black text on dark background, no grey skeletons that are invisible on dark surfaces (manual/emulator check — not runnable in this environment, no device/emulator attached)
