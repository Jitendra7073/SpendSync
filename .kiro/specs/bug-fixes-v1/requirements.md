## Introduction

Five user-reported bugs in the SpendSync Android + Next.js finance app: authentication guards, transaction date validation, live currency conversion, Home screen layout, and dark theme consistency.

## Requirements

### Requirement 1

**User Story:** As a user whose account has been deleted, I want the app to immediately redirect me to the Login screen so I am never left stranded on a protected screen with no data. As an unauthenticated visitor, I want to be blocked from reaching any screen beyond Login and Register.

#### Acceptance Criteria

1. WHEN a signed-in user triggers "Delete Account" THEN the app clears the local session, clears the FinanceRepository cache, and navigates to the Login screen with the back stack fully cleared.
2. WHEN `MainActivity` resolves the start destination AND no valid local session token exists THEN the start destination is `Route.LOGIN`, never `Route.MAIN`.
3. WHEN the app is resumed AND the local session has been cleared THEN the user is redirected to Login automatically.
4. WHEN any authenticated API call returns HTTP 401 THEN the app clears the local session and navigates to Login regardless of which screen triggered the call.
5. WHEN a user is on the Login or Register screen THEN pressing the system back button does NOT navigate to any authenticated screen.

### Requirement 2

**User Story:** As a user adding a transaction for today's date, I want the app to accept it without showing a "future date" error, because today is never in the future.

#### Acceptance Criteria

1. WHEN a user selects today's date in the transaction date picker THEN no validation error is shown and the transaction can be submitted.
2. WHEN a user selects a date strictly after today (tomorrow or later) THEN a clear validation error is shown: "Transaction date cannot be in the future."
3. WHEN a user selects any date on or before today THEN the date is accepted without error.
4. WHEN the date comparison is performed THEN it compares calendar dates (LocalDate) only, ignoring the time component.
5. WHEN no date is explicitly selected by the user THEN the default date is today and is always treated as valid.

### Requirement 3

**User Story:** As a user who sets their preferred currency to USD, EUR, GBP, or JPY, I want all monetary amounts converted from INR using a live exchange rate so the figures I see reflect real-world values.

#### Acceptance Criteria

1. WHEN the user's selected currency is INR THEN amounts are displayed as-is with the ₹ symbol and no conversion is applied.
2. WHEN the user selects a non-INR currency THEN all displayed monetary amounts are multiplied by the live INR→target exchange rate before rendering.
3. WHEN the exchange rate is fetched THEN it is sourced from a free, no-auth-required public API (e.g. `https://open.er-api.com/v6/latest/INR`).
4. WHEN the exchange rate has been fetched THEN it is cached in memory for at least 1 hour to avoid redundant network calls.
5. WHEN the exchange rate fetch fails THEN the app falls back to displaying amounts in INR with a visible note rather than crashing or showing zeros.
6. WHEN the user changes their currency setting THEN the new rate is applied immediately to all visible amounts across all screens.
7. WHEN amounts are displayed in JPY THEN no decimal places are shown. All other currencies use 2 decimal places.
8. WHEN the exchange rate is being fetched for the first time in a session THEN amounts show in INR until the rate arrives (non-blocking).

### Requirement 4

**User Story:** As a user on the Home screen, I want to see my full month's transactions grouped by day with the most recent day at the top, my true all-time global balance in the header, and the date filter only affecting the income/expense summary tiles.

#### Acceptance Criteria

1. WHEN the Home screen loads THEN it fetches all transactions for the current calendar month.
2. WHEN transactions are displayed THEN they are grouped by calendar day with the most recent day's group at the top.
3. WHEN a day group is rendered THEN it shows a date header (e.g. "Today", "Yesterday", or "Mon, 28 Jul") above that day's transaction rows.
4. WHEN the user changes the selected date via the top-bar calendar THEN only the Income and Expense summary tiles update to reflect totals for that selected date.
5. WHEN the Total Balance figure in the header card is computed THEN it reflects the user's all-time net balance across all time.
6. WHEN the all-time balance is loading THEN a skeleton placeholder is shown in the balance card instead of zero.
7. WHEN a new transaction is added or deleted THEN both the all-time balance and the grouped list refresh automatically.
8. WHEN the Income/Expense filter tiles are tapped THEN the transaction list filters by type within the current month's grouped view.

### Requirement 5

**User Story:** As a user who enables dark mode, I want every part of the app to use a consistent dark color palette so nothing appears broken, washed-out, or with hard-coded light colors.

#### Acceptance Criteria

1. WHEN dark mode is enabled THEN all screen backgrounds use `MaterialTheme.colorScheme.background` (never a hardcoded light hex).
2. WHEN dark mode is enabled THEN all card surfaces use `MaterialTheme.colorScheme.surface` (never hardcoded white).
3. WHEN dark mode is enabled THEN all primary text uses `MaterialTheme.colorScheme.onBackground` and secondary text uses `MaterialTheme.colorScheme.onSurfaceVariant`.
4. WHEN dark mode is enabled THEN the Home screen blue header remains the brand accent color, unaffected by dark mode inversion.
5. WHEN dark mode is enabled THEN the Profile screen gradient header remains the brand gradient, unaffected by dark mode inversion.
6. WHEN dark mode is enabled THEN all dialogs use `MaterialTheme.colorScheme.surface` as their container color.
7. WHEN dark mode is enabled THEN the bottom navigation bar background matches `MaterialTheme.colorScheme.surface`.
8. WHEN dark mode is enabled THEN skeleton loader shimmer colors use theme-aware muted tones.
9. WHEN dark mode is enabled THEN AddExpenseScreen, AddTransactionTypeSheet, and CategoryIconPickerScreen overlays use theme-aware backgrounds.
10. WHEN dark mode is enabled THEN Analytics and Budget screen cards, chart containers, and pill selectors all use theme-aware surface and outline colors.
11. WHEN dark mode is enabled THEN Auth screens (Login, Register) use theme-aware backgrounds and input field colors.
12. WHEN the app theme switches between light and dark THEN the theme change is reflected immediately without an app restart.
