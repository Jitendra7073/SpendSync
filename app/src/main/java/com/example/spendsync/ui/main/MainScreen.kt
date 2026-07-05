package com.example.spendsync.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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

    // ── Add-expense overlay flag ──────────────────────────────────────────────
    var showAddExpense by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            // Hide the bottom bar when the add-expense screen is open so it
            // doesn't peek through the overlay.
            if (!showAddExpense) {
                SpendSyncBottomBar(
                    sessionDataStore = sessionDataStore,
                    currentRoute   = selectedRoute,
                    onItemSelected = { item ->
                        when {
                            item.isFab -> showAddExpense = true
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
                    )
                    BottomNavItem.Budget.route    -> BudgetScreen(
                        sessionDataStore = sessionDataStore,
                        financeRepository = financeRepository,
                        dateFilterState = dateFilterState,
                    )
                    BottomNavItem.Profile.route   -> ProfileScreen(
                        sessionDataStore = sessionDataStore,
                        repository       = repository,
                        financeRepository = financeRepository,
                        onSignOut        = onSignOut,
                    )
                    else -> HomeScreen(
                        repository       = repository,
                        financeRepository = financeRepository,
                        sessionDataStore = sessionDataStore,
                        dateFilterState  = dateFilterState,
                        onSignOut        = onSignOut,
                    )
                }
            }

            // ── Add Expense overlay — slides up over everything ───────────────
            AnimatedContent(
                targetState    = showAddExpense,
                transitionSpec = {
                    if (targetState) {
                        // Opening: slide up from bottom
                        slideInVertically(tween(320)) { it } togetherWith
                            fadeOut(tween(0))
                    } else {
                        // Closing: slide back down
                        fadeIn(tween(0)) togetherWith
                            slideOutVertically(tween(300)) { it }
                    }
                },
                label = "add_expense_overlay",
            ) { visible ->
                if (visible) {
                    AddExpenseScreen(
                        sessionDataStore = sessionDataStore,
                        financeRepository = financeRepository,
                        onBack = { showAddExpense = false },
                    )
                }
            }

            // ── Global Month Picker Dialog ────────────────────────────────────
            if (dateFilterState.showMonthPicker) {
                MonthPickerDialog(
                    current   = dateFilterState.selectedDate,
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
