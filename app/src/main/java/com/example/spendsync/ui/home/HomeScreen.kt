package com.example.spendsync.ui.home

import com.example.spendsync.ui.i18n.LanguageManager
import com.example.spendsync.ui.settings.SegmentOption
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import com.example.spendsync.ui.components.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import com.example.spendsync.ui.components.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.IconifyApiClient
import com.example.spendsync.data.repository.AuthRepository
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.data.repository.AuthResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.example.spendsync.ui.components.Skeleton
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.search.GlobalSearchDialog
import com.example.spendsync.ui.transaction.builtInCategoryIcon
import com.example.spendsync.utils.LocalizationUtils
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.DateFilterState
import com.example.spendsync.ui.shared.MaskableAmountText
import com.example.spendsync.ui.shared.TopBarDateSearchGroup
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.BrandYellow
import com.example.spendsync.ui.theme.NeutralBlack
import com.example.spendsync.ui.theme.NeutralMid
import com.example.spendsync.ui.theme.NeutralOffWhite
import com.example.spendsync.ui.theme.NeutralWhite
import com.example.spendsync.ui.theme.SemanticError
import com.example.spendsync.notifications.HoldReminderWorker
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SwapHoriz
import com.example.spendsync.ui.settings.SettingsBackdrop
import com.example.spendsync.ui.settings.SettingsContentWidth
import com.example.spendsync.ui.settings.SettingsDivider
import com.example.spendsync.ui.settings.SettingsGroup
import com.example.spendsync.ui.settings.SettingsGroupLabel
import com.example.spendsync.ui.settings.SettingsNavRow
import com.example.spendsync.ui.settings.SettingsSegmented
import com.example.spendsync.ui.settings.SettingsTopBar
import com.example.spendsync.ui.settings.cascadeIn
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class TransactionType {
    CREDIT, DEBIT
}

data class MockTransaction(
    val id: String,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val date: LocalDate
)

/** One calendar day's worth of transactions, used to render sticky date-header groups. */
data class DayGroup(val date: LocalDate, val transactions: List<TransactionDto>)

/** "Today" / "Yesterday" / "EEE, d MMM" — the label shown on each day's sticky header. */
fun LocalDate.toRelativeLabel(): String = when (this) {
    LocalDate.now() -> tr(R.string.today)
    LocalDate.now().minusDays(1) -> tr(R.string.yesterday)
    else -> format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
}

/** Fake rows rendered inside [Skeleton] so the loading state has the exact shape of real rows. */
private val PlaceholderGroups: List<DayGroup> by lazy {
    val now = java.time.ZonedDateTime.now().toString()
    fun tx(i: Int, type: String) = TransactionDto(
        id = "placeholder_$i", userId = "", amount = "1250.00", type = type,
        merchant = "Merchant name", category = "Category name", sourceApp = null, note = null,
        createdAt = now, updatedAt = null,
    )
    listOf(DayGroup(LocalDate.now(), listOf(tx(1, "debit"), tx(2, "debit"), tx(3, "credit"))))
}

/** "Today" / "Yesterday" / weekday + the user's chosen date format (Settings → Appearance). */
private fun dayLabel(date: LocalDate, datePattern: String): String = when (date.also { LanguageManager.current }) {
    LocalDate.now() -> tr(R.string.today)
    LocalDate.now().minusDays(1) -> tr(R.string.yesterday)
    else -> date.format(DateTimeFormatter.ofPattern("EEE, $datePattern", Locale.getDefault()))
}

private fun List<TransactionDto>.toDayGroups(): List<DayGroup> =
    groupBy {
        try {
            java.time.ZonedDateTime.parse(it.createdAt).toLocalDate()
        } catch (e: Exception) {
            LocalDate.now()
        }
    }
        .map { (date, txns) -> DayGroup(date, txns.sortedByDescending { it.createdAt }) }
        .sortedByDescending { it.date }

private fun List<TransactionDto>.sumType(type: String): Double =
    filter { it.type == type }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

/** The real day-card layout, filled with placeholder rows and wrapped in [Skeleton]. */
private fun LazyListScope.skeletonDayGroups(
    datePattern: String,
    customCategoryIcons: Map<String, String>,
    amountVisibility: AmountVisibilityState,
) {
    item(key = "skeleton_groups") {
        Skeleton(loading = true) {
            Column {
                PlaceholderGroups.forEach { group ->
                    DayHeader(dayLabel(group.date, datePattern), 1250.0, 1250.0, amountVisibility)
                    DayCard {
                        group.transactions.forEachIndexed { i, tx ->
                            TransactionRow(tx, customCategoryIcons, amountVisibility, onDelete = {}, onEdit = {}, onInfo = {})
                            if (i != group.transactions.lastIndex) RowDivider()
                        }
                    }
                                    Spacer(Modifier.height(14.dp)) // real day cards end with a gap too; without it the placeholders touch
                }
            }
        }
    }
}

/** One card per day with its header — shared by Home's "Recent" and the Transactions page. */
private fun LazyListScope.dayGroupItems(
    groups: List<DayGroup>,
    datePattern: String,
    customCategoryIcons: Map<String, String>,
    amountVisibility: AmountVisibilityState,
    onDelete: (TransactionDto) -> Unit,
    onEdit: (TransactionDto) -> Unit,
    onInfo: (TransactionDto) -> Unit,
) {
    groups.forEach { group ->
        item(key = "day_${group.date}") {
            Column(Modifier.animateItem()) {
                DayHeader(
                    label = dayLabel(group.date, datePattern),
                    creditTotal = group.transactions.sumType("credit"),
                    debitTotal = group.transactions.sumType("debit"),
                    amountVisibility = amountVisibility,
                )
                DayCard {
                    group.transactions.forEachIndexed { index, tx ->
                        TransactionRow(
                            transaction = tx,
                            customCategoryIcons = customCategoryIcons,
                            amountVisibility = amountVisibility,
                            onDelete = { onDelete(tx) },
                            onEdit = { onEdit(tx) },
                            onInfo = { onInfo(tx) },
                        )
                        if (index != group.transactions.lastIndex) RowDivider()
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: AuthRepository,
    financeRepository: FinanceRepository,
    sessionDataStore: SessionDataStore,
    dateFilterState: DateFilterState,
    refreshKey: Int = 0,
    onEditTransaction: (TransactionDto) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onNavigate: (com.example.spendsync.ui.search.SearchDest) -> Unit = {},
    onOpenHolds: () -> Unit = {},
    onOpenAssistant: () -> Unit = {},
    amountVisibility: AmountVisibilityState,
    // Set when a search result is picked from a different tab (Analytics/
    // Budget) — each request uses a distinct id so repeat-selecting the same
    // transaction still reopens the dialog.
    externalViewTransactionId: Int = 0,
    externalViewTransaction: TransactionDto? = null,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf<ToastMessage?>(null) }

    val dateFormat by sessionDataStore.dateFormat.collectAsState(initial = "DD / MM / YYYY")
    val datePattern = remember(dateFormat) { LocalizationUtils.getDateFormatPattern(dateFormat) }

    // Custom categories' icons live only in local storage (iconId, from the
    // Iconify picker) — a transaction only carries the category name, so it
    // has to be cross-referenced here, not guessed from the name alone.
    val customIncomeCategories by sessionDataStore.customIncomeCategories.collectAsState(initial = emptyList())
    val customExpenseCategories by sessionDataStore.customExpenseCategories.collectAsState(initial = emptyList())
    val customCategoryIcons = remember(customIncomeCategories, customExpenseCategories) {
        (customIncomeCategories + customExpenseCategories)
            .mapNotNull { cat -> cat.iconId?.let { cat.name to it } }
            .toMap()
    }

    var monthlyTransactions by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun loadTransactions(forceRefresh: Boolean) {
        val start = dateFilterState.selectedDate.withDayOfMonth(1).toString() + "T00:00:00.000Z"
        val end = dateFilterState.selectedDate.withDayOfMonth(dateFilterState.selectedDate.lengthOfMonth()).toString() + "T23:59:59.999Z"
        when (val res = financeRepository.getTransactions(startDate = start, endDate = end, limit = 500, forceRefresh = forceRefresh)) {
            is AuthResult.Success -> monthlyTransactions = res.data
            is AuthResult.Error -> Unit
        }
    }

    LaunchedEffect(dateFilterState.selectedDate, refreshKey) {
        isLoading = true
        loadTransactions(forceRefresh = false)
        isLoading = false
    }

    // ── All-time balance (independent of the selected month) ─────────────────
    var allTimeBalance by remember { mutableStateOf<Double?>(null) }
    var isBalanceLoading by remember { mutableStateOf(true) }
    var balanceRefreshKey by remember { mutableStateOf(0) }

    suspend fun loadAllTimeBalance(forceRefresh: Boolean) {
        when (val res = financeRepository.getAllTimeBalance(forceRefresh = forceRefresh)) {
            is AuthResult.Success -> allTimeBalance = res.data
            is AuthResult.Error -> Unit
        }
    }

    // Pending holds, split by direction; net hold money = owed to me − owed by me.
    var holdsOwedToMe by remember { mutableStateOf(0.0) }
    var holdsOwedByMe by remember { mutableStateOf(0.0) }
    val holdMoney = holdsOwedToMe - holdsOwedByMe

    LaunchedEffect(refreshKey, balanceRefreshKey) {
        isBalanceLoading = true
        loadAllTimeBalance(forceRefresh = false)
        isBalanceLoading = false

        when (val res = financeRepository.getHolds(status = "pending")) {
            is AuthResult.Success -> {
                fun sum(direction: String) = res.data
                    .filter { it.direction == direction }
                    .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                holdsOwedToMe = sum("owed_to_me")
                holdsOwedByMe = sum("owed_by_me")
            }
            // Left at the last known values rather than reset to 0 — a transient
            // fetch failure should make Hold Money go stale, not vanish. Silent,
            // like loadAllTimeBalance's own failure branch.
            is AuthResult.Error -> Unit
        }
    }

    // Nested pages + the Transactions page's type filter.
    var page by remember { mutableStateOf<HomePage?>(null) }
    var typeFilter by remember { mutableStateOf(TypeFilter.All) }
    BackHandler(enabled = page != null) { page = null }

    // Plays Home's entrance once; lives here (not in the lazy list) so scrolling doesn't replay it.
    var intro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { intro = true }

    // Global search modal + per-row action state
    var showGlobalSearch by remember { mutableStateOf(false) }
    var transactionToView by remember { mutableStateOf<TransactionDto?>(null) }

    LaunchedEffect(externalViewTransactionId) {
        if (externalViewTransactionId > 0) externalViewTransaction?.let { transactionToView = it }
    }

    val totalIncome = remember(monthlyTransactions) { monthlyTransactions.sumType("credit") }
    val totalExpenses = remember(monthlyTransactions) { monthlyTransactions.sumType("debit") }
    val monthLabel = remember(dateFilterState.selectedDate, LanguageManager.current) {
        dateFilterState.selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    }

    // Home previews only the newest few; the Transactions page shows the whole (filtered) month.
    val recentGroups = remember(monthlyTransactions) {
        monthlyTransactions.sortedByDescending { it.createdAt }.take(5).toDayGroups()
    }
    val filteredGroups = remember(monthlyTransactions, typeFilter) {
        monthlyTransactions
            .filter {
                when (typeFilter) {
                    TypeFilter.Income -> it.type == "credit"
                    TypeFilter.Expense -> it.type == "debit"
                    TypeFilter.All -> true
                }
            }
            .toDayGroups()
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    fun restoreTx(tx: TransactionDto) {
        scope.launch {
            when (val r = financeRepository.restoreTransaction(tx.id)) {
                is AuthResult.Success -> {
                    monthlyTransactions = monthlyTransactions.filter { it.id != tx.id } + r.data.transaction
                    r.data.holds.forEach { HoldReminderWorker.scheduleFor(context, it) }
                    balanceRefreshKey++
                }
                is AuthResult.Error -> toast = ToastMessage(r.message, isError = true)
            }
        }
    }

    // Instant: the row leaves now, the server moves it to the Trash, Undo brings it back.
    val onDeleteTx: (TransactionDto) -> Unit = { tx ->
        monthlyTransactions = monthlyTransactions.filter { it.id != tx.id }
        scope.launch {
            when (val res = financeRepository.deleteTransaction(tx.id)) {
                is AuthResult.Success -> {
                    res.data.forEach { HoldReminderWorker.cancel(context, it) }
                    balanceRefreshKey++
                    toast = ToastMessage(tr(R.string.trash_moved), isError = false, actionLabel = tr(R.string.trash_undo), onAction = { restoreTx(tx) })
                }
                is AuthResult.Error -> {
                    if (monthlyTransactions.none { it.id == tx.id }) monthlyTransactions = monthlyTransactions + tx
                    toast = ToastMessage(res.message, isError = true)
                }
            }
        }
    }
    val onEditTx: (TransactionDto) -> Unit = { onEditTransaction(it) }
    val onInfoTx: (TransactionDto) -> Unit = { transactionToView = it }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
    AnimatedContent(
        targetState = page,
        transitionSpec = com.example.spendsync.ui.theme.motionSpec(com.example.spendsync.ui.theme.LocalMotion.current.enabled(com.example.spendsync.ui.theme.MotionKind.Transitions)) {
            val forward = targetState != null
            val enter = slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(320))
            val exit = slideOutHorizontally(tween(320)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200))
            enter togetherWith exit
        },
        label = "home_nav",
    ) { current ->
        when (current) {
            HomePage.Balance -> SettingsBackdrop {
                Column(Modifier.fillMaxSize()) {
                    SettingsTopBar(tr(R.string.balance), onBack = { page = null })
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SettingsContentWidth {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                SettingsGroupLabel(tr(R.string.all_time))
                                SettingsGroup(Modifier.cascadeIn(0)) {
                                    AmountRow(tr(R.string.net_balance), allTimeBalance ?: 0.0, amountVisibility, hint = tr(R.string.everything_earned_minus_everything_spent))
                                    SettingsDivider()
                                    AmountRow(tr(R.string.owed_to_you), holdsOwedToMe, amountVisibility, color = incomeColor(), hint = tr(R.string.pending_holds))
                                    SettingsDivider()
                                    AmountRow(tr(R.string.you_owe), holdsOwedByMe, amountVisibility, color = expenseColor(), hint = tr(R.string.pending_holds))
                                    SettingsDivider()
                                    AmountRow(tr(R.string.total_incl_holds), (allTimeBalance ?: 0.0) + holdMoney, amountVisibility)
                                }
                                SettingsGroupLabel(monthLabel)
                                SettingsGroup(Modifier.cascadeIn(1)) {
                                    AmountRow(tr(R.string.income), totalIncome, amountVisibility, color = incomeColor())
                                    SettingsDivider()
                                    AmountRow(tr(R.string.expenses), totalExpenses, amountVisibility, color = expenseColor())
                                    SettingsDivider()
                                    val saved = totalIncome - totalExpenses
                                    AmountRow(
                                        tr(R.string.saved), saved, amountVisibility,
                                        color = if (saved >= 0) incomeColor() else expenseColor(),
                                        hint = if (totalIncome > 0) tr(R.string.s_1_of_income, (saved / totalIncome * 100).toInt().coerceAtLeast(0)) else null,
                                    )
                                }
                                SettingsGroup(Modifier.cascadeIn(2)) {
                                    SettingsNavRow(Icons.Default.SwapHoriz, tr(R.string.open_holds), tr(R.string.money_you_ve_lent_or_borrowed), onClick = onOpenHolds)
                                }
                                Spacer(Modifier.height(110.dp))
                            }
                        }
                    }
                }
            }

            HomePage.Transactions -> SettingsBackdrop {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                        item { SettingsTopBar(tr(R.string.transactions), onBack = { page = null }) }
                        item {
                            SettingsGroup {
                                SettingsSegmented(
                                    options = listOf(
                                        SegmentOption(TypeFilter.All.name, TypeFilter.All.label, Icons.Default.FilterList),
                                        SegmentOption(TypeFilter.Income.name, TypeFilter.Income.label, Icons.Default.ArrowUpward),
                                        SegmentOption(TypeFilter.Expense.name, TypeFilter.Expense.label, Icons.Default.ArrowDownward),
                                    ),
                                    selected = typeFilter.name,
                                    onSelect = { key -> typeFilter = TypeFilter.valueOf(key) },
                                )
                            }
                            Text(
                                tr(R.string.s_1_2_transactions, monthLabel, filteredGroups.sumOf { it.transactions.size }),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                            )
                        }
                        if (isLoading) {
                            skeletonDayGroups(datePattern, customCategoryIcons, amountVisibility)
                        } else if (filteredGroups.isEmpty()) {
                            item { HomeEmptyState(tr(R.string.nothing_here), tr(R.string.no_1_transactions_in_2, typeFilter.label.lowercase(), monthLabel)) }
                        } else {
                            dayGroupItems(filteredGroups, datePattern, customCategoryIcons, amountVisibility, onDeleteTx, onEditTx, onInfoTx)
                        }
                        item { Spacer(Modifier.height(110.dp)) }
                    }
                }
            }

            null -> SettingsBackdrop {
                com.example.spendsync.ui.components.AppPullToRefresh(
                    onLongPull = { showGlobalSearch = true },
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            loadTransactions(forceRefresh = true)
                            loadAllTimeBalance(forceRefresh = true)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                            item {
                                Column(Modifier.statusBarsPadding()) {
                                    HomeTopBar(
                                        selectedDate = dateFilterState.selectedDate,
                                        datePattern = datePattern,
                                        onCalendarClick = { dateFilterState.showMonthPicker = true },
                                        onSearchClick = { showGlobalSearch = true },
                                    )
                                }
                            }
                            item {
                                BalanceHero(
                                    balance = allTimeBalance ?: 0.0,
                                    holdNet = holdMoney,
                                    isLoading = isBalanceLoading,
                                    amountVisibility = amountVisibility,
                                    onOpenBalance = { page = HomePage.Balance },
                                    onOpenHolds = onOpenHolds,
                                    modifier = Modifier.introIn(intro, 0),
                                )
                            }
                            item { Spacer(Modifier.height(14.dp)) }
                            item {
                                MonthSummaryCard(
                                    monthLabel = monthLabel,
                                    income = totalIncome,
                                    expenses = totalExpenses,
                                    isLoading = isLoading,
                                    amountVisibility = amountVisibility,
                                    onIncome = { typeFilter = TypeFilter.Income; page = HomePage.Transactions },
                                    onExpenses = { typeFilter = TypeFilter.Expense; page = HomePage.Transactions },
                                    modifier = Modifier.introIn(intro, 1),
                                )
                            }
                            item { Spacer(Modifier.height(22.dp)) }
                            item {
                                Column(Modifier.introIn(intro, 2)) {
                                    HomeSectionHeader(
                                        title = tr(R.string.recent_transactions),
                                        actionLabel = if (monthlyTransactions.isNotEmpty()) tr(R.string.see_all) else null,
                                        onAction = { typeFilter = TypeFilter.All; page = HomePage.Transactions },
                                    )
                                }
                            }
                            if (isLoading) {
                                skeletonDayGroups(datePattern, customCategoryIcons, amountVisibility)
                            } else if (recentGroups.isEmpty()) {
                                item {
                                    HomeEmptyState(
                                        title = tr(R.string.no_transactions_in_1, monthLabel),
                                        body = tr(R.string.tap_to_add_one_it_ll),
                                    )
                                }
                            } else {
                                dayGroupItems(recentGroups, datePattern, customCategoryIcons, amountVisibility, onDeleteTx, onEditTx, onInfoTx)
                            }
                            // Keeps the last row clear of the floating bottom bar.
                            item { Spacer(Modifier.height(110.dp)) }
                        }
                    }
                }
            }
        }
    }

    // ── Transaction details ───────────────────────────────────────────────────
    transactionToView?.let { tx ->
        TransactionInfoDialog(
            transaction = tx,
            amountVisibility = amountVisibility,
            onDismiss = { transactionToView = null },
            financeRepository = financeRepository,
        )
    }

    // ── Global search ─────────────────────────────────────────────────────────
    if (showGlobalSearch) {
        GlobalSearchDialog(
            financeRepository = financeRepository,
            sessionDataStore = sessionDataStore,
            amountVisibility = amountVisibility,
            onDismiss = { showGlobalSearch = false },
            onTransactionSelected = { tx ->
                showGlobalSearch = false
                transactionToView = tx
            },
            onNavigate = { dest ->
                showGlobalSearch = false
                onNavigate(dest)
            },
        )
    }
    }
}
