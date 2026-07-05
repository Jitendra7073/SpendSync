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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.spendsync.ui.theme.KakarikiActive
import com.example.spendsync.ui.theme.KakarikiBg
import com.example.spendsync.ui.theme.KakarikiOnBg
import com.example.spendsync.ui.theme.NeutralBlack
import com.example.spendsync.ui.theme.NeutralLight
import com.example.spendsync.ui.theme.NeutralMid
import com.example.spendsync.ui.theme.NeutralOffWhite
import com.example.spendsync.ui.theme.NeutralWhite
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Date range enum for the selector pills
enum class DateRange {
    THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_YEAR, CUSTOM
}

// ── Analytics ─────────────────────────────────────────────────────────────────

@Composable
fun AnalyticsScreen(
    sessionDataStore: SessionDataStore,
    financeRepository: FinanceRepository,
    dateFilterState: DateFilterState
) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val KakarikiBg = MaterialTheme.colorScheme.background
    val KakarikiOnBg = MaterialTheme.colorScheme.onBackground

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

    LaunchedEffect(bounds) {
        isAnalyticsLoading = true
        val startStr = startDate.toString() + "T00:00:00.000Z"
        val endStr = endDate.toString() + "T23:59:59.999Z"
        when (val res = financeRepository.getTransactions(startDate = startStr, endDate = endStr, limit = 500)) {
            is AuthResult.Success -> {
                apiTransactions = res.data
            }
            is AuthResult.Error -> {
                // handle error
            }
        }
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

    // Kakariki-matching category colors
    val categoryColors = remember {
        mapOf(
            "Salary" to Color(0xFF4D7C0F),        // Olive Green Accent
            "Freelance" to Color(0xFF65A30D),     // Light Olive Accent
            "Side Income" to Color(0xFF84CC16),   // Lime Accent
            "Gift" to Color(0xFF14B8A6),          // Teal
            "Groceries" to Color(0xFF0284C7),     // Sky Blue
            "Café" to Color(0xFFD97706),          // Dark Amber
            "Rent" to Color(0xFFB91C1C),          // Crimson Red
            "Fitness" to Color(0xFF0D9488),       // Teal Dark
            "Bills" to Color(0xFF7C3AED),         // Violet
            "Entertainment" to Color(0xFFDB2777), // Pink Accent
            "Transport" to Color(0xFFEA580C),      // Orange Accent
            "Dining Out" to Color(0xFF059669),     // Emerald
            "Shopping" to Color(0xFF4F46E5),       // Indigo
            "Travel" to Color(0xFF475569),         // Slate Grey
            "Other" to Color(0xFF64748B)          // Cool Grey
        )
    }

    // Group expenses by category
    val expensesByCategory = remember(apiTransactions) {
        apiTransactions
            .filter { it.type == "debit" }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount.toDoubleOrNull() ?: 0.0 } }
            .toList()
            .sortedByDescending { it.second }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralOffWhite),
    ) {
        // ── Top bar (Integrated Kakariki SpendSync Header) ────────────────────
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
                    tint = KakarikiOnBg,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SpendSync",
                    color = KakarikiOnBg,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            }

            // Right Side: Date & Calendar Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { 
                        selectedRange = DateRange.CUSTOM
                        dateFilterState.showMonthPicker = true 
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                Text(
                    text = dateFilterState.selectedDate.format(dateFormatter),
                    color = KakarikiOnBg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Select Date",
                    tint = KakarikiOnBg,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── Scrollable Body Content ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ── Pill selector capsules (Kakariki Styling) ────────────────────
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
                    
                    // Selected: Dark Olive (KakarikiOnBg), Unselected: White with Olive Border
                    val pillBg = if (isSelected) KakarikiOnBg else NeutralWhite
                    val pillText = if (isSelected) KakarikiBg else KakarikiOnBg
                    val border = BorderStroke(1.dp, KakarikiActive.copy(alpha = 0.6f))

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
                    color = Color(0xFF16A34A),
                    modifier = Modifier.weight(1f)
                )
                SummaryStatCard(
                    label = LocalizationUtils.getTranslation("expenses", language) + " Flow",
                    amount = "${currencySymbol}%,.2f".format(totalExpenses),
                    color = Color(0xFFDC2626),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── SECTION 2: Breakdown Analysis ────────────────────────────────
            SectionHeader(title = "Breakdown Analysis", subtitle = "Expense share by categories")
            Spacer(Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                border = BorderStroke(1.dp, KakarikiActive.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DonutChart(
                        categoryExpenses = expensesByCategory,
                        totalExpenseSum = totalExpenses,
                        categoryColors = categoryColors,
                        currencySymbol = currencySymbol
                    )

                    Spacer(Modifier.height(24.dp))

                    ExpensesLegend(
                        categoryExpenses = expensesByCategory,
                        totalExpenseSum = totalExpenses,
                        categoryColors = categoryColors
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── SECTION 3: Spending Comparison ───────────────────────────────
            SectionHeader(title = "Income vs Expenses", subtitle = "Flow proportion comparison")
            Spacer(Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                border = BorderStroke(1.dp, KakarikiActive.copy(alpha = 0.3f)),
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

            // ── SECTION 4: Financial Insights ─────────────────────────────────
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
                    accentColor = Color(0xFF16A34A)
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
                    accentColor = KakarikiOnBg
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── SECTION 5: Category Details List ─────────────────────────────
            SectionHeader(title = "Category Details", subtitle = "Percentage weight breakdown")
            Spacer(Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                border = BorderStroke(1.dp, KakarikiActive.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (expensesByCategory.isEmpty()) {
                        Text(
                            text = "No category data available.",
                            color = NeutralMid,
                            fontSize = 13.sp
                        )
                    } else {
                        expensesByCategory.forEach { (category, amount) ->
                            val pct = if (totalExpenses > 0) (amount / totalExpenses).toFloat() else 0f
                            val color = categoryColors[category] ?: Color.Gray

                            CategoryProgressItem(
                                label = category,
                                amount = "${currencySymbol}%,.2f".format(amount),
                                progress = pct,
                                color = color
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }

            // Bottom padding for the navigation bar
            Spacer(Modifier.height(100.dp))
        }
    }
}

// ── Section Header Helper Component ──────────────────────────────────────────
@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KakarikiActive)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = KakarikiOnBg
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeutralWhite),
        border = BorderStroke(1.dp, KakarikiActive.copy(alpha = 0.3f)),
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

// ── Interactive Custom Canvas Donut Chart ────────────────────────────────────
@Composable
private fun DonutChart(
    categoryExpenses: List<Pair<String, Double>>,
    totalExpenseSum: Double,
    categoryColors: Map<String, Color>,
    currencySymbol: String
) {
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val KakarikiOnBg = MaterialTheme.colorScheme.onBackground
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(180.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 24.dp.toPx()
            var startAngle = -90f

            if (totalExpenseSum == 0.0) {
                // Draw a standard grey circle when there are no expenses
                drawArc(
                    color = Color(0xFFE2E8F0),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth)
                )
            } else {
                categoryExpenses.forEach { (cat, amount) ->
                    val sweepAngle = ((amount / totalExpenseSum) * 360f).toFloat()
                    val color = categoryColors[cat] ?: Color.Gray

                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += sweepAngle
                }
            }
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Total Spent", fontSize = 11.sp, color = NeutralMid, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${currencySymbol}%,.2f".format(totalExpenseSum),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = KakarikiOnBg
            )
        }
    }
}

// ── Legend items below the chart ─────────────────────────────────────────────
@Composable
private fun ExpensesLegend(
    categoryExpenses: List<Pair<String, Double>>,
    totalExpenseSum: Double,
    categoryColors: Map<String, Color>
) {
    val KakarikiOnBg = MaterialTheme.colorScheme.onBackground
    if (categoryExpenses.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Chunk categories into rows of 2 for clean alignment
        val rows = categoryExpenses.chunked(2)
        rows.forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { (category, amount) ->
                    val color = categoryColors[category] ?: Color.Gray
                    val pct = if (totalExpenseSum > 0) (amount / totalExpenseSum * 100) else 0.0

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "%s (%,.1f%%)".format(category, pct),
                            fontSize = 12.sp,
                            color = KakarikiOnBg,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                // Add a blank placeholder spacer if row has only 1 item
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
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
                
                // 1. Income Bar (Green)
                val incomeHeight = (income / maxVal) * size.height
                drawRoundRect(
                    color = Color(0xFF16A34A),
                    topLeft = Offset(startX, size.height - incomeHeight),
                    size = Size(barWidth, incomeHeight),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )

                // 2. Expense Bar (Red)
                val expenseHeight = (expense / maxVal) * size.height
                drawRoundRect(
                    color = Color(0xFFDC2626),
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
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF16A34A)))
                Spacer(Modifier.width(6.dp))
                Text("Income", fontSize = 11.sp, color = NeutralMid, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFDC2626)))
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
    val KakarikiOnBg = MaterialTheme.colorScheme.onBackground
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeutralWhite),
        border = BorderStroke(1.dp, KakarikiActive.copy(alpha = 0.3f)),
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
                Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = KakarikiOnBg)
                Spacer(Modifier.height(2.dp))
                Text(text = desc, fontSize = 11.sp, color = NeutralMid)
            }
        }
    }
}

// ── Category List Progress Item Component ─────────────────────────────────────
@Composable
private fun CategoryProgressItem(
    label: String,
    amount: String,
    progress: Float,
    color: Color
) {
    val KakarikiOnBg = MaterialTheme.colorScheme.onBackground
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = KakarikiOnBg)
            Text(text = amount, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = KakarikiOnBg)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = Color(0xFFF1F5F9),
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
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
@Composable
fun BudgetScreen(
    sessionDataStore: SessionDataStore,
    financeRepository: FinanceRepository,
    dateFilterState: DateFilterState
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
    val scope = rememberCoroutineScope()

    val activeMonth = remember(dateFilterState.selectedDate) {
        dateFilterState.selectedDate.toString().slice(0..6) // "YYYY-MM"
    }

    val loadData = {
        isBudgetLoading = true
        scope.launch {
            when (val res = financeRepository.getDashboardSummary(month = activeMonth)) {
                is AuthResult.Success -> {
                    summaryData = res.data
                }
                is AuthResult.Error -> {
                    // handle
                }
            }
            isBudgetLoading = false
        }
    }

    LaunchedEffect(activeMonth) {
        loadData()
    }

    var showCreateBudgetDialog by remember { mutableStateOf(false) }

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

            // Month Select Card
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { dateFilterState.showMonthPicker = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val dateFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
                Text(
                    text = dateFilterState.selectedDate.format(dateFormatter),
                    color = NeutralBlack,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Select Month",
                    tint = NeutralBlack,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── Main Body ─────────────────────────────────────────────────────────
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

            if (breakdown.isEmpty()) {
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
                        loadData()
                    }
                    showCreateBudgetDialog = false
                }
            }
        )
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
