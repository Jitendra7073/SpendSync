package com.example.spendsync.ui.budget

import com.example.spendsync.ui.i18n.categoryLabel
import androidx.annotation.StringRes
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.spendsync.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.example.spendsync.ui.components.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.model.CategoryBreakdownDto
import com.example.spendsync.data.remote.model.DashboardSummaryDto
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.charts.BarGroup
import com.example.spendsync.ui.charts.BarSeries
import com.example.spendsync.ui.charts.ChartPoint
import com.example.spendsync.ui.charts.InteractiveBarChart
import com.example.spendsync.ui.charts.InteractiveLineChart
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppChip
import com.example.spendsync.ui.components.AppDialog
import com.example.spendsync.ui.components.AppTextField
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.DialogAction
import com.example.spendsync.ui.components.Skeleton
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.home.AmountRow
import com.example.spendsync.ui.home.HeroCard
import com.example.spendsync.ui.home.HeroChip
import com.example.spendsync.ui.home.HomeEmptyState
import com.example.spendsync.ui.home.HomeSectionHeader
import com.example.spendsync.ui.home.HomeTopBar
import com.example.spendsync.ui.home.animatedAmount
import com.example.spendsync.ui.home.animatedFraction
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.home.introIn
import com.example.spendsync.ui.search.GlobalSearchDialog
import com.example.spendsync.ui.settings.SettingsBackdrop
import com.example.spendsync.ui.settings.SettingsContentWidth
import com.example.spendsync.ui.settings.SettingsDivider
import com.example.spendsync.ui.settings.SettingsGroup
import com.example.spendsync.ui.settings.SettingsInfoRow
import com.example.spendsync.ui.settings.SettingsTopBar
import com.example.spendsync.ui.settings.cascadeIn
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.DateFilterState
import com.example.spendsync.ui.shared.MaskableAmountText
import com.example.spendsync.ui.theme.SemanticWarning
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Usage bands: under 75% is on track, 75–100% is close, above is over. */
private enum class BudgetStatus(@StringRes val labelRes: Int) {
    OnTrack(R.string.on_track), Close(R.string.near_limit), Over(R.string.over);

    val label: String get() = tr(labelRes)
}

private fun statusFor(percentUsed: Double): BudgetStatus = when {
    percentUsed > 100.0 -> BudgetStatus.Over
    percentUsed >= 75.0 -> BudgetStatus.Close
    else -> BudgetStatus.OnTrack
}

@Composable
private fun BudgetStatus.color(): Color = when (this) {
    BudgetStatus.OnTrack -> MaterialTheme.colorScheme.primary
    BudgetStatus.Close -> SemanticWarning
    BudgetStatus.Over -> expenseColor()
}

// ── Budget ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    sessionDataStore: SessionDataStore,
    financeRepository: FinanceRepository,
    dateFilterState: DateFilterState,
    amountVisibility: AmountVisibilityState,
    onOpenSettings: () -> Unit = {},
    onViewTransaction: (TransactionDto) -> Unit = {},
    onOpenAssistant: () -> Unit = {},
    /** Set when Budget is shown as its own page (not a tab): adds a back arrow to the top bar. */
    onBack: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme

    var summaryData by remember { mutableStateOf<DashboardSummaryDto?>(null) }
    // The month's transactions, used only to draw the spending-pace charts.
    var monthTxs by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var isBudgetLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    var showCreateBudgetDialog by remember { mutableStateOf(false) }
    // Category whose detail page is open; looked up by name so it survives reloads.
    var openCategory by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = openCategory != null) { openCategory = null }

    var intro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { intro = true }
    val scope = rememberCoroutineScope()

    val activeMonth = remember(dateFilterState.selectedDate) {
        dateFilterState.selectedDate.toString().slice(0..6) // "YYYY-MM"
    }

    suspend fun fetchBudgetData(forceRefresh: Boolean) {
        when (val res = financeRepository.getDashboardSummary(month = activeMonth, forceRefresh = forceRefresh)) {
            is AuthResult.Success -> summaryData = res.data
            is AuthResult.Error -> Unit
        }
        val month = dateFilterState.selectedDate
        val start = month.withDayOfMonth(1).toString() + "T00:00:00.000Z"
        val end = month.withDayOfMonth(month.lengthOfMonth()).toString() + "T23:59:59.999Z"
        when (val res = financeRepository.getTransactions(startDate = start, endDate = end, limit = 500, forceRefresh = forceRefresh)) {
            is AuthResult.Success -> monthTxs = res.data
            is AuthResult.Error -> Unit
        }
    }

    val loadData = {
        isBudgetLoading = true
        scope.launch {
            fetchBudgetData(forceRefresh = false)
            isBudgetLoading = false
        }
    }

    LaunchedEffect(activeMonth) { loadData() }

    val totals = summaryData?.totals
    val totalBudget = totals?.totalBudget ?: 0.0
    val totalSpent = totals?.totalSpent ?: 0.0
    // Most-used budgets first so the ones needing attention are at the top.
    val breakdown = remember(summaryData) {
        summaryData?.categoryBreakdown
            ?.filter { it.budget != null }
            ?.sortedByDescending { it.percentageUsed ?: 0.0 }
            ?: emptyList()
    }
    val onTrack = breakdown.count { statusFor(it.percentageUsed ?: 0.0) == BudgetStatus.OnTrack }
    val close = breakdown.count { statusFor(it.percentageUsed ?: 0.0) == BudgetStatus.Close }
    val over = breakdown.count { statusFor(it.percentageUsed ?: 0.0) == BudgetStatus.Over }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        AnimatedContent(
            targetState = openCategory,
            transitionSpec = {
                val forward = targetState != null
                val enter = slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(320))
                val exit = slideOutHorizontally(tween(320)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200))
                enter togetherWith exit
            },
            label = "budget_nav",
        ) { current ->
            val detail = current?.let { name -> breakdown.firstOrNull { it.category == name } }
            if (detail != null) {
                CategoryDetailPage(detail, dateFilterState.selectedDate, monthTxs, amountVisibility, onBack = { openCategory = null })
            } else {
                SettingsBackdrop {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            scope.launch {
                                isRefreshing = true
                                fetchBudgetData(forceRefresh = true)
                                isRefreshing = false
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            SettingsContentWidth {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Column(Modifier.statusBarsPadding()) {
                                        HomeTopBar(
                                            onBack = onBack,
                                            title = tr(R.string.budget),
                                            icon = Icons.Default.Wallet,
                                            selectedDate = dateFilterState.selectedDate,
                                            datePattern = "MMMM yyyy",
                                            onCalendarClick = { dateFilterState.showMonthPicker = true },
                                            onSearchClick = { showGlobalSearch = true },
                                            onAssistantClick = onOpenAssistant,
                                        )
                                    }

                                    Skeleton(loading = isBudgetLoading) {
                                        BudgetHero(
                                            if (isBudgetLoading) 50000.0 else totalBudget,
                                            if (isBudgetLoading) 32000.0 else totalSpent,
                                            amountVisibility,
                                            Modifier.introIn(intro, 0),
                                        )
                                    }

                                    if (isBudgetLoading || breakdown.isNotEmpty()) {
                                        Skeleton(loading = isBudgetLoading) {
                                            StatusStrip(
                                                if (isBudgetLoading) 3 else onTrack,
                                                if (isBudgetLoading) 2 else close,
                                                if (isBudgetLoading) 1 else over,
                                                Modifier.introIn(intro, 1),
                                            )
                                        }
                                    }

                                    if (!isBudgetLoading && totalBudget > 0.0) {
                                        PaceCard(monthTxs.filter { it.type == "debit" }, dateFilterState.selectedDate, totalBudget, amountVisibility, Modifier.introIn(intro, 2))
                                    }
                                    if (!isBudgetLoading && breakdown.isNotEmpty()) {
                                        BudgetVsSpentCard(breakdown, amountVisibility, Modifier.introIn(intro, 3))
                                    }

                                    AddBudgetButton(Modifier.introIn(intro, 4)) { showCreateBudgetDialog = true }

                                    HomeSectionHeader(tr(R.string.category_limits))

                                    if (isBudgetLoading) {
                                        Skeleton(loading = true) {
                                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                                repeat(3) { CategoryBudgetCard(PlaceholderCategory, amountVisibility) {} }
                                            }
                                        }
                                    } else if (breakdown.isEmpty()) {
                                        HomeEmptyState(tr(R.string.no_category_budgets_yet), tr(R.string.set_a_limit_for_a_category))
                                    } else {
                                        breakdown.forEach { cat -> CategoryBudgetCard(cat, amountVisibility) { openCategory = cat.category } }
                                    }
                                    Spacer(Modifier.height(110.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCreateBudgetDialog) {
            CreateBudgetDialog(
                onDismiss = { showCreateBudgetDialog = false },
                onConfirm = { category, limit ->
                    scope.launch {
                        val res = financeRepository.createBudget(category = category, month = activeMonth, limitAmount = limit)
                        if (res is AuthResult.Success) {
                            toast = ToastMessage(tr(R.string.budget_added), isError = false)
                            loadData()
                            showCreateBudgetDialog = false
                        } else {
                            // Keep the dialog open so the user can pick a different
                            // category/month instead of the save silently vanishing.
                            toast = ToastMessage((res as AuthResult.Error).message, isError = true)
                        }
                    }
                },
            )
        }

        if (showGlobalSearch) {
            GlobalSearchDialog(
                financeRepository = financeRepository,
                sessionDataStore = sessionDataStore,
                amountVisibility = amountVisibility,
                onDismiss = { showGlobalSearch = false },
                onTransactionSelected = { tx ->
                    showGlobalSearch = false
                    onViewTransaction(tx)
                },
                onOpenSettings = {
                    showGlobalSearch = false
                    onOpenSettings()
                },
            )
        }
    }
}

private val PlaceholderCategory = CategoryBreakdownDto(
    category = "Category name", spent = 4200.0, earned = 0.0, transactionCount = 6,
    budget = 6000.0, remaining = 1800.0, percentageUsed = 70.0,
)

// ── Pieces ───────────────────────────────────────────────────────────────────

@Composable
private fun BudgetHero(
    totalBudget: Double,
    totalSpent: Double,
    amountVisibility: AmountVisibilityState,
    modifier: Modifier = Modifier,
) {
    val onHero = MaterialTheme.colorScheme.onPrimary
    val isOver = totalBudget > 0 && totalSpent > totalBudget
    val progress = animatedFraction(if (totalBudget > 0) (totalSpent / totalBudget).toFloat().coerceIn(0f, 1f) else 0f)
    val shownBudget = animatedAmount(totalBudget)
    val shownSpent = animatedAmount(totalSpent)
    val shownLeft = animatedAmount(if (isOver) totalSpent - totalBudget else (totalBudget - totalSpent).coerceAtLeast(0.0))

    HeroCard(modifier) {
        Text(tr(R.string.monthly_budget), color = onHero.copy(alpha = 0.8f), fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        if (totalBudget <= 0.0) {
            Text(tr(R.string.no_budget_set), color = onHero, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(tr(R.string.add_a_category_limit_below_to), color = onHero.copy(alpha = 0.8f), fontSize = 13.sp)
            return@HeroCard
        }
        MaskableAmountText(shownBudget, amountVisibility, color = onHero, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(onHero.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(onHero))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroChip(tr(R.string.spent), shownSpent, amountVisibility, Modifier.weight(1f))
            HeroChip(if (isOver) tr(R.string.over_by) else tr(R.string.remaining), shownLeft, amountVisibility, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusStrip(onTrack: Int, close: Int, over: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(vertical = 14.dp),
    ) {
        StatusStat(onTrack, BudgetStatus.OnTrack, Modifier.weight(1f))
        StatusStat(close, BudgetStatus.Close, Modifier.weight(1f))
        StatusStat(over, BudgetStatus.Over, Modifier.weight(1f))
    }
}

@Composable
private fun StatusStat(count: Int, status: BudgetStatus, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = status.color())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(status.color()))
            Spacer(Modifier.width(6.dp))
            Text(status.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddBudgetButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppButton(
        text = tr(R.string.set_category_budget),
        onClick = onClick,
        modifier = modifier.padding(horizontal = 16.dp),
        variant = ButtonVariant.Tonal,
        size = ButtonSize.Large,
        leadingIcon = Icons.Default.Add,
        fullWidth = true,
    )
}

@Composable
private fun CategoryBudgetCard(cat: CategoryBreakdownDto, amountVisibility: AmountVisibilityState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val limit = cat.budget ?: 0.0
    val pct = cat.percentageUsed ?: 0.0
    val status = statusFor(pct)
    val progress = animatedFraction((pct / 100.0).toFloat().coerceIn(0f, 1f))

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glassCard()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(categoryLabel(cat.category), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = scheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1)
            MaskableAmountText(cat.spent, amountVisibility, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = scheme.onSurface)
            Text(" / ", fontSize = 13.sp, color = scheme.onSurfaceVariant)
            MaskableAmountText(limit, amountVisibility, fontSize = 13.sp, color = scheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(scheme.outlineVariant.copy(alpha = 0.5f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(status.color()))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(status.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = status.color())
            Text(tr(R.string.s_0f_used).format(pct), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
    }
}

/** Cumulative spend by day for [month] (up to today if it's the current month) plus the even-pace budget line. */
private fun paceSeries(debits: List<TransactionDto>, month: LocalDate, budget: Double): Pair<List<ChartPoint>, List<Double>> {
    val len = month.lengthOfMonth()
    val last = if (YearMonth.from(month) == YearMonth.now()) LocalDate.now().dayOfMonth else len
    val byDay = HashMap<Int, Double>()
    debits.forEach { t ->
        val d = try { java.time.ZonedDateTime.parse(t.createdAt).dayOfMonth } catch (e: Exception) { null }
        if (d != null) byDay[d] = (byDay[d] ?: 0.0) + (t.amount.toDoubleOrNull() ?: 0.0)
    }
    var run = 0.0
    val points = (1..last).map { d ->
        run += byDay[d] ?: 0.0
        ChartPoint(tr(R.string.day_1, d), run)
    }
    val guide = (1..last).map { d -> budget * d / len }
    return points to guide
}

/** Spend so far vs. an even spread of the budget across the month — tap or drag to compare any day. */
@Composable
private fun PaceCard(
    debits: List<TransactionDto>,
    month: LocalDate,
    budget: Double,
    vis: AmountVisibilityState,
    modifier: Modifier = Modifier,
    title: String = tr(R.string.spending_pace),
) {
    val scheme = MaterialTheme.colorScheme
    val (points, guide) = remember(debits, month, budget) { paceSeries(debits, month, budget) }
    val gap = (points.lastOrNull()?.value ?: 0.0) - (guide.lastOrNull() ?: 0.0)
    val gapColor = if (gap > 0) expenseColor() else incomeColor()
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(16.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (gap > 0) tr(R.string.ahead_of_pace_by) else tr(R.string.under_pace_by), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = gapColor)
            MaskableAmountText(kotlin.math.abs(gap), vis, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = gapColor)
            Text(tr(R.string.dashed_even_spread), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        InteractiveLineChart(
            points = points,
            color = if (gap > 0) expenseColor() else scheme.primary,
            vis = vis,
            emptyIcon = Icons.Default.Wallet,
            emptyText = tr(R.string.no_spending_yet_this_month),
            plotHeight = 140.dp,
            valueLabel = tr(R.string.spent_so_far),
            guide = guide,
            guideLabel = tr(R.string.even_pace),
        )
    }
}

/** Limit vs. spent for every budgeted category, side by side. Tap a pair for the numbers. */
@Composable
private fun BudgetVsSpentCard(breakdown: List<CategoryBreakdownDto>, vis: AmountVisibilityState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(16.dp)) {
        Text(tr(R.string.budget_vs_spent), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        Spacer(Modifier.height(4.dp))
        InteractiveBarChart(
            groups = breakdown.take(6).map { BarGroup(categoryLabel(it.category), listOf(it.budget ?: 0.0, it.spent)) },
            series = listOf(BarSeries(tr(R.string.limit), scheme.outline), BarSeries(tr(R.string.spent), scheme.primary)),
            vis = vis,
            emptyIcon = Icons.Default.Wallet,
            emptyText = tr(R.string.no_category_budgets_yet_2),
            plotHeight = 140.dp,
            hint = tr(R.string.tap_a_category_to_compare),
        )
    }
}

/** One category's budget: figures, usage bar, and (for the current month) what's safe to spend per day. */
@Composable
private fun CategoryDetailPage(
    cat: CategoryBreakdownDto,
    month: LocalDate,
    monthTxs: List<TransactionDto>,
    amountVisibility: AmountVisibilityState,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val limit = cat.budget ?: 0.0
    val pct = cat.percentageUsed ?: 0.0
    val status = statusFor(pct)
    val progress = animatedFraction((pct / 100.0).toFloat().coerceIn(0f, 1f))
    val left = limit - cat.spent

    SettingsBackdrop {
        Column(Modifier.fillMaxSize()) {
            SettingsTopBar(categoryLabel(cat.category), onBack)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SettingsContentWidth {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(
                            Modifier.cascadeIn(0).padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(20.dp),
                        ) {
                            Text(status.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = status.color())
                            Text(tr(R.string.s_0f_of_budget_used).format(pct), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                            Spacer(Modifier.height(14.dp))
                            Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(scheme.outlineVariant.copy(alpha = 0.5f))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(status.color()))
                            }
                        }
                        if (limit > 0.0) {
                            PaceCard(
                                debits = monthTxs.filter { it.type == "debit" && it.category == cat.category },
                                month = month,
                                budget = limit,
                                vis = amountVisibility,
                                modifier = Modifier.cascadeIn(1),
                            )
                        }
                        SettingsGroup(Modifier.cascadeIn(1)) {
                            AmountRow(tr(R.string.spent), cat.spent, amountVisibility, color = expenseColor())
                            SettingsDivider()
                            AmountRow(tr(R.string.limit), limit, amountVisibility)
                            SettingsDivider()
                            AmountRow(
                                if (left >= 0) tr(R.string.remaining) else tr(R.string.over_by),
                                kotlin.math.abs(left),
                                amountVisibility,
                                color = if (left >= 0) incomeColor() else expenseColor(),
                            )
                        }
                        SettingsGroup(Modifier.cascadeIn(2)) {
                            SettingsInfoRow(tr(R.string.transactions), cat.transactionCount.toString())
                            SettingsDivider()
                            val isCurrentMonth = YearMonth.from(month) == YearMonth.now()
                            if (isCurrentMonth) {
                                val daysLeft = (month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1).coerceAtLeast(1)
                                AmountRow(
                                    tr(R.string.safe_to_spend_per_day),
                                    if (left > 0) left / daysLeft else 0.0,
                                    amountVisibility,
                                    hint = tr(R.string.for_the_remaining_1_day_s, daysLeft),
                                )
                            } else {
                                SettingsInfoRow(tr(R.string.period), tr(R.string.month_ended))
                            }
                        }
                        Spacer(Modifier.height(110.dp))
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CreateBudgetDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit,
) {
    var category by remember { mutableStateOf("Groceries") }
    var limit by remember { mutableStateOf("") }
    val categories = listOf("Groceries", "Shopping", "Dining Out", "Transport", "Bills", "Rent", "Entertainment", "Fitness", "Café", "Other")
    val amount = limit.toDoubleOrNull() ?: 0.0

    AppDialog(
        onDismiss = onDismiss,
        title = tr(R.string.set_a_category_budget),
        message = tr(R.string.choose_a_category_and_the_most),
        icon = Icons.Default.Wallet,
        primary = DialogAction(tr(R.string.save_budget), { onConfirm(category, amount) }, enabled = amount > 0.0),
        secondary = DialogAction(tr(R.string.cancel), onDismiss),
    ) {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { cat -> AppChip(categoryLabel(cat), selected = category == cat, onClick = { category = cat }) }
        }
        Spacer(Modifier.height(16.dp))
        AppTextField(
            value = limit,
            onValueChange = { limit = it.filter { c -> c.isDigit() || c == '.' } },
            label = tr(R.string.monthly_limit),
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        )
    }
}
