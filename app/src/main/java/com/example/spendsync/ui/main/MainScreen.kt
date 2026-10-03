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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.spendsync.ui.holds.HoldsScreen
import com.example.spendsync.ui.home.HomeScreen
import com.example.spendsync.ui.analytics.AnalyticsScreen
import com.example.spendsync.ui.budget.BudgetScreen
import com.example.spendsync.ui.profile.ProfileScreen
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.DateFilterState
import com.example.spendsync.ui.shared.MonthPickerDialog
import com.example.spendsync.ui.shared.PinSetupDialog
import com.example.spendsync.ui.shared.PinUnlockDialog
import com.example.spendsync.ui.transaction.AddExpenseScreen
import com.example.spendsync.ui.transaction.AddTransactionTypeSheet
import com.example.spendsync.ui.transaction.TransactionType
import kotlinx.coroutines.launch

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

    // ── Shared amount-visibility session — one unlock, every tab reflects it ──
    val amountVisibility = remember { AmountVisibilityState() }

    // The masking feature is opt-in (Settings toggle) — keep the shared
    // session's isMaskingEnabled in sync with the persisted preference so
    // masking never applies before the user has actually turned it on.
    val amountMaskingEnabled by sessionDataStore.amountMaskingEnabled.collectAsState(initial = false)
    LaunchedEffect(amountMaskingEnabled) {
        amountVisibility.setMaskingEnabled(amountMaskingEnabled)
    }

    // Shown when PinUnlockDialog detects masking is on but no PIN exists
    // (post sign-out) and the user opts to set one up from there.
    var showPinSetupFallback by remember { mutableStateOf(false) }

    LaunchedEffect(amountVisibility.unlockedUntil) {
        val until = amountVisibility.unlockedUntil ?: return@LaunchedEffect
        val remainingMs = java.time.Duration.between(java.time.Instant.now(), until).toMillis()
        if (remainingMs > 0) {
            kotlinx.coroutines.delay(remainingMs)
        }
        amountVisibility.lock()
    }

    // ── Tab selection — a real swipeable pager (Home/Analytics/Budget/Profile;
    // the FAB "Add" isn't a page, it opens the overlay below instead) so tabs
    // can be reached either by dragging left/right or by tapping the bottom bar.
    val pages = remember {
        listOf(BottomNavItem.Home.route, BottomNavItem.Analytics.route, BottomNavItem.Budget.route, BottomNavItem.Profile.route)
    }
    val pagerState = rememberPagerState(initialPage = 0) { pages.size }
    val selectedRoute = pages[pagerState.currentPage]
    val pagerScope = rememberCoroutineScope()

    // ── Add-expense flow ─────────────────────────────────────────────────────
    // Tapping the FAB opens the half-screen "Income or Expense?" sheet first;
    // picking one sets presetType and opens the full form. Editing an existing
    // transaction (from Home's row actions) skips the sheet — type is already known.
    var showTypeSheet by rememberSaveable { mutableStateOf(false) }
    var presetType by remember { mutableStateOf<TransactionType?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionDto?>(null) }
    val expenseOverlayVisible = presetType != null || editingTransaction != null

    // ── Holds list overlay — reached from Home's "Hold Money" stat ────────────
    var showHolds by rememberSaveable { mutableStateOf(false) }

    // ── Assistant chat overlay — opened from the sparkle button in the top bar ─
    var showAssistant by rememberSaveable { mutableStateOf(false) }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val assistantViewModel: com.example.spendsync.ui.assistant.AssistantViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.example.spendsync.ui.assistant.AssistantViewModel.factory(
            repository = remember { com.example.spendsync.data.assistant.AssistantRepository(sessionDataStore) },
            store = remember { com.example.spendsync.data.assistant.ChatStore(appContext) },
            session = sessionDataStore,
        ),
    )

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
        // A direct jump from another tab's search — snap instead of animating
        // through every page in between.
        pagerScope.launch { pagerState.scrollToPage(pages.indexOf(BottomNavItem.Profile.route)) }
        openSettingsRequestId++
    }
    fun requestViewTransaction(tx: TransactionDto) {
        pagerScope.launch { pagerState.scrollToPage(pages.indexOf(BottomNavItem.Home.route)) }
        viewTransactionRequestData = tx
        viewTransactionRequestId++
    }

    // Bottom-bar taps jump instantly, no slide — only a hand-drag on the
    // pager itself animates. Sliding a tap through every in-between page was
    // where the jank was (Analytics/Budget composing live mid-animation);
    // an instant switch has no animation for that to happen during.
    fun jumpToTab(target: Int) {
        pagerScope.launch { pagerState.scrollToPage(target) }
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
                            else -> {
                                val index = pages.indexOf(item.route)
                                if (index >= 0) jumpToTab(index)
                            }
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
            // ── Tab content — swipe left/right or tap the bottom bar ──────────
            // beyondViewportPageCount keeps the neighboring tab pre-composed
            // (its network fetch, chart draws, layout) so that work happens
            // once you land on a page, not mid-drag when you swipe to it —
            // that on-demand composition was the stutter.
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !expenseOverlayVisible,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (pages[page]) {
                    BottomNavItem.Analytics.route -> AnalyticsScreen(
                        sessionDataStore = sessionDataStore,
                        financeRepository = financeRepository,
                        dateFilterState = dateFilterState,
                        amountVisibility = amountVisibility,
                        onOpenSettings = ::requestOpenSettings,
                        onViewTransaction = ::requestViewTransaction,
                        onOpenAssistant = { showAssistant = true },
                    )
                    BottomNavItem.Budget.route    -> BudgetScreen(
                        sessionDataStore = sessionDataStore,
                        financeRepository = financeRepository,
                        dateFilterState = dateFilterState,
                        amountVisibility = amountVisibility,
                        onOpenSettings = ::requestOpenSettings,
                        onViewTransaction = ::requestViewTransaction,
                        onOpenAssistant = { showAssistant = true },
                    )
                    BottomNavItem.Profile.route   -> ProfileScreen(
                        sessionDataStore = sessionDataStore,
                        repository       = repository,
                        financeRepository = financeRepository,
                        openSettingsRequestId = openSettingsRequestId,
                        amountVisibility = amountVisibility,
                        onSignOut        = onSignOut,
                    )
                    else -> HomeScreen(
                        repository       = repository,
                        financeRepository = financeRepository,
                        sessionDataStore = sessionDataStore,
                        dateFilterState  = dateFilterState,
                        amountVisibility = amountVisibility,
                        refreshKey       = homeRefreshKey,
                        onEditTransaction = { tx -> editingTransaction = tx },
                        onOpenSettings   = ::requestOpenSettings,
                        externalViewTransactionId = viewTransactionRequestId,
                        externalViewTransaction = viewTransactionRequestData,
                        onSignOut        = onSignOut,
                        onOpenHolds      = { showHolds = true },
                        onOpenAssistant  = { showAssistant = true },
                    )
                }
            }

            // ── Add/Edit Expense overlay — slides up/down with the SAME spring ─
            // both ways. It used to spring in but tween out (flat 280ms) —
            // two different motion characters for one modal read as a glitch,
            // not two deliberate choices.
            val overlaySlideSpec = spring<androidx.compose.ui.unit.IntOffset>(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
            AnimatedContent(
                targetState    = expenseOverlayVisible,
                transitionSpec = {
                    if (targetState) {
                        // Opening: spring up from the bottom
                        slideInVertically(animationSpec = overlaySlideSpec) { it } togetherWith fadeOut(tween(0))
                    } else {
                        // Closing: spring back down — mirrors the opening motion
                        fadeIn(tween(0)) togetherWith
                            slideOutVertically(animationSpec = overlaySlideSpec) { it }
                    }
                },
                label = "add_expense_overlay",
            ) { visible ->
                if (visible) {
                    AddExpenseScreen(
                        sessionDataStore = sessionDataStore,
                        financeRepository = financeRepository,
                        amountVisibility = amountVisibility,
                        editTransaction = editingTransaction,
                        initialType = presetType,
                        onBack = { closeExpenseOverlay() },
                    )
                }
            }

            // ── Holds list overlay — same spring as the add/edit-expense one ──
            AnimatedContent(
                targetState    = showHolds,
                transitionSpec = {
                    if (targetState) {
                        slideInVertically(animationSpec = overlaySlideSpec) { it } togetherWith fadeOut(tween(0))
                    } else {
                        fadeIn(tween(0)) togetherWith
                            slideOutVertically(animationSpec = overlaySlideSpec) { it }
                    }
                },
                label = "holds_overlay",
            ) { visible ->
                if (visible) {
                    HoldsScreen(
                        financeRepository = financeRepository,
                        sessionDataStore = sessionDataStore,
                        amountVisibility = amountVisibility,
                        // Home stays composed underneath this overlay, so settling
                        // a hold here leaves its balance card stale unless we bump
                        // the same key closeExpenseOverlay() uses.
                        onBack = { showHolds = false; homeRefreshKey++ },
                    )
                }
            }

            // ── Assistant chat — slides up like the other full-screen overlays ──
            AnimatedContent(
                targetState    = showAssistant,
                transitionSpec = {
                    if (targetState) {
                        slideInVertically(animationSpec = overlaySlideSpec) { it } togetherWith fadeOut(tween(0))
                    } else {
                        fadeIn(tween(0)) togetherWith slideOutVertically(animationSpec = overlaySlideSpec) { it }
                    }
                },
                label = "assistant_overlay",
            ) { visible ->
                if (visible) {
                    androidx.activity.compose.BackHandler { showAssistant = false }
                    com.example.spendsync.ui.assistant.AssistantScreen(
                        viewModel = assistantViewModel,
                        sessionDataStore = sessionDataStore,
                        amountVisibility = amountVisibility,
                        currentScreen = when (selectedRoute) {
                            BottomNavItem.Analytics.route -> "analytics"
                            BottomNavItem.Budget.route -> "budget"
                            BottomNavItem.Profile.route -> "profile"
                            else -> "home"
                        },
                        onBack = { showAssistant = false },
                        onOpenScreen = { screen ->
                            showAssistant = false
                            when (screen) {
                                "home" -> jumpToTab(pages.indexOf(BottomNavItem.Home.route))
                                "analytics" -> jumpToTab(pages.indexOf(BottomNavItem.Analytics.route))
                                "budget" -> jumpToTab(pages.indexOf(BottomNavItem.Budget.route))
                                "profile" -> jumpToTab(pages.indexOf(BottomNavItem.Profile.route))
                                "holds" -> showHolds = true
                                "add_transaction" -> showTypeSheet = true
                            }
                        },
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

            // ── Shared PIN-unlock dialog — triggered from any tab's eye icon ──
            if (amountVisibility.showUnlockPrompt) {
                PinUnlockDialog(
                    sessionDataStore = sessionDataStore,
                    onUnlock = { durationSeconds -> amountVisibility.unlock(durationSeconds) },
                    onDismiss = { amountVisibility.dismissUnlockPrompt() },
                    onNeedsSetup = {
                        amountVisibility.dismissUnlockPrompt()
                        showPinSetupFallback = true
                    },
                )
            }

            // ── PIN-setup fallback — reached when masking is on but sign-out
            // wiped the PIN (SessionDataStore.clearSession keeps the masking
            // preference but clears pinHash/pinSalt as an account-scoped
            // secret). No auto-unlock after this; the user just taps the eye
            // icon again to unlock with the new PIN. ─────────────────────────
            if (showPinSetupFallback) {
                PinSetupDialog(
                    sessionDataStore = sessionDataStore,
                    requireCurrentPin = false,
                    onDone = { showPinSetupFallback = false },
                    onDismiss = { showPinSetupFallback = false },
                )
            }
        }
    }
}
