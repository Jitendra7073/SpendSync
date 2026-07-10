package com.example.spendsync.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.MaterialTheme
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.repository.AuthRepository
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.data.repository.AuthResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.example.spendsync.ui.components.SkeletonBox
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.search.GlobalSearchDialog
import com.example.spendsync.utils.LocalizationUtils
import com.example.spendsync.ui.shared.DateFilterState
import com.example.spendsync.ui.shared.TopBarDateSearchGroup
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.BrandYellow
import com.example.spendsync.ui.theme.NeutralBlack
import com.example.spendsync.ui.theme.NeutralMid
import com.example.spendsync.ui.theme.NeutralOffWhite
import com.example.spendsync.ui.theme.NeutralWhite
import com.example.spendsync.ui.theme.SemanticError
import kotlinx.coroutines.launch
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
    // Set when a search result is picked from a different tab (Analytics/
    // Budget) — each request uses a distinct id so repeat-selecting the same
    // transaction still reopens the dialog.
    externalViewTransactionId: Int = 0,
    externalViewTransaction: TransactionDto? = null,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant

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
    val language by sessionDataStore.language.collectAsState(initial = "English")
    val dateFormat by sessionDataStore.dateFormat.collectAsState(initial = "DD / MM / YYYY")
    val dateFormatPattern = remember(dateFormat) {
        LocalizationUtils.getDateFormatPattern(dateFormat)
    }

    var monthlyTransactions by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun loadTransactions(forceRefresh: Boolean) {
        val start = dateFilterState.selectedDate.withDayOfMonth(1).toString() + "T00:00:00.000Z"
        val end = dateFilterState.selectedDate.withDayOfMonth(dateFilterState.selectedDate.lengthOfMonth()).toString() + "T23:59:59.999Z"
        when (val res = financeRepository.getTransactions(startDate = start, endDate = end, limit = 500, forceRefresh = forceRefresh)) {
            is AuthResult.Success -> {
                monthlyTransactions = res.data
            }
            is AuthResult.Error -> {
                // handle error
            }
        }
    }

    LaunchedEffect(dateFilterState.selectedDate, refreshKey) {
        isLoading = true
        loadTransactions(forceRefresh = false)
        isLoading = false
    }

    // Dynamic Filter State
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // ALL, CREDIT, DEBIT

    // Global search modal + per-row action state
    var showGlobalSearch by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<TransactionDto?>(null) }
    var transactionToView by remember { mutableStateOf<TransactionDto?>(null) }

    LaunchedEffect(externalViewTransactionId) {
        if (externalViewTransactionId > 0) externalViewTransaction?.let { transactionToView = it }
    }

    // 1. Filter by EXACT Selected Date from Top Bar
    val dailyTransactions = remember(monthlyTransactions, dateFilterState.selectedDate) {
        monthlyTransactions.filter {
            try {
                val parsedDate = java.time.ZonedDateTime.parse(it.createdAt).toLocalDate()
                parsedDate.isEqual(dateFilterState.selectedDate)
            } catch (e: Exception) {
                false
            }
        }
    }

    // Calculate Summary Stats from daily data
    val totalIncome = remember(dailyTransactions) {
        dailyTransactions.filter { it.type == "credit" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    }
    val totalExpenses = remember(dailyTransactions) {
        dailyTransactions.filter { it.type == "debit" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    }
    val totalBalance = totalIncome - totalExpenses

    // 2. Filter by Credit/Debit Type (tapping the Income/Expense tile above)
    val filteredTransactions = remember(dailyTransactions, selectedTypeFilter) {
        dailyTransactions.filter { transaction ->
            when (selectedTypeFilter) {
                "CREDIT" -> transaction.type == "credit"
                "DEBIT" -> transaction.type == "debit"
                else -> true
            }
        }
    }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                loadTransactions(forceRefresh = true)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralOffWhite)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header — blue section ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandBlue)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Top Bar Header inside the Home Screen (adapts to blue background)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left Side: App Name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = "App Logo",
                        tint = NeutralWhite,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SpendSync",
                        color = NeutralWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }

                // Right Side: Calendar + global search, grouped together.
                TopBarDateSearchGroup(
                    selectedDate = dateFilterState.selectedDate,
                    onCalendarClick = { dateFilterState.showMonthPicker = true },
                    onSearchClick = { showGlobalSearch = true },
                    contentColor = NeutralWhite,
                    groupBackgroundColor = NeutralWhite.copy(alpha = 0.15f),
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Balance card ──────────────────────────────────────────────────
            Card(
                shape     = RoundedCornerShape(20.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = NeutralWhite.copy(alpha = 0.15f),
                ),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier  = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    Text(
                        text     = LocalizationUtils.getTranslation("total_balance", language),
                        color    = NeutralWhite.copy(alpha = 0.80f),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text       = "${currencySymbol}%,.2f".format(totalBalance),
                        color      = NeutralWhite,
                        fontSize   = 36.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Yellow accent bar
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BrandYellow),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Responsive & Premium Summary tiles (Income & Expenses) ───────────
        // Tapping a tile filters the list below by that type — tap again to
        // clear the filter. Replaces the separate ALL/Income/Expense pills.
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLoading) {
                SkeletonBox(modifier = Modifier.weight(1f).height(78.dp), shape = RoundedCornerShape(16.dp))
                SkeletonBox(modifier = Modifier.weight(1f).height(78.dp), shape = RoundedCornerShape(16.dp))
            } else {
            // Premium Income Card
            SummaryTile(
                label = LocalizationUtils.getTranslation("income", language),
                amount = "${currencySymbol}%,.2f".format(totalIncome),
                icon = Icons.Default.ArrowUpward,
                cardColor = Color(0xFFF0FDF4),       // Soft light green tint
                borderColor = Color(0xFFDCFCE7),     // Soft green border
                accentColor = Color(0xFF15803D),     // Deep green icon/text
                isSelected = selectedTypeFilter == "CREDIT",
                onClick = { selectedTypeFilter = if (selectedTypeFilter == "CREDIT") "ALL" else "CREDIT" },
                modifier = Modifier.weight(1f)
            )

            // Premium Expenses Card
            SummaryTile(
                label = LocalizationUtils.getTranslation("expenses", language),
                amount = "${currencySymbol}%,.2f".format(totalExpenses),
                icon = Icons.Default.ArrowDownward,
                cardColor = Color(0xFFFEF2F2),       // Soft light red tint
                borderColor = Color(0xFFFEE2E2),     // Soft red border
                accentColor = Color(0xFFB91C1C),     // Deep red icon/text
                isSelected = selectedTypeFilter == "DEBIT",
                onClick = { selectedTypeFilter = if (selectedTypeFilter == "DEBIT") "ALL" else "DEBIT" },
                modifier = Modifier.weight(1f)
            )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Transactions List Section ─────────────────────────────────────────
        Text(
            text       = LocalizationUtils.getTranslation("recent_transactions", language),
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold,
            color      = NeutralBlack,
            modifier   = Modifier.padding(horizontal = 20.dp),
        )
        
        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            repeat(3) { TransactionRowSkeleton() }
        } else if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text     = "No transactions found for this period.",
                    fontSize = 14.sp,
                    color    = NeutralMid,
                )
            }
        } else {
            // Render filtered list dynamically inside the Scrollable Column
            filteredTransactions.forEach { transaction ->
                TransactionRow(
                    transaction = transaction,
                    currencySymbol = currencySymbol,
                    dateFormatPattern = dateFormatPattern,
                    onDelete = { transactionToDelete = transaction },
                    onEdit = { onEditTransaction(transaction) },
                    onInfo = { transactionToView = transaction },
                )
            }
        }

        // Bottom padding to ensure content isn't hidden behind the floating bottom navigation bar
        Spacer(Modifier.height(100.dp))
    }
    }

    // ── Delete confirmation ───────────────────────────────────────────────────
    transactionToDelete?.let { tx ->
        DeleteTransactionDialog(
            transaction = tx,
            currencySymbol = currencySymbol,
            onDismiss = { transactionToDelete = null },
            onConfirm = {
                scope.launch {
                    val res = financeRepository.deleteTransaction(tx.id)
                    if (res is AuthResult.Success) {
                        monthlyTransactions = monthlyTransactions.filter { it.id != tx.id }
                        toast = ToastMessage("Transaction deleted", isError = false)
                    } else {
                        toast = ToastMessage((res as AuthResult.Error).message, isError = true)
                    }
                    transactionToDelete = null
                }
            },
        )
    }

    // ── Transaction details ───────────────────────────────────────────────────
    transactionToView?.let { tx ->
        TransactionInfoDialog(
            transaction = tx,
            currencySymbol = currencySymbol,
            onDismiss = { transactionToView = null },
        )
    }

    // ── Global search ─────────────────────────────────────────────────────────
    if (showGlobalSearch) {
        GlobalSearchDialog(
            financeRepository = financeRepository,
            onDismiss = { showGlobalSearch = false },
            onTransactionSelected = { tx ->
                showGlobalSearch = false
                transactionToView = tx
            },
            onOpenSettings = {
                showGlobalSearch = false
                onOpenSettings()
            },
        )
    }
    }
}


// ── Premium Summary Card Component ──────────────────────────────────────────
@Composable
private fun SummaryTile(
    label: String,
    amount: String,
    icon: ImageVector,
    cardColor: Color,
    borderColor: Color,
    accentColor: Color,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) accentColor else borderColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = NeutralMid
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = amount,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
        }
    }
}

// ── Transaction Row Skeleton (shown while the month's data is loading) ───────
@Composable
private fun TransactionRowSkeleton() {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeutralWhite),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBox(modifier = Modifier.size(40.dp), shape = CircleShape)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                SkeletonBox(modifier = Modifier.width(120.dp).height(14.dp))
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(modifier = Modifier.width(80.dp).height(11.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            SkeletonBox(modifier = Modifier.width(60.dp).height(15.dp))
        }
    }
}

// ── Transaction Row Component ────────────────────────────────────────────────
@Composable
private fun TransactionRow(
    transaction: TransactionDto,
    currencySymbol: String,
    dateFormatPattern: String,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant

    val dateForm = remember(dateFormatPattern) {
        java.time.format.DateTimeFormatter.ofPattern(dateFormatPattern)
    }

    val parsedDate = remember(transaction.createdAt) {
        try {
            java.time.ZonedDateTime.parse(transaction.createdAt).toLocalDate()
        } catch (e: Exception) {
            LocalDate.now()
        }
    }

    val amountVal = remember(transaction.amount) {
        transaction.amount.toDoubleOrNull() ?: 0.0
    }

    val isCredit = transaction.type == "credit"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeutralWhite),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Category Icon Badge
                val iconBg = if (isCredit) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                val iconTint = if (isCredit) Color(0xFF15803D) else Color(0xFF475569)
                val categoryIcon = when (transaction.category) {
                    "Salary", "Freelance", "Side Income" -> Icons.Default.Savings
                    "Groceries", "Shopping" -> Icons.Default.ShoppingCart
                    "Rent", "Bills" -> Icons.Default.Home
                    else -> Icons.Default.Star
                }
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = transaction.merchant.ifBlank { transaction.category },
                        color = NeutralBlack,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = transaction.category,
                            color = NeutralMid,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "•",
                            color = NeutralMid,
                            fontSize = 12.sp
                        )
                        Text(
                            text = parsedDate.format(dateForm),
                            fontSize = 11.sp,
                            color = NeutralMid,
                        )
                    }
                }
            }
            
            val amountText = if (isCredit) {
                "+ ${currencySymbol}%,.2f".format(amountVal)
            } else {
                "- ${currencySymbol}%,.2f".format(amountVal)
            }
            val amountColor = if (isCredit) Color(0xFF16A34A) else Color(0xFFDC2626)
            
            Text(
                text = amountText,
                color = amountColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Row actions: info, edit, delete ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Transaction details",
                    tint = NeutralMid,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit transaction",
                    tint = NeutralMid,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete transaction",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        }
    }
}

// ── Delete Confirmation Dialog ───────────────────────────────────────────────
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DeleteTransactionDialog(
    transaction: TransactionDto,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val amountVal = transaction.amount.toDoubleOrNull() ?: 0.0

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NeutralWhite),
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = SemanticError,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Delete transaction?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${transaction.merchant.ifBlank { transaction.category }} — $currencySymbol%,.2f".format(amountVal),
                    fontSize = 13.sp,
                    color = NeutralMid,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "This can't be undone.",
                    fontSize = 12.sp,
                    color = NeutralMid,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", color = NeutralBlack, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SemanticError)
                            .clickable(onClick = onConfirm)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Delete", color = NeutralWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ── Transaction Info Dialog ──────────────────────────────────────────────────
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TransactionInfoDialog(
    transaction: TransactionDto,
    currencySymbol: String,
    onDismiss: () -> Unit,
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val amountVal = transaction.amount.toDoubleOrNull() ?: 0.0
    val isCredit = transaction.type == "credit"

    val createdDate = remember(transaction.createdAt) {
        try {
            java.time.ZonedDateTime.parse(transaction.createdAt)
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()))
        } catch (e: Exception) {
            transaction.createdAt
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NeutralWhite),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Transaction Details",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeutralBlack,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = NeutralMid)
                    }
                }
                Spacer(Modifier.height(12.dp))

                InfoRow("Amount", "${if (isCredit) "+" else "-"} $currencySymbol%,.2f".format(amountVal))
                InfoRow("Type", if (isCredit) "Income" else "Expense")
                InfoRow("Merchant / Note", transaction.merchant.ifBlank { "—" })
                InfoRow("Category", transaction.category)
                if (!transaction.note.isNullOrBlank()) InfoRow("Note", transaction.note)
                if (!transaction.sourceApp.isNullOrBlank()) InfoRow("Source", transaction.sourceApp)
                InfoRow("Date", createdDate)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, fontSize = 13.sp, color = NeutralMid)
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = NeutralBlack,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
