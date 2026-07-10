package com.example.spendsync.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.data.repository.AuthRepository
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.navigation.BottomNavItem
import com.example.spendsync.navigation.SpendSyncBottomBar
import com.example.spendsync.ui.home.HomeScreen
import com.example.spendsync.ui.placeholder.AnalyticsScreen
import com.example.spendsync.ui.placeholder.BudgetScreen
import com.example.spendsync.ui.profile.ProfileScreen
import com.example.spendsync.ui.shared.DateFilterState
import com.example.spendsync.ui.shared.MonthPickerDialog
import com.example.spendsync.ui.transaction.AddExpenseScreen
import com.example.spendsync.ui.transaction.AddTransactionTypeSheet
import com.example.spendsync.ui.transaction.TransactionType

/**
 * Main scaffold — owns the bottom nav, the shared [DateFilterState] (calendar
 * filter that every tab observes), and in-place navigation to [AddExpenseScreen]
 * when the centre FAB is tapped.
 *
 * The FAB opens [AddExpenseScreen] with a slide-up / slide-down transition so it
 * feels like a modal without requiring a nested NavHost.
 */
@Composable
fun MainScreen(
    repository: AuthRepository,
    financeRepository: FinanceRepository,
    sessionDataStore: SessionDataStore,
    onSignOut: () -> Unit,
) {
    // ── Shared date filter — one instance, all tabs read/mutate it ────────────
    val dateFilterState = remember { DateFilterState() }

    // ── Tab selection ─────────────────────────────────────────────────────────
    var selectedRoute by rememberSaveable { mutableStateOf(BottomNavItem.Home.route) }

    // ── Add-expense flow ─────────────────────────────────────────────────────
    // Tapping the FAB opens the half-screen "Income or Expense?" sheet first;
    // picking one sets presetType and opens the full form. Editing an existing
    // transaction (from Home's row actions) skips the sheet — type is already known.
    var showTypeSheet by rememberSaveable { mutableStateOf(false) }
    var presetType by remember { mutableStateOf<TransactionType?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionDto?>(null) }
    val expenseOverlayVisible = presetType != null || editingTransaction != null

    // Bumped every time the add/edit overlay closes so Home reloads its list —
    // Home only reacts to date-filter changes otherwise.
    var homeRefreshKey by remember { mutableStateOf(0) }
    fun closeExpenseOverlay() {
        presetType = null
        editingTransaction = null
        homeRefreshKey++
    }

    // Bumped to jump straight to Settings inside the Profile tab (used by
    // any tab's global search when a settings result is tapped).
    var openSettingsRequestId by remember { mutableStateOf(0) }

    // Set when Analytics/Budget's global search selects a transaction — jumps
    // to Home and opens that transaction's detail dialog there.
    var viewTransactionRequestId by remember { mutableStateOf(0) }
    var viewTransactionRequestData by remember { mutableStateOf<TransactionDto?>(null) }
    fun requestOpenSettings() {
        selectedRoute = BottomNavItem.Profile.route
        openSettingsRequestId++
    }
    fun requestViewTransaction(tx: TransactionDto) {
        selectedRoute = BottomNavItem.Home.route
        viewTransactionRequestData = tx
        viewTransactionRequestId++
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            // Hide the bottom bar when the add-expense screen is open so it
            // doesn't peek through the overlay.
            if (!expenseOverlayVisible) {
                SpendSyncBottomBar(
                    sessionDataStore = sessionDataStore,
                    currentRoute   = selectedRoute,
                    onItemSelected = { item ->
                        when {
                            item.isFab -> showTypeSheet = true
                            else       -> selectedRoute  = item.route
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            // ── Tab content ───────────────────────────────────────────────────
            AnimatedContent(
                targetState    = selectedRoute,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label          = "tab_switch",
            ) { route ->
                when (route) {
                    BottomNavItem.Analytics.route -> AnalyticsScreen(
                        sessionDataStore = sessionDataStore,
                        financeRepository = financeRepository,
                        dateFilterState = dateFilterState,
                        onOpenSettings = ::requestOpenSettings,
                        onViewTransaction = ::requestViewTransaction,
                    )
                    BottomNavItem.Budget.route    -> BudgetScreen(
                        sessionDataStore = sessionDataStore,
                        financeRepository = financeRepository,
                        dateFilterState = dateFilterState,
                        onOpenSettings = ::requestOpenSettings,
                        onViewTransaction = ::requestViewTransaction,
                    )
                    BottomNavItem.Profile.route   -> ProfileScreen(
                        sessionDataStore = sessionDataStore,
                        repository       = repository,
                        financeRepository = financeRepository,
                        openSettingsRequestId = openSettingsRequestId,
                        onSignOut        = onSignOut,
                    )
                    else -> HomeScreen(
                        repository       = repository,
                        financeRepository = financeRepository,
                        sessionDataStore = sessionDataStore,
                        dateFilterState  = dateFilterState,
                        refreshKey       = homeRefreshKey,
                        onEditTransaction = { tx -> editingTransaction = tx },
                        onOpenSettings   = ::requestOpenSettings,
                        externalViewTransactionId = viewTransactionRequestId,
                        externalViewTransaction = viewTransactionRequestData,
                        onSignOut        = onSignOut,
                    )
                }
            }

            // ── Add/Edit Expense overlay — slides up over everything with a ───
            // springy, slightly-decelerating motion instead of a flat linear tween.
            AnimatedContent(
                targetState    = expenseOverlayVisible,
                transitionSpec = {
                    if (targetState) {
                        // Opening: spring up from the bottom
                        slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            )
                        ) { it } togetherWith fadeOut(tween(0))
                    } else {
                        // Closing: slide back down
                        fadeIn(tween(0)) togetherWith
                            slideOutVertically(tween(280)) { it }
                    }
                },
                label = "add_expense_overlay",
            ) { visible ->
                if (visible) {
                    AddExpenseScreen(
                        sessionDataStore = sessionDataStore,
                        financeRepository = financeRepository,
                        editTransaction = editingTransaction,
                        initialType = presetType,
                        onBack = { closeExpenseOverlay() },
                    )
                }
            }

            // ── Income/Expense choice sheet — shown before the form ───────────
            if (showTypeSheet) {
                AddTransactionTypeSheet(
                    onDismiss = { showTypeSheet = false },
                    onSelect = { type ->
                        showTypeSheet = false
                        presetType = type
                    },
                )
            }

            // ── Global Month Picker Dialog ────────────────────────────────────
            if (dateFilterState.showMonthPicker) {
                MonthPickerDialog(
                    current   = dateFilterState.selectedDate,
                    maxDate   = java.time.LocalDate.now(),
                    onConfirm = { picked ->
                        dateFilterState.selectDate(picked)
                        dateFilterState.showMonthPicker = false
                    },
                    onDismiss = {
                        dateFilterState.showMonthPicker = false
                    }
                )
            }
        }
    }
}
