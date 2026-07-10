package com.example.spendsync.ui.placeholder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.remote.model.*
import com.example.spendsync.utils.LocalizationUtils
import com.example.spendsync.ui.components.SkeletonBox
import com.example.spendsync.ui.components.SkeletonLine
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.search.GlobalSearchDialog
import com.example.spendsync.ui.shared.TopBarDateSearchGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.ui.home.MockTransaction
import com.example.spendsync.ui.home.TransactionType
import com.example.spendsync.ui.shared.CalendarHeader
import com.example.spendsync.ui.shared.DateFilterState
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.chartCategoricalColors
import com.example.spendsync.ui.theme.NeutralBlack
import com.example.spendsync.ui.theme.NeutralLight
import com.example.spendsync.ui.theme.NeutralMid
import com.example.spendsync.ui.theme.NeutralOffWhite
import com.example.spendsync.ui.theme.NeutralWhite
import com.example.spendsync.ui.theme.SemanticError
import com.example.spendsync.ui.theme.SemanticSuccess
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Date range enum for the selector pills
enum class DateRange {
    THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR, CUSTOM
}

private data class TrendPoint(val label: String, val spent: Double)

/**
 * Buckets debit transactions by day (ranges up to ~45 days) or by month
 * (longer ranges) so the trend chart always has a readable number of points
 * regardless of which date-range pill is selected.
 */
private fun buildTrendPoints(
    transactions: List<TransactionDto>,
    startDate: LocalDate,
    endDate: LocalDate,
): List<TrendPoint> {
    val totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1
    val debits = transactions.filter { it.type == "debit" }

    return if (totalDays <= 45) {
        val byDay = debits.groupBy {
            try {
                java.time.ZonedDateTime.parse(it.createdAt).toLocalDate()
            } catch (e: Exception) {
                null
            }
        }
        val dayFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        (0 until totalDays).map { offset ->
            val day = startDate.plusDays(offset)
            val spent = byDay[day]?.sumOf { it.amount.toDoubleOrNull() ?: 0.0 } ?: 0.0
            TrendPoint(day.format(dayFormatter), spent)
        }
    } else {
        val byMonth = debits.groupBy {
            try {
                java.time.ZonedDateTime.parse(it.createdAt).toLocalDate().withDayOfMonth(1)
            } catch (e: Exception) {
                null
            }
        }
        val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        val points = mutableListOf<TrendPoint>()
        var cursor = startDate.withDayOfMonth(1)
        val end = endDate.withDayOfMonth(1)
        while (!cursor.isAfter(end)) {
            val spent = byMonth[cursor]?.sumOf { it.amount.toDoubleOrNull() ?: 0.0 } ?: 0.0
            points.add(TrendPoint(cursor.format(monthFormatter), spent))
            cursor = cursor.plusMonths(1)
        }
        points
    }
}

// ── Analytics ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    sessionDataStore: SessionDataStore,
    financeRepository: FinanceRepository,
    dateFilterState: DateFilterState,
    onOpenSettings: () -> Unit = {},
    onViewTransaction: (TransactionDto) -> Unit = {},
) {
    var showGlobalSearch by remember { mutableStateOf(false) }
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    // Reads the user's chosen accent (Settings → Accent Color) like every
    // other tab does, instead of a hardcoded palette.
    val BrandBlue = MaterialTheme.colorScheme.primary

    val language by sessionDataStore.language.collectAsState(initial = "English")
    val currencyCode by sessionDataStore.currency.collectAsState(initial = "USD")
    val currencySymbol = remember(currencyCode) {
        when (currencyCode) {
            "EUR" -> "€"
            "GBP" -> "£"
            "INR" -> "₹"
            "JPY" -> "¥"
            else  -> "$"
        }
    }
    val today = remember { LocalDate.now() }

    var selectedRange by remember { mutableStateOf(DateRange.THIS_MONTH) }

    // Calculate dates bounds for the selected range pill
    val bounds = remember(selectedRange, dateFilterState.selectedDate) {
        when (selectedRange) {
            DateRange.THIS_WEEK -> {
                val dayOfWeek = today.dayOfWeek.value
                val start = today.minusDays((dayOfWeek - 1).toLong())
                Pair(start, today)
            }
            DateRange.LAST_WEEK -> {
                val dayOfWeek = today.dayOfWeek.value
                val end = today.minusDays(dayOfWeek.toLong())
                val start = end.minusDays(6)
                Pair(start, end)
            }
            DateRange.THIS_MONTH -> {
                val start = today.withDayOfMonth(1)
                val end = today.withDayOfMonth(today.lengthOfMonth())
                Pair(start, end)
            }
            DateRange.LAST_MONTH -> {
                val lastMonth = today.minusMonths(1)
                val start = lastMonth.withDayOfMonth(1)
                val end = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth())
                Pair(start, end)
            }
            DateRange.THIS_YEAR -> {
                val start = today.withDayOfYear(1)
                val end = today.withMonth(12).withDayOfMonth(31)
                Pair(start, end)
            }
            DateRange.LAST_YEAR -> {
                val lastYear = today.minusYears(1)
                val start = lastYear.withDayOfYear(1)
                val end = lastYear.withMonth(12).withDayOfMonth(31)
                Pair(start, end)
            }
            DateRange.CUSTOM -> {
                Pair(dateFilterState.selectedDate, dateFilterState.selectedDate)
            }
        }
    }

    val (startDate, endDate) = bounds

    var apiTransactions by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var isAnalyticsLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun loadAnalyticsData(forceRefresh: Boolean) {
        val startStr = startDate.toString() + "T00:00:00.000Z"
        val endStr = endDate.toString() + "T23:59:59.999Z"
        when (val res = financeRepository.getTransactions(startDate = startStr, endDate = endStr, limit = 500, forceRefresh = forceRefresh)) {
            is AuthResult.Success -> {
                apiTransactions = res.data
            }
            is AuthResult.Error -> {
                // handle error
            }
        }
    }

    LaunchedEffect(bounds) {
        isAnalyticsLoading = true
        loadAnalyticsData(forceRefresh = false)
        isAnalyticsLoading = false
    }

    // Dynamic stats computations
    val totalIncome = remember(apiTransactions) {
        apiTransactions.filter { it.type == "credit" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    }
    val totalExpenses = remember(apiTransactions) {
        apiTransactions.filter { it.type == "debit" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    }
    val totalBalance = totalIncome - totalExpenses

    val savingsRate = if (totalIncome > 0.0) {
        ((totalIncome - totalExpenses) / totalIncome * 100).coerceIn(0.0..100.0)
    } else 0.0

    // Group expenses by category, biggest first
    val expensesByCategory = remember(apiTransactions) {
        apiTransactions
            .filter { it.type == "debit" }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount.toDoubleOrNull() ?: 0.0 } }
            .toList()
            .sortedByDescending { it.second }
    }

    // Fixed, CVD-validated categorical order (separately validated per theme
    // surface) — never a generated hue per category name. Past 7 real
    // categories, the tail folds into "Other" in neutral gray so it doesn't
    // masquerade as its own identity.
    val categoricalColors = chartCategoricalColors()
    val chartCategoryData = remember(expensesByCategory, categoricalColors) {
        val top = expensesByCategory.take(7)
        val tailSum = expensesByCategory.drop(7).sumOf { it.second }
        val entries = if (tailSum > 0.0) top + ("Other" to tailSum) else top
        entries.mapIndexed { index, (category, amount) ->
            val color = if (category == "Other") NeutralMid else categoricalColors[index]
            Triple(category, amount, color)
        }
    }

    // Spending trend across the selected range — daily buckets for short
    // ranges, monthly buckets once it spans more than ~45 days.
    val trendPoints = remember(apiTransactions, startDate, endDate) {
        buildTrendPoints(apiTransactions, startDate, endDate)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralOffWhite),
    ) {
        // ── Top bar (matches Home/Budget's header) ────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeutralOffWhite) // Blends in with main screen
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Side: App Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Savings,
                    contentDescription = "App Logo",
                    tint = NeutralBlack,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SpendSync",
                    color = NeutralBlack,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }

            // Right Side: Calendar + global search, grouped together.
            TopBarDateSearchGroup(
                selectedDate = dateFilterState.selectedDate,
                onCalendarClick = {
                    selectedRange = DateRange.CUSTOM
                    dateFilterState.showMonthPicker = true
                },
                onSearchClick = { showGlobalSearch = true },
                contentColor = NeutralBlack,
                groupBackgroundColor = Color(0xFFF1F5F9),
            )
        }

        // ── Scrollable Body Content (pull-to-refresh) ─────────────────────────
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadAnalyticsData(forceRefresh = true)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Pill selector capsules ────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val ranges = listOf(
                    DateRange.THIS_WEEK to "This Week",
                    DateRange.LAST_WEEK to "Last Week",
                    DateRange.THIS_MONTH to "This Month",
                    DateRange.LAST_MONTH to "Last Month",
                    DateRange.THIS_YEAR to "This Year",
                    DateRange.LAST_YEAR to "Last Year",
                    DateRange.CUSTOM to "Custom"
                )
                items(ranges) { (range, label) ->
                    val isSelected = selectedRange == range

                    // Selected: brand accent fill; unselected: white with a hairline border.
                    val pillBg = if (isSelected) BrandBlue else NeutralWhite
                    val pillText = if (isSelected) NeutralWhite else NeutralBlack
                    val border = BorderStroke(1.dp, NeutralLight)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(pillBg)
                            .border(border, RoundedCornerShape(20.dp))
                            .clickable { 
                                selectedRange = range 
                                if (range == DateRange.CUSTOM) {
                                    dateFilterState.showMonthPicker = true
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = pillText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── Dynamic Range Label Display ───────────────────────────────────
            val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
            Text(
                text = "Showing data from ${startDate.format(dateFormatter)} to ${endDate.format(dateFormatter)}",
                color = NeutralMid,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(16.dp))

            if (isAnalyticsLoading) {
                AnalyticsSkeleton()
                Spacer(Modifier.height(100.dp))
                return@Column
            }

            // ── SECTION 1: Overview ──────────────────────────────────────────
            SectionHeader(title = "Financial Overview", subtitle = "Net balance and flow summary")
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryStatCard(
                    label = LocalizationUtils.getTranslation("income", language) + " Flow",
                    amount = "${currencySymbol}%,.2f".format(totalIncome),
                    color = SemanticSuccess,
                    modifier = Modifier.weight(1f)
                )
                SummaryStatCard(
                    label = LocalizationUtils.getTranslation("expenses", language) + " Flow",
                    amount = "${currencySymbol}%,.2f".format(totalExpenses),
                    color = SemanticError,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            // Net balance gets its own full-width line — it's the one number
            // that answers "am I ahead or behind," and shouldn't require the
            // reader to subtract the two cards above themselves.
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                border = BorderStroke(1.dp, NeutralLight),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Net Balance", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NeutralMid)
                    Text(
                        text = "${if (totalBalance >= 0) "+" else "-"}${currencySymbol}%,.2f".format(kotlin.math.abs(totalBalance)),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (totalBalance >= 0) SemanticSuccess else SemanticError,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── SECTION 2: Spending Trend ─────────────────────────────────────
            SectionHeader(title = "Spending Trend", subtitle = "How your spending moved across this period")
            Spacer(Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                border = BorderStroke(1.dp, NeutralLight),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SpendingTrendChart(
                        points = trendPoints,
                        currencySymbol = currencySymbol,
                        lineColor = BrandBlue,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── SECTION 3: Where Your Money Goes ──────────────────────────────
            SectionHeader(title = "Where Your Money Goes", subtitle = "Categories ranked by spend, biggest first")
            Spacer(Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                border = BorderStroke(1.dp, NeutralLight),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    CategoryBarChart(
                        items = chartCategoryData,
                        totalExpenseSum = totalExpenses,
                        currencySymbol = currencySymbol,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── SECTION 4: Income vs Expenses ─────────────────────────────────
            SectionHeader(title = "Income vs Expenses", subtitle = "Flow proportion comparison")
            Spacer(Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                border = BorderStroke(1.dp, NeutralLight),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    DoubleBarChart(
                        income = totalIncome.toFloat(),
                        expense = totalExpenses.toFloat()
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── SECTION 5: Financial Insights ─────────────────────────────────
            SectionHeader(title = "Financial Insights", subtitle = "Automated spending analysis")
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Savings Rate Insights
                InsightCard(
                    title = "Savings Rate",
                    value = "%,.1f%%".format(savingsRate),
                    desc = if (savingsRate >= 20.0) "Excellent! You are building safety funds." else "Aim to save at least 20% of your earnings.",
                    icon = Icons.Default.Savings,
                    accentColor = SemanticSuccess
                )

                // 2. Average Daily Spending
                val daysCount = remember(startDate, endDate) {
                    val count = ChronoUnit.DAYS.between(startDate, endDate) + 1
                    maxOf(count, 1L)
                }
                val avgDaily = totalExpenses / daysCount
                InsightCard(
                    title = "Average Daily Spend",
                    value = "${currencySymbol}%,.2f".format(avgDaily),
                    desc = "Calculated over a range of $daysCount day(s).",
                    icon = Icons.Default.Wallet,
                    accentColor = BrandBlue
                )
            }

            // Bottom padding for the navigation bar
            Spacer(Modifier.height(100.dp))
        }
        }
    }

    if (showGlobalSearch) {
        GlobalSearchDialog(
            financeRepository = financeRepository,
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

// ── Analytics loading skeleton — one shape per section, in place of the ──────
// real content while the transactions for the selected range are in flight.
@Composable
private fun AnalyticsSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonBox(modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(16.dp))
            SkeletonBox(modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(16.dp))
        }
        Spacer(Modifier.height(12.dp))
        SkeletonBox(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(48.dp),
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(24.dp))
        SkeletonLine(modifier = Modifier.padding(horizontal = 20.dp).width(140.dp))
        Spacer(Modifier.height(12.dp))
        SkeletonBox(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(200.dp),
            shape = RoundedCornerShape(20.dp),
        )
        Spacer(Modifier.height(24.dp))
        SkeletonLine(modifier = Modifier.padding(horizontal = 20.dp).width(160.dp))
        Spacer(Modifier.height(12.dp))
        SkeletonBox(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(220.dp),
            shape = RoundedCornerShape(20.dp),
        )
    }
}

// ── Section Header Helper Component ──────────────────────────────────────────
@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val BrandBlue = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BrandBlue)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = NeutralBlack
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = NeutralMid,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

// ── Helper Stats Card Component ──────────────────────────────────────────────
@Composable
private fun SummaryStatCard(
    label: String,
    amount: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeutralWhite),
        border = BorderStroke(1.dp, NeutralLight),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = label, fontSize = 12.sp, color = NeutralMid)
            Spacer(Modifier.height(6.dp))
            Text(
                text = amount,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// ── Spending Trend — single-series line + area, the headline "where is my ──
// money going over time" view. One hue (sequential job), gridlines, a direct
// peak label instead of a legend (a single series needs none).
@Composable
private fun SpendingTrendChart(
    points: List<TrendPoint>,
    currencySymbol: String,
    lineColor: Color,
) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    if (points.isEmpty() || points.sumOf { it.spent } <= 0.0) {
        Box(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("No spending recorded in this period yet.", fontSize = 13.sp, color = NeutralMid)
        }
        return
    }

    val maxSpent = points.maxOf { it.spent }.let { if (it <= 0.0) 1.0 else it }
    val peakIndex = points.indices.maxByOrNull { points[it].spent } ?: 0

    Column {
        Text(
            text = "Peak: ${currencySymbol}%,.2f".format(points[peakIndex].spent) + " · ${points[peakIndex].label}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = NeutralBlack,
        )
        Spacer(Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val chartHeight = size.height
            val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f

            // Recessive gridlines
            val gridSteps = 3
            for (i in 0..gridSteps) {
                val y = chartHeight - (chartHeight * i / gridSteps)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val linePath = Path()
            val fillPath = Path()
            points.forEachIndexed { index, point ->
                val x = index * stepX
                val y = chartHeight - (point.spent / maxSpent).toFloat() * chartHeight
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, chartHeight)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (index == points.lastIndex) {
                    fillPath.lineTo(x, chartHeight)
                    fillPath.close()
                }
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f))
                ),
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )

            // Direct-label the peak instead of a legend box.
            val peakX = peakIndex * stepX
            val peakY = chartHeight - (points[peakIndex].spent / maxSpent).toFloat() * chartHeight
            drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(peakX, peakY))
            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(peakX, peakY))
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(points.first().label, fontSize = 10.sp, color = NeutralMid)
            if (points.size > 1) Text(points.last().label, fontSize = 10.sp, color = NeutralMid)
        }
    }
}

// ── Category Breakdown — horizontal ranked bars ───────────────────────────────
// Part-to-whole with real labels reads far better as ranked bars than a donut:
// direct amount + percentage per row, and categories are instantly comparable
// by length instead of by eyeballing arc angles.
@Composable
private fun CategoryBarChart(
    items: List<Triple<String, Double, Color>>,
    totalExpenseSum: Double,
    currencySymbol: String,
) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    if (items.isEmpty()) {
        Text(text = "No category data available.", color = NeutralMid, fontSize = 13.sp)
        return
    }

    val maxAmount = items.maxOf { it.second }.let { if (it <= 0.0) 1.0 else it }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (category, amount, color) ->
            val pct = if (totalExpenseSum > 0) (amount / totalExpenseSum * 100) else 0.0
            val fraction = (amount / maxAmount).toFloat().coerceIn(0f, 1f)

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = category, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NeutralBlack)
                    Text(
                        text = "${currencySymbol}%,.2f".format(amount) + "  ·  %,.1f%%".format(pct),
                        fontSize = 12.sp,
                        color = NeutralMid,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(trackColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

// ── Custom Canvas Double Bar Chart Comparison ────────────────────────────────
@Composable
private fun DoubleBarChart(
    income: Float,
    expense: Float
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxVal = maxOf(income, expense, 100f)
                val padding = 40.dp.toPx()
                
                val barWidth = 44.dp.toPx()
                val spacing = 32.dp.toPx()
                
                val totalWidth = barWidth * 2 + spacing
                val startX = (size.width - totalWidth) / 2
                
                // 1. Income Bar
                val incomeHeight = (income / maxVal) * size.height
                drawRoundRect(
                    color = SemanticSuccess,
                    topLeft = Offset(startX, size.height - incomeHeight),
                    size = Size(barWidth, incomeHeight),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )

                // 2. Expense Bar
                val expenseHeight = (expense / maxVal) * size.height
                drawRoundRect(
                    color = SemanticError,
                    topLeft = Offset(startX + barWidth + spacing, size.height - expenseHeight),
                    size = Size(barWidth, expenseHeight),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Chart Legends label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(SemanticSuccess))
                Spacer(Modifier.width(6.dp))
                Text("Income", fontSize = 11.sp, color = NeutralMid, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(SemanticError))
                Spacer(Modifier.width(6.dp))
                Text("Expenses", fontSize = 11.sp, color = NeutralMid, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Financial Insight Card Component ─────────────────────────────────────────
@Composable
private fun InsightCard(
    title: String,
    value: String,
    desc: String,
    icon: ImageVector,
    accentColor: Color
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeutralWhite),
        border = BorderStroke(1.dp, NeutralLight),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 12.sp, color = NeutralMid, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NeutralBlack)
                Spacer(Modifier.height(2.dp))
                Text(text = desc, fontSize = 11.sp, color = NeutralMid)
            }
        }
    }
}

// ── Generic placeholder with shared top bar (Kept for BudgetScreen & others) ──
@Composable
private fun PlaceholderTab(
    icon: ImageVector,
    title: String,
    body: String,
    dateFilterState: DateFilterState,
) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralOffWhite),
    ) {
        // ── Top bar (Integrated SpendSync Header) ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeutralWhite)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Side: App Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Savings,
                    contentDescription = "App Logo",
                    tint = NeutralBlack,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SpendSync",
                    color = NeutralBlack,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }

            // Right Side: Date & Calendar Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { dateFilterState.showMonthPicker = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                Text(
                    text = dateFilterState.selectedDate.format(dateFormatter),
                    color = NeutralBlack,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Select Date",
                    tint = NeutralBlack,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(color = NeutralLight, thickness = 1.dp)

        // ── Placeholder body ──────────────────────────────────────────────────
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = BrandBlue.copy(alpha = 0.25f),
                modifier           = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text       = title,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = NeutralMid,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text     = body,
                fontSize = 14.sp,
                color    = NeutralMid,
            )
        }
    }
}

// ── Budget ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    sessionDataStore: SessionDataStore,
    financeRepository: FinanceRepository,
    dateFilterState: DateFilterState,
    onOpenSettings: () -> Unit = {},
    onViewTransaction: (TransactionDto) -> Unit = {},
) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val BrandBlue = MaterialTheme.colorScheme.primary

    val language by sessionDataStore.language.collectAsState(initial = "English")
    val currencyCode by sessionDataStore.currency.collectAsState(initial = "USD")
    val currencySymbol = remember(currencyCode) {
        when (currencyCode) {
            "EUR" -> "€"
            "GBP" -> "£"
            "INR" -> "₹"
            "JPY" -> "¥"
            else  -> "$"
        }
    }

    var summaryData by remember { mutableStateOf<DashboardSummaryDto?>(null) }
    var isBudgetLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var showGlobalSearch by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val activeMonth = remember(dateFilterState.selectedDate) {
        dateFilterState.selectedDate.toString().slice(0..6) // "YYYY-MM"
    }

    suspend fun fetchBudgetData(forceRefresh: Boolean) {
        when (val res = financeRepository.getDashboardSummary(month = activeMonth, forceRefresh = forceRefresh)) {
            is AuthResult.Success -> {
                summaryData = res.data
            }
            is AuthResult.Error -> {
                // handle
            }
        }
    }

    val loadData = {
        isBudgetLoading = true
        scope.launch {
            fetchBudgetData(forceRefresh = false)
            isBudgetLoading = false
        }
    }

    LaunchedEffect(activeMonth) {
        loadData()
    }

    var showCreateBudgetDialog by remember { mutableStateOf(false) }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralOffWhite)
    ) {
        // ── Top Bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeutralOffWhite)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wallet,
                    contentDescription = "Wallet Logo",
                    tint = NeutralBlack,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = LocalizationUtils.getTranslation("budget", language),
                    color = NeutralBlack,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }

            // Month Select Card + global search, grouped together.
            TopBarDateSearchGroup(
                selectedDate = dateFilterState.selectedDate,
                onCalendarClick = { dateFilterState.showMonthPicker = true },
                onSearchClick = { showGlobalSearch = true },
                contentColor = NeutralBlack,
                groupBackgroundColor = Color(0xFFF1F5F9),
                dateFormatPattern = "MMMM yyyy",
            )
        }

        // ── Main Body (pull-to-refresh) ──────────────────────────────────────
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Total Budget Card
            val totals = summaryData?.totals
            val totalBudget = totals?.totalBudget ?: 0.0
            val totalSpent = totals?.totalSpent ?: 0.0
            val budgetProgress = if (totalBudget > 0.0) (totalSpent / totalBudget).toFloat().coerceIn(0f..1f) else 0f

            if (isBudgetLoading) {
                SkeletonBox(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(24.dp),
                )
            } else {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                border = BorderStroke(1.dp, NeutralLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Total Monthly Budget Limit",
                        color = NeutralMid,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${currencySymbol}%,.2f".format(totalBudget),
                        color = NeutralBlack,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Spent: ${currencySymbol}%,.2f".format(totalSpent),
                            fontSize = 12.sp,
                            color = NeutralMid
                        )
                        Text(
                            text = "Remaining: ${currencySymbol}%,.2f".format((totalBudget - totalSpent).coerceAtLeast(0.0)),
                            fontSize = 12.sp,
                            color = if (totalSpent > totalBudget) Color.Red else BrandBlue
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { budgetProgress },
                        color = if (totalSpent > totalBudget) Color.Red else BrandBlue,
                        trackColor = NeutralLight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
            }

            Spacer(Modifier.height(24.dp))

            // Add Budget Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BrandBlue)
                    .clickable { showCreateBudgetDialog = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Set Category Budget",
                    color = NeutralWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(24.dp))

            // Category Budgets Header
            Text(
                text = "Category Limits",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = NeutralBlack
            )
            Spacer(Modifier.height(12.dp))

            // List of Category Limits
            val breakdown = summaryData?.categoryBreakdown?.filter { it.budget != null } ?: emptyList()

            if (isBudgetLoading) {
                repeat(3) {
                    SkeletonBox(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(72.dp),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            } else if (breakdown.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No category budgets set for this month.",
                        color = NeutralMid,
                        fontSize = 14.sp
                    )
                }
            } else {
                breakdown.forEach { cat ->
                    val limit = cat.budget ?: 0.0
                    val spent = cat.spent
                    val percentage = cat.percentageUsed ?: 0.0
                    val progressFloat = (percentage / 100.0).toFloat().coerceIn(0f..1f)

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                        border = BorderStroke(1.dp, NeutralLight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                  Text(
                                      text = cat.category,
                                      fontWeight = FontWeight.Bold,
                                      fontSize = 14.sp,
                                      color = NeutralBlack
                                  )
                                  Text(
                                      text = "${currencySymbol}%,.0f / ${currencySymbol}%,.0f".format(spent, limit),
                                      fontWeight = FontWeight.SemiBold,
                                      fontSize = 13.sp,
                                      color = NeutralBlack
                                  )
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progressFloat },
                                color = if (spent > limit) Color.Red else BrandBlue,
                                trackColor = NeutralLight,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "%.0f%% Used".format(percentage),
                                fontSize = 11.sp,
                                color = if (spent > limit) Color.Red else NeutralMid,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
        }
    }

    if (showCreateBudgetDialog) {
        CreateBudgetDialog(
            currencySymbol = currencySymbol,
            onDismiss = { showCreateBudgetDialog = false },
            onConfirm = { category, limit ->
                scope.launch {
                    val res = financeRepository.createBudget(
                        category = category,
                        month = activeMonth,
                        limitAmount = limit
                    )
                    if (res is AuthResult.Success) {
                        toast = ToastMessage("Budget added", isError = false)
                        loadData()
                        showCreateBudgetDialog = false
                    } else {
                        // Keep the dialog open so the user can pick a different
                        // category/month instead of the save silently vanishing.
                        toast = ToastMessage((res as AuthResult.Error).message, isError = true)
                    }
                }
            }
        )
    }

    if (showGlobalSearch) {
        GlobalSearchDialog(
            financeRepository = financeRepository,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateBudgetDialog(
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val BrandBlue = MaterialTheme.colorScheme.primary

    var category by remember { mutableStateOf("Groceries") }
    var limitAmount by remember { mutableStateOf("") }

    val categories = listOf("Groceries", "Shopping", "Dining Out", "Transport", "Bills", "Rent", "Entertainment", "Fitness", "Café", "Other")

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NeutralWhite),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Set Category Budget",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack
                )
                Spacer(Modifier.height(16.dp))

                // Category selector
                Text("Select Category", fontSize = 12.sp, color = NeutralMid)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        val isSelected = category == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) BrandBlue else NeutralLight.copy(alpha = 0.3f))
                                .clickable { category = cat }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(cat, color = if (isSelected) NeutralWhite else NeutralBlack, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Limit Amount input
                OutlinedTextField(
                    value = limitAmount,
                    onValueChange = { limitAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Budget Limit ($currencySymbol)", fontSize = 14.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = NeutralLight,
                        cursorColor = BrandBlue,
                        focusedLabelColor = BrandBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Cancel",
                        color = NeutralMid,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Set Limit",
                        color = NeutralWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandBlue)
                            .clickable(enabled = limitAmount.toDoubleOrNull() ?: 0.0 > 0.0) {
                                onConfirm(category, limitAmount.toDoubleOrNull() ?: 0.0)
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
