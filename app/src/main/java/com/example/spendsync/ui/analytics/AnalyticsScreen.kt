package com.example.spendsync.ui.analytics

import com.example.spendsync.ui.i18n.LanguageManager
import com.example.spendsync.ui.i18n.categoryLabel
import androidx.annotation.StringRes
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.spendsync.ui.components.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.charts.BarGroup
import com.example.spendsync.ui.charts.BarSeries
import com.example.spendsync.ui.charts.ChartPoint
import com.example.spendsync.ui.charts.DonutSlice
import com.example.spendsync.ui.charts.InteractiveBarChart
import com.example.spendsync.ui.charts.InteractiveDonut
import com.example.spendsync.ui.charts.InteractiveLineChart
import com.example.spendsync.ui.charts.RankedBars
import com.example.spendsync.ui.charts.RankedItem
import com.example.spendsync.ui.charts.SpendHeatmap
import com.example.spendsync.ui.components.AppChip
import com.example.spendsync.ui.components.Skeleton
import com.example.spendsync.ui.home.AmountRow
import com.example.spendsync.ui.home.HeroCard
import com.example.spendsync.ui.home.HeroChip
import com.example.spendsync.ui.home.HomeSectionHeader
import com.example.spendsync.ui.home.HomeTopBar
import com.example.spendsync.ui.home.PreviewCard
import com.example.spendsync.ui.home.animatedAmount
import com.example.spendsync.ui.home.animatedFraction
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.home.introIn
import com.example.spendsync.ui.search.GlobalSearchDialog
import com.example.spendsync.ui.settings.SettingsBackdrop
import com.example.spendsync.ui.settings.SettingsContentWidth
import com.example.spendsync.ui.settings.SettingsDivider
import com.example.spendsync.ui.settings.SettingsGroup
import com.example.spendsync.ui.settings.SettingsTopBar
import com.example.spendsync.ui.settings.cascadeIn
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.DateFilterState
import com.example.spendsync.ui.shared.MaskableAmountText
import com.example.spendsync.ui.theme.chartCategoricalColors
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
import com.example.spendsync.utils.LocalizationUtils
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class DateRange {
    THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR, CUSTOM
}

/** Nested pages reachable from Analytics' preview cards. */
private enum class AnalyticsPage(@StringRes val titleRes: Int) {
    Trend(R.string.spending_trend),
    CashFlow(R.string.income_vs_expenses),
    Categories(R.string.where_your_money_goes),
    Patterns(R.string.spending_patterns),
    Merchants(R.string.top_merchants_spends),
    Insights(R.string.insights);

    val title: String get() = tr(titleRes)
}

private fun List<TransactionDto>.sumType(type: String): Double =
    filter { it.type == type }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

private fun TransactionDto.day(): LocalDate? = try {
    java.time.ZonedDateTime.parse(createdAt).toLocalDate()
} catch (e: Exception) {
    null
}

private fun TransactionDto.value() = amount.toDoubleOrNull() ?: 0.0

private data class FlowBucket(val label: String, val income: Double, val expense: Double)

/** Everything the charts need, derived once per (transactions, range). */
private class AnalyticsData(
    val income: Double,
    val expenses: Double,
    val savingsRate: Double,
    val daysCount: Long,
    val trend: List<ChartPoint>,
    val flow: List<FlowBucket>,
    val expenseItems: List<RankedItem>,
    val incomeItems: List<RankedItem>,
    val weekday: List<Double>,
    val daily: Map<LocalDate, Double>,
    val merchants: List<RankedItem>,
    val largest: List<TransactionDto>,
) {
    val net get() = income - expenses
}

/** Day buckets up to 45 days, month buckets beyond. */
private fun buildTrend(debits: List<TransactionDto>, start: LocalDate, end: LocalDate): List<ChartPoint> {
    val totalDays = ChronoUnit.DAYS.between(start, end) + 1
    return if (totalDays <= 45) {
        val byDay = debits.groupBy { it.day() }
        val f = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        (0 until totalDays).map { off ->
            val d = start.plusDays(off)
            ChartPoint(d.format(f), byDay[d]?.sumOf { it.value() } ?: 0.0)
        }
    } else {
        val byMonth = debits.groupBy { it.day()?.withDayOfMonth(1) }
        val f = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        val out = mutableListOf<ChartPoint>()
        var cur = start.withDayOfMonth(1)
        val last = end.withDayOfMonth(1)
        while (!cur.isAfter(last)) {
            out.add(ChartPoint(cur.format(f), byMonth[cur]?.sumOf { it.value() } ?: 0.0))
            cur = cur.plusMonths(1)
        }
        out
    }
}

/** Daily (≤14 days), weekly (≤92) or monthly buckets of income and expenses. */
private fun buildFlow(txs: List<TransactionDto>, start: LocalDate, end: LocalDate): List<FlowBucket> {
    val totalDays = ChronoUnit.DAYS.between(start, end) + 1
    val dayF = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    return when {
        totalDays <= 14 -> (0 until totalDays).map { off ->
            val d = start.plusDays(off)
            val of = txs.filter { it.day() == d }
            FlowBucket(d.format(dayF), of.sumType("credit"), of.sumType("debit"))
        }
        totalDays <= 92 -> {
            val weeks = ((totalDays + 6) / 7).toInt()
            (0 until weeks).map { w ->
                val ws = start.plusDays(w * 7L)
                val we = ws.plusDays(6)
                val of = txs.filter { t -> t.day()?.let { !it.isBefore(ws) && !it.isAfter(we) } == true }
                FlowBucket(ws.format(dayF), of.sumType("credit"), of.sumType("debit"))
            }
        }
        else -> {
            val f = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
            val out = mutableListOf<FlowBucket>()
            var cur = start.withDayOfMonth(1)
            val last = end.withDayOfMonth(1)
            while (!cur.isAfter(last)) {
                val of = txs.filter { it.day()?.withDayOfMonth(1) == cur }
                out.add(FlowBucket(cur.format(f), of.sumType("credit"), of.sumType("debit")))
                cur = cur.plusMonths(1)
            }
            out
        }
    }
}

private fun rank(txs: List<TransactionDto>, key: (TransactionDto) -> String, palette: List<Color>, neutral: Color, keep: Int, display: (String) -> String = { it }): List<RankedItem> {
    val grouped = txs.groupBy(key).map { (k, v) -> Triple(k, v.sumOf { it.value() }, v.size) }.sortedByDescending { it.second }
    val top = grouped.take(keep)
    val tail = grouped.drop(keep)
    val items = top.mapIndexed { i, (k, sum, count) -> RankedItem(display(k), sum, palette[i % palette.size], count) }
    return if (tail.isEmpty()) items else items + RankedItem(categoryLabel("Other"), tail.sumOf { it.second }, neutral, tail.sumOf { it.third })
}

private fun buildData(txs: List<TransactionDto>, start: LocalDate, end: LocalDate, palette: List<Color>, neutral: Color): AnalyticsData {
    val debits = txs.filter { it.type == "debit" }
    val credits = txs.filter { it.type == "credit" }
    val income = credits.sumOf { it.value() }
    val expenses = debits.sumOf { it.value() }
    val weekday = DoubleArray(7)
    debits.forEach { t -> t.day()?.let { weekday[it.dayOfWeek.value - 1] += t.value() } }
    return AnalyticsData(
        income = income,
        expenses = expenses,
        savingsRate = if (income > 0.0) ((income - expenses) / income * 100).coerceIn(0.0..100.0) else 0.0,
        daysCount = maxOf(ChronoUnit.DAYS.between(start, end) + 1, 1L),
        trend = buildTrend(debits, start, end),
        flow = buildFlow(txs, start, end),
        expenseItems = rank(debits, { it.category }, palette, neutral, 7, ::categoryLabel),
        incomeItems = rank(credits, { it.category }, palette, neutral, 7, ::categoryLabel),
        weekday = weekday.toList(),
        daily = debits.groupBy { it.day() }.filterKeys { it != null }.mapKeys { it.key!! }.mapValues { e -> e.value.sumOf { it.value() } },
        merchants = rank(debits.filter { it.merchant.isNotBlank() }, { it.merchant }, palette, neutral, 8).filter { it.label != categoryLabel("Other") },
        largest = debits.sortedByDescending { it.value() }.take(5),
    )
}

/** Realistic-looking numbers; only ever shown as skeleton bones, never as real data. */
private fun placeholderData(palette: List<Color>, neutral: Color): AnalyticsData {
    fun item(i: Int) = RankedItem("Category name", 10000.0 - i * 1500, palette[i % palette.size], 5)
    return AnalyticsData(
        income = 50000.0, expenses = 32000.0, savingsRate = 36.0, daysCount = 30,
        trend = List(12) { ChartPoint("Day", it * 100.0 + 100) },
        flow = List(6) { FlowBucket("Week", 8000.0, 5000.0) },
        expenseItems = List(4) { item(it) }, incomeItems = List(3) { item(it) },
        weekday = List(7) { 1000.0 + it * 100 }, daily = emptyMap(),
        merchants = List(3) { item(it) }, largest = emptyList(),
    )
}

// ── Analytics ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    sessionDataStore: SessionDataStore,
    financeRepository: FinanceRepository,
    dateFilterState: DateFilterState,
    amountVisibility: AmountVisibilityState,
    onOpenSettings: () -> Unit = {},
    onNavigate: (com.example.spendsync.ui.search.SearchDest) -> Unit = {},
    onViewTransaction: (TransactionDto) -> Unit = {},
    onOpenAssistant: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    var showGlobalSearch by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<AnalyticsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }

    var intro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { intro = true }

    // Honours Settings → Date format.
    val dateFormat by sessionDataStore.dateFormat.collectAsState(initial = "DD / MM / YYYY")
    val datePattern = remember(dateFormat) { LocalizationUtils.getDateFormatPattern(dateFormat) }

    val today = remember { LocalDate.now() }
    var selectedRange by remember { mutableStateOf(DateRange.THIS_MONTH) }

    val bounds = remember(selectedRange, dateFilterState.selectedDate) {
        when (selectedRange) {
            DateRange.THIS_WEEK -> Pair(today.minusDays((today.dayOfWeek.value - 1).toLong()), today)
            DateRange.LAST_WEEK -> {
                val end = today.minusDays(today.dayOfWeek.value.toLong())
                Pair(end.minusDays(6), end)
            }
            DateRange.THIS_MONTH -> Pair(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
            DateRange.LAST_MONTH -> {
                val last = today.minusMonths(1)
                Pair(last.withDayOfMonth(1), last.withDayOfMonth(last.lengthOfMonth()))
            }
            DateRange.THIS_YEAR -> Pair(today.withDayOfYear(1), today.withMonth(12).withDayOfMonth(31))
            DateRange.LAST_YEAR -> {
                val last = today.minusYears(1)
                Pair(last.withDayOfYear(1), last.withMonth(12).withDayOfMonth(31))
            }
            DateRange.CUSTOM -> Pair(dateFilterState.selectedDate, dateFilterState.selectedDate)
        }
    }
    val (startDate, endDate) = bounds

    var apiTransactions by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load(forceRefresh: Boolean) {
        val startStr = startDate.toString() + "T00:00:00.000Z"
        val endStr = endDate.toString() + "T23:59:59.999Z"
        when (val res = financeRepository.getTransactions(startDate = startStr, endDate = endStr, limit = 500, forceRefresh = forceRefresh)) {
            is AuthResult.Success -> apiTransactions = res.data
            is AuthResult.Error -> Unit
        }
    }

    LaunchedEffect(bounds) {
        isLoading = true
        load(forceRefresh = false)
        isLoading = false
    }

    // Fixed, CVD-validated categorical palette; categories past the 7th fold into a neutral "Other".
    val palette = chartCategoricalColors()
    val neutral = scheme.onSurfaceVariant
    val language = com.example.spendsync.ui.i18n.LanguageManager.current
    val realData = remember(apiTransactions, startDate, endDate, palette, neutral, language) {
        buildData(apiTransactions, startDate, endDate, palette, neutral)
    }
    // While loading, the REAL layout is drawn with placeholder numbers and turned into bones.
    val data = if (isLoading) remember(palette, neutral) { placeholderData(palette, neutral) } else realData
    val rangeFmt = DateTimeFormatter.ofPattern(datePattern, Locale.getDefault())

    AnimatedContent(
        targetState = page,
        transitionSpec = com.example.spendsync.ui.theme.motionSpec(com.example.spendsync.ui.theme.LocalMotion.current.enabled(com.example.spendsync.ui.theme.MotionKind.Transitions)) {
            val forward = targetState != null
            val enter = slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(320))
            val exit = slideOutHorizontally(tween(320)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200))
            enter togetherWith exit
        },
        label = "analytics_nav",
    ) { current ->
        if (current != null) {
            SettingsBackdrop {
                Column(Modifier.fillMaxSize()) {
                    SettingsTopBar(current.title, onBack = { page = null })
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SettingsContentWidth {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                when (current) {
                                    AnalyticsPage.Trend -> TrendPage(data, amountVisibility)
                                    AnalyticsPage.CashFlow -> CashFlowPage(data, amountVisibility)
                                    AnalyticsPage.Categories -> CategoriesPage(data, amountVisibility)
                                    AnalyticsPage.Patterns -> PatternsPage(data, startDate, endDate, amountVisibility)
                                    AnalyticsPage.Merchants -> MerchantsPage(data, datePattern, amountVisibility, onViewTransaction)
                                    AnalyticsPage.Insights -> InsightsPage(data, amountVisibility)
                                }
                                Spacer(Modifier.height(110.dp))
                            }
                        }
                    }
                }
            }
        } else {
            SettingsBackdrop {
                com.example.spendsync.ui.components.AppPullToRefresh(
                    onLongPull = { showGlobalSearch = true },
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            load(forceRefresh = true)
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
                                        title = tr(R.string.analytics),
                                        icon = Icons.Default.BarChart,
                                        selectedDate = dateFilterState.selectedDate,
                                        datePattern = datePattern,
                                        onCalendarClick = {
                                            selectedRange = DateRange.CUSTOM
                                            dateFilterState.showMonthPicker = true
                                        },
                                        onSearchClick = { showGlobalSearch = true },
                                        onAssistantClick = onOpenAssistant,
                                    )
                                }

                                RangePills(selectedRange) { range ->
                                    selectedRange = range
                                    if (range == DateRange.CUSTOM) dateFilterState.showMonthPicker = true
                                }
                                Text(
                                    "${startDate.format(rangeFmt)} – ${endDate.format(rangeFmt)}",
                                    color = scheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                )

                                Skeleton(loading = isLoading) {
                                  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    AnalyticsHero(data, amountVisibility, Modifier.introIn(intro, 0))

                                    HomeSectionHeader(tr(R.string.money_flow))
                                    PreviewCard(tr(R.string.spending_trend), tr(R.string.tap_or_drag_to_read_any), { page = AnalyticsPage.Trend }, Modifier.introIn(intro, 1)) {
                                        TrendChart(data, amountVisibility, plotHeight = 110, cumulative = false)
                                    }
                                    PreviewCard(tr(R.string.income_vs_expenses), tr(R.string.what_came_in_vs_what_went), { page = AnalyticsPage.CashFlow }, Modifier.introIn(intro, 2)) {
                                        FlowChart(data, amountVisibility, plotHeight = 120)
                                    }

                                    HomeSectionHeader(tr(R.string.where_it_goes))
                                    PreviewCard(
                                        tr(R.string.where_your_money_goes),
                                        if (data.expenseItems.size > 4) tr(R.string.top_4_of_1_tap_a, data.expenseItems.size) else tr(R.string.tap_a_row_for_details),
                                        { page = AnalyticsPage.Categories },
                                        Modifier.introIn(intro, 3),
                                    ) {
                                        RankedBars(data.expenseItems.take(4), data.expenses, amountVisibility, Icons.Default.Wallet, tr(R.string.no_spending_in_this_period_yet))
                                    }
                                    PreviewCard(tr(R.string.top_merchants), tr(R.string.where_you_spend_the_most), { page = AnalyticsPage.Merchants }, Modifier.introIn(intro, 4)) {
                                        RankedBars(data.merchants.take(3), data.expenses, amountVisibility, Icons.Default.Wallet, tr(R.string.add_a_merchant_to_transactions_to))
                                    }

                                    HomeSectionHeader(tr(R.string.habits))
                                    PreviewCard(tr(R.string.spending_patterns), tr(R.string.which_days_you_spend_most), { page = AnalyticsPage.Patterns }, Modifier.introIn(intro, 5)) {
                                        WeekdayChart(data, amountVisibility, plotHeight = 110)
                                    }
                                    PreviewCard(tr(R.string.insights), tr(R.string.savings_rate_and_daily_average), { page = AnalyticsPage.Insights }, Modifier.introIn(intro, 6)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            InsightRow(Icons.Default.Savings, tr(R.string.savings_rate), incomeColor()) {
                                                Text("%,.1f%%".format(data.savingsRate), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                                            }
                                            InsightRow(Icons.Default.Wallet, tr(R.string.average_daily_spend), scheme.primary) {
                                                MaskableAmountText(data.expenses / data.daysCount, amountVisibility, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                                            }
                                        }
                                    }
                                  }
                                }
                                Spacer(Modifier.height(110.dp))
                            }
                        }
                    }
                }
            }
        }
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
            onNavigate = { dest ->
                showGlobalSearch = false
                onNavigate(dest)
            },
        )
    }
}

// ── Chart wrappers (shared by preview cards and pages) ───────────────────────

@Composable
private fun TrendChart(data: AnalyticsData, vis: AmountVisibilityState, plotHeight: Int, cumulative: Boolean) {
    val points = remember(data, cumulative) {
        if (!cumulative) data.trend
        else {
            var run = 0.0
            data.trend.map { run += it.value; ChartPoint(it.label, run) }
        }
    }
    InteractiveLineChart(
        points = points,
        color = MaterialTheme.colorScheme.primary,
        vis = vis,
        emptyIcon = Icons.Default.BarChart,
        emptyText = tr(R.string.no_spending_recorded_in_this_period),
        plotHeight = plotHeight.dp,
        valueLabel = if (cumulative) tr(R.string.total_so_far) else tr(R.string.spent),
    )
}

@Composable
private fun FlowChart(data: AnalyticsData, vis: AmountVisibilityState, plotHeight: Int) {
    InteractiveBarChart(
        groups = data.flow.map { BarGroup(it.label, listOf(it.income, it.expense)) },
        series = listOf(BarSeries(tr(R.string.income), incomeColor()), BarSeries(tr(R.string.expenses), expenseColor())),
        vis = vis,
        emptyIcon = Icons.Default.BarChart,
        emptyText = tr(R.string.no_income_or_expenses_in_this),
        plotHeight = plotHeight.dp,
    )
}

@Composable
private fun WeekdayChart(data: AnalyticsData, vis: AmountVisibilityState, plotHeight: Int) {
    val names = remember(LanguageManager.current) { DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) } }
    InteractiveBarChart(
        groups = data.weekday.mapIndexed { i, v -> BarGroup(names[i], listOf(v)) },
        series = listOf(BarSeries(tr(R.string.spent), MaterialTheme.colorScheme.primary)),
        vis = vis,
        emptyIcon = Icons.Default.BarChart,
        emptyText = tr(R.string.no_spending_in_this_period_yet),
        plotHeight = plotHeight.dp,
    )
}

// ── Top-level pieces ─────────────────────────────────────────────────────────

@Composable
private fun RangePills(selected: DateRange, onSelect: (DateRange) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ranges = listOf(
        DateRange.THIS_WEEK to tr(R.string.this_week),
        DateRange.LAST_WEEK to tr(R.string.last_week),
        DateRange.THIS_MONTH to tr(R.string.this_month),
        DateRange.LAST_MONTH to tr(R.string.last_month),
        DateRange.THIS_YEAR to tr(R.string.this_year),
        DateRange.LAST_YEAR to tr(R.string.last_year),
        DateRange.CUSTOM to tr(R.string.custom),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(ranges) { (range, label) ->
            AppChip(label, selected = selected == range, onClick = { onSelect(range) }, role = Role.Tab)
        }
    }
}

/** Small pill group for switching a chart's view (Daily / Cumulative, Spending / Income). */
@Composable
private fun ChoiceChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, opt -> AppChip(opt, selected = i == selectedIndex, onClick = { onSelect(i) }) }
    }
}

@Composable
private fun AnalyticsHero(data: AnalyticsData, vis: AmountVisibilityState, modifier: Modifier = Modifier) {
    val onHero = MaterialTheme.colorScheme.onPrimary
    val shownNet = animatedAmount(kotlin.math.abs(data.net))
    val shownIncome = animatedAmount(data.income)
    val shownExpenses = animatedAmount(data.expenses)
    HeroCard(modifier) {
        Text(tr(R.string.net_flow), color = onHero.copy(alpha = 0.8f), fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        MaskableAmountText(
            amount = shownNet,
            visibility = vis,
            prefix = if (data.net >= 0) "+" else "-",
            color = onHero,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroChip(tr(R.string.income), shownIncome, vis, Modifier.weight(1f))
            HeroChip(tr(R.string.expenses), shownExpenses, vis, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        val rate = animatedFraction((data.savingsRate / 100.0).toFloat())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(onHero.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(rate).clip(CircleShape).background(onHero))
            }
            Spacer(Modifier.width(12.dp))
            Text(tr(R.string.s_0f_saved).format(data.savingsRate), color = onHero, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InsightRow(icon: ImageVector, title: String, tint: Color, value: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 12.sp, color = scheme.onSurfaceVariant)
            value()
        }
    }
}

@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(20.dp)) { content() }
}

// ── Nested pages ─────────────────────────────────────────────────────────────

@Composable
private fun TrendPage(data: AnalyticsData, vis: AmountVisibilityState) {
    var cumulative by remember { mutableStateOf(false) }
    val peak = data.trend.maxByOrNull { it.value }
    Box(Modifier.cascadeIn(0).padding(horizontal = 16.dp)) { ChoiceChips(listOf(tr(R.string.daily), tr(R.string.cumulative)), if (cumulative) 1 else 0) { cumulative = it == 1 } }
    CardBox(Modifier.cascadeIn(1)) { TrendChart(data, vis, plotHeight = 220, cumulative = cumulative) }
    SettingsGroup(Modifier.cascadeIn(2)) {
        AmountRow(tr(R.string.total_spent), data.expenses, vis)
        SettingsDivider()
        AmountRow(tr(R.string.average_per_day), data.expenses / data.daysCount, vis)
        if (peak != null && peak.value > 0.0) {
            SettingsDivider()
            AmountRow(tr(R.string.highest_spend), peak.value, vis, color = expenseColor(), hint = peak.label)
        }
    }
}

@Composable
private fun CashFlowPage(data: AnalyticsData, vis: AmountVisibilityState) {
    val best = data.flow.maxByOrNull { it.income - it.expense }
    CardBox(Modifier.cascadeIn(0)) { FlowChart(data, vis, plotHeight = 220) }
    SettingsGroup(Modifier.cascadeIn(1)) {
        AmountRow(tr(R.string.income), data.income, vis, color = incomeColor())
        SettingsDivider()
        AmountRow(tr(R.string.expenses), data.expenses, vis, color = expenseColor())
        SettingsDivider()
        AmountRow(tr(R.string.net), kotlin.math.abs(data.net), vis, color = if (data.net >= 0) incomeColor() else expenseColor(), hint = if (data.net >= 0) tr(R.string.you_came_out_ahead) else tr(R.string.you_spent_more_than_you_earned))
        if (best != null && (best.income - best.expense) > 0.0) {
            SettingsDivider()
            AmountRow(tr(R.string.best_period), best.income - best.expense, vis, color = incomeColor(), hint = tr(R.string.starting_1, best.label))
        }
    }
}

@Composable
private fun CategoriesPage(data: AnalyticsData, vis: AmountVisibilityState) {
    var spending by remember { mutableStateOf(true) }
    val items = if (spending) data.expenseItems else data.incomeItems
    val total = if (spending) data.expenses else data.income
    Box(Modifier.cascadeIn(0).padding(horizontal = 16.dp)) { ChoiceChips(listOf(tr(R.string.spending), tr(R.string.income)), if (spending) 0 else 1) { spending = it == 0 } }
    CardBox(Modifier.cascadeIn(1)) {
        InteractiveDonut(
            slices = items.map { DonutSlice(it.label, it.value, it.color) },
            vis = vis,
            totalLabel = if (spending) tr(R.string.total_spent) else tr(R.string.total_income),
        )
    }
    CardBox(Modifier.cascadeIn(2)) {
        RankedBars(items, total, vis, Icons.Default.Category, tr(R.string.nothing_here_for_this_period_yet))
    }
}

@Composable
private fun PatternsPage(data: AnalyticsData, start: LocalDate, end: LocalDate, vis: AmountVisibilityState) {
    val names = remember(LanguageManager.current) { DayOfWeek.entries.map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) } }
    val busiest = data.weekday.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0.0 }
    CardBox(Modifier.cascadeIn(0)) {
        Text(tr(R.string.by_day_of_the_week), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        WeekdayChart(data, vis, plotHeight = 180)
        if (busiest != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                tr(R.string.you_spend_the_most_on_1, names[busiest.index]),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (ChronoUnit.DAYS.between(start, end) <= 92) {
        CardBox(Modifier.cascadeIn(1)) {
            Text(tr(R.string.day_by_day), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            SpendHeatmap(data.daily, start, end, expenseColor(), vis)
        }
    }
}

@Composable
private fun MerchantsPage(
    data: AnalyticsData,
    datePattern: String,
    vis: AmountVisibilityState,
    onViewTransaction: (TransactionDto) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fmt = remember(datePattern) { DateTimeFormatter.ofPattern(datePattern, Locale.getDefault()) }
    CardBox(Modifier.cascadeIn(0)) {
        Text(tr(R.string.top_merchants), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = scheme.onSurface)
        Spacer(Modifier.height(8.dp))
        RankedBars(data.merchants, data.expenses, vis, Icons.Default.Wallet, tr(R.string.add_a_merchant_to_transactions_to))
    }
    HomeSectionHeader(tr(R.string.largest_transactions))
    SettingsGroup(Modifier.cascadeIn(1)) {
        if (data.largest.isEmpty()) {
            Text(tr(R.string.no_spending_in_this_period_yet), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
        data.largest.forEachIndexed { i, tx ->
            if (i > 0) SettingsDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .clickable(role = Role.Button) { onViewTransaction(tx) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(categoryLabel(tx.category), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, maxLines = 1)
                    Text(
                        listOfNotNull(tx.merchant.takeIf { it.isNotBlank() }, tx.day()?.format(fmt)).joinToString(" · "),
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                MaskableAmountText(tx.value(), vis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = expenseColor())
            }
        }
    }
}

@Composable
private fun InsightsPage(data: AnalyticsData, vis: AmountVisibilityState) {
    val scheme = MaterialTheme.colorScheme
    val top = data.expenseItems.firstOrNull { it.label != categoryLabel("Other") }
    CardBox(Modifier.cascadeIn(0)) {
        Text(tr(R.string.income_vs_expenses), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = scheme.onSurface)
        Spacer(Modifier.height(4.dp))
        InteractiveBarChart(
            groups = listOf(BarGroup(tr(R.string.this_period), listOf(data.income, data.expenses))),
            series = listOf(BarSeries(tr(R.string.income), incomeColor()), BarSeries(tr(R.string.expenses), expenseColor())),
            vis = vis,
            emptyIcon = Icons.Default.BarChart,
            emptyText = tr(R.string.no_income_or_expenses_in_this),
            plotHeight = 160.dp,
        )
    }
    Column(
        Modifier.cascadeIn(1).padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InsightRow(Icons.Default.Savings, tr(R.string.savings_rate), incomeColor()) {
            Text("%,.1f%%".format(data.savingsRate), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            Text(
                if (data.savingsRate >= 20.0) tr(R.string.excellent_you_re_building_a_safety) else tr(R.string.aim_to_save_at_least_20),
                fontSize = 12.sp, color = scheme.onSurfaceVariant,
            )
        }
        InsightRow(Icons.Default.Wallet, tr(R.string.average_daily_spend), scheme.primary) {
            MaskableAmountText(data.expenses / data.daysCount, vis, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            Text(tr(R.string.over_1_day_s, data.daysCount), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        if (top != null) {
            InsightRow(Icons.Default.Category, tr(R.string.biggest_category), expenseColor()) {
                Text(top.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                MaskableAmountText(top.value, vis, fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
        }
    }
}
