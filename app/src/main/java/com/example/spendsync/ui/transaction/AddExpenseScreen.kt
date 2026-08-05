package com.example.spendsync.ui.transaction

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.spendsync.data.local.SessionDataStore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.IconifyRepository
import com.example.spendsync.data.local.PersistedCategory
import com.example.spendsync.data.remote.IconifyApiClient
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.components.rememberPressScale
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.MaskableAmountText
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.window.DialogProperties
import com.example.spendsync.ui.theme.SemanticError
import com.example.spendsync.ui.theme.SemanticSuccess
import com.example.spendsync.ui.shared.MonthPickerDialog
import com.example.spendsync.notifications.HoldReminderWorker
import androidx.compose.material.icons.filled.CalendarMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ─────────────────────────────────────────────────────────────────────────────
//  Data
// ─────────────────────────────────────────────────────────────────────────────

enum class TransactionType { INCOME, EXPENSE }

// internal (not private) — the default category → icon mapping is the single
// source of truth other screens (HomeScreen's transaction rows, global search)
// look up when rendering a transaction's category icon. See builtInCategoryIcon.
internal data class Category(
    val label: String,
    val icon: ImageVector? = null,
    // Iconify "prefix:name" — set for categories picked via the icon search,
    // rendered remotely via Coil instead of a bundled ImageVector.
    val iconId: String? = null,
)

internal val incomeCategories = listOf(
    Category("Salary",     Icons.Default.Work),
    Category("Freelance",  Icons.Default.AttachMoney),
    Category("Business",   Icons.Default.Home),
    Category("Gift",       Icons.Default.CardGiftcard),
    Category("Investment", Icons.Default.AttachMoney),
)

private fun toCategory(persisted: PersistedCategory): Category =
    if (persisted.iconId != null) Category(persisted.name, iconId = persisted.iconId)
    else Category(persisted.name, icon = Icons.Default.Star)

internal val expenseCategories = listOf(
    Category("Food",        Icons.Default.Fastfood),
    Category("Transport",   Icons.Default.DirectionsCar),
    Category("Shopping",    Icons.Default.ShoppingBag),
    Category("Housing",     Icons.Default.Home),
    Category("Health",      Icons.Default.LocalHospital),
    Category("Education",   Icons.Default.School),
    Category("Travel",      Icons.Default.Flight),
    Category("Bills",       Icons.Default.Wifi),
    Category("Fitness",     Icons.Default.FitnessCenter),
    Category("Movies",      Icons.Default.MovieFilter),
)

/**
 * The icon for one of the built-in default categories (income or expense),
 * or null if [category] isn't a default — i.e. it's a custom category, whose
 * icon lives in [PersistedCategory.iconId] instead and must be looked up there.
 */
internal fun builtInCategoryIcon(category: String): ImageVector? =
    (incomeCategories + expenseCategories).firstOrNull { it.label == category }?.icon

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AddExpenseScreen(
    sessionDataStore: SessionDataStore,
    financeRepository: FinanceRepository,
    amountVisibility: AmountVisibilityState,
    editTransaction: TransactionDto? = null,
    initialType: TransactionType? = null,
    onBack: () -> Unit
) {
    val isEditing = editTransaction != null
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    val currencySymbol = "₹"

    var type        by rememberSaveable {
        mutableStateOf(
            when {
                editTransaction != null -> if (editTransaction.type == "credit") TransactionType.INCOME else TransactionType.EXPENSE
                initialType != null     -> initialType
                else                    -> TransactionType.EXPENSE
            }
        )
    }
    var amount      by rememberSaveable { mutableStateOf(editTransaction?.amount ?: "") }
    var selectedCat by rememberSaveable { mutableStateOf(editTransaction?.category) }
    var note        by rememberSaveable { mutableStateOf(editTransaction?.note ?: "") } // Unifies description/note

    val today = remember { LocalDate.now() }
    var transactionDate by rememberSaveable {
        mutableStateOf(
            editTransaction?.createdAt?.let {
                try { java.time.ZonedDateTime.parse(it).toLocalDate() } catch (e: Exception) { today }
            } ?: today
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }

    var expectReturn by remember { mutableStateOf(false) }
    var holdPersonName by remember { mutableStateOf("") }
    var holdReturnDate by remember { mutableStateOf(today) }
    var showHoldDatePicker by remember { mutableStateOf(false) }

    // Hold reminders post a notification; on API 33+ that's silently dropped
    // unless POST_NOTIFICATIONS was granted. Nothing else in this flow asks
    // for it — the auto-capture opt-in in ProfileScreen is unrelated.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: denial just means the reminder won't show, same as elsewhere */ }

    // Auto-focus the amount as soon as the screen opens — it's the first
    // thing every transaction needs, so the keyboard should already be up.
    val amountFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { amountFocusRequester.requestFocus() }

    // All-time balance — expenses are never allowed to push it negative.
    // Best-effort (client-derived, not an authoritative ledger lock): if it
    // hasn't loaded yet, the save guard fails open rather than blocking on a
    // spinner, same tradeoff HomeScreen's balance card already makes.
    var availableBalance by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        when (val res = financeRepository.getAllTimeBalance()) {
            is AuthResult.Success -> availableBalance = res.data
            is AuthResult.Error -> Unit
        }
    }

    val amountVal = remember(amount) { amount.toDoubleOrNull() ?: 0.0 }

    // Editing an expense shouldn't count its OWN old amount against the new
    // one — put it back first, so only the delta is checked. Editing an
    // income being switched to expense removes that income's contribution
    // the same way.
    val availableForThisTransaction = remember(availableBalance, editTransaction) {
        val base = availableBalance ?: 0.0
        if (editTransaction == null) {
            base
        } else {
            val originalAmount = editTransaction.amount.toDoubleOrNull() ?: 0.0
            if (editTransaction.type == "debit") base + originalAmount else base - originalAmount
        }
    }

    val insufficientBalance = remember(type, amountVal, availableForThisTransaction, availableBalance) {
        type == TransactionType.EXPENSE && availableBalance != null && amountVal > availableForThisTransaction
    }

    // Categories added THIS session, before the DataStore write round-trips —
    // so a brand-new category shows up immediately without waiting on persistence.
    var sessionIncomeCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var sessionExpenseCategories by remember { mutableStateOf<List<Category>>(emptyList()) }

    // Persisted custom categories, icon included — the source of truth for
    // anything the user has actually added via the icon picker.
    val savedIncome by sessionDataStore.customIncomeCategories.collectAsState(initial = emptyList())
    val savedExpense by sessionDataStore.customExpenseCategories.collectAsState(initial = emptyList())

    // Pool = persisted customs (real icon) → this-session additions → built-in
    // defaults → the edited transaction's own category as a last-resort
    // placeholder. Built as one pure merge, not staggered LaunchedEffects —
    // with effects, the edit-category placeholder could load before the real
    // persisted entry and permanently shadow it (its icon silently downgrading
    // to a plain star). putIfAbsent means the richer entry always wins.
    val poolIncomeCategories = remember(savedIncome, sessionIncomeCategories, editTransaction) {
        val byLabel = LinkedHashMap<String, Category>()
        savedIncome.forEach { byLabel.putIfAbsent(it.name, toCategory(it)) }
        sessionIncomeCategories.forEach { byLabel.putIfAbsent(it.label, it) }
        incomeCategories.forEach { byLabel.putIfAbsent(it.label, it) }
        if (editTransaction?.type == "credit") {
            byLabel.putIfAbsent(editTransaction.category, Category(editTransaction.category, Icons.Default.Star))
        }
        byLabel.values.toList()
    }
    val poolExpenseCategories = remember(savedExpense, sessionExpenseCategories, editTransaction) {
        val byLabel = LinkedHashMap<String, Category>()
        savedExpense.forEach { byLabel.putIfAbsent(it.name, toCategory(it)) }
        sessionExpenseCategories.forEach { byLabel.putIfAbsent(it.label, it) }
        expenseCategories.forEach { byLabel.putIfAbsent(it.label, it) }
        if (editTransaction != null && editTransaction.type != "credit") {
            byLabel.putIfAbsent(editTransaction.category, Category(editTransaction.category, Icons.Default.Star))
        }
        byLabel.values.toList()
    }

    val categoryPool = if (type == TransactionType.INCOME) poolIncomeCategories else poolExpenseCategories
    val defaultsForType = if (type == TransactionType.INCOME) incomeCategories else expenseCategories

    // A broad, cached fetch — reused across the session — to rank categories
    // by how often this user actually picks them, so the 8 pinned up top are
    // personal rather than an arbitrary fixed list.
    var recentTransactions by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    LaunchedEffect(Unit) {
        when (val res = financeRepository.getTransactions(limit = 200)) {
            is AuthResult.Success -> recentTransactions = res.data
            is AuthResult.Error -> Unit
        }
    }

    // Kept at the front of the list even if usage history wouldn't otherwise
    // surface them near the top — the transaction being edited, and whatever
    // the user just created — so neither is buried at the bottom of a scroll.
    val originalEditCategory = remember { editTransaction?.category }
    var justAddedCategory by remember { mutableStateOf<String?>(null) }

    // A-Z filter — a shortcut for jumping straight to a letter once the pool
    // is big enough that even scrolling the grid is slower than that.
    // Multi-select: any letter in the set matches, applied live as you tap.
    var selectedFilterLetters by remember { mutableStateOf<Set<Char>>(emptySet()) }
    var showAlphabetFilter by remember { mutableStateOf(false) }
    LaunchedEffect(type) { selectedFilterLetters = emptySet() }

    // Every category for this type, most-used first (falling back to the
    // curated default order), with no cap — the grid scrolls internally to
    // reach the rest instead of hiding anything behind the alphabet filter.
    val rankedCategories = remember(categoryPool, recentTransactions, type, justAddedCategory) {
        val apiType = if (type == TransactionType.INCOME) "credit" else "debit"
        val byLabel = categoryPool.associateBy { it.label }
        val mostUsed = recentTransactions
            .filter { it.type == apiType }
            .groupingBy { it.category }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }
            .filter { it in byLabel }

        val ordered = LinkedHashSet<String>().apply {
            originalEditCategory?.let { if (it in byLabel) add(it) }
            justAddedCategory?.let { if (it in byLabel) add(it) }
            addAll(mostUsed)
            defaultsForType.forEach { add(it.label) }
            addAll(categoryPool.map { it.label })
        }

        ordered.mapNotNull { byLabel[it] }
    }

    // Categories currently shown in the grid: every category (scrollable) by
    // default, or every category starting with any of the chosen letters once
    // the filter is active — recomputed live as letters are toggled.
    val displayedCategories = remember(rankedCategories, categoryPool, selectedFilterLetters) {
        if (selectedFilterLetters.isEmpty()) {
            rankedCategories
        } else {
            categoryPool
                .filter { it.label.firstOrNull()?.uppercaseChar() in selectedFilterLetters }
                .sortedBy { it.label }
        }
    }

    // Which starting letters actually have a category — disables dead letters
    // in the A-Z sheet instead of showing 26 options where most do nothing.
    val availableFilterLetters = remember(categoryPool) {
        categoryPool.mapNotNull { it.label.firstOrNull()?.uppercaseChar() }.toSet()
    }

    // Auto-suggest a category from what the user types, based on past picks
    // (backend learns merchant-keyword → category rules as transactions are
    // saved — see the createCategory call below). Only kicks in before the
    // user has manually picked a category, and only for a name already in
    // the current grid so the suggestion always shows as a highlighted chip.
    LaunchedEffect(note) {
        if (selectedCat == null && note.isNotBlank()) {
            when (val res = financeRepository.suggestCategory(note)) {
                is AuthResult.Success -> {
                    val suggested = res.data.suggestedCategory
                    if (suggested != null && selectedCat == null &&
                        displayedCategories.any { it.label == suggested }
                    ) {
                        selectedCat = suggested
                    }
                }
                is AuthResult.Error -> Unit
            }
        }
    }
    val accentColor = if (type == TransactionType.INCOME) SemanticSuccess else SemanticError
    val headerColor by animateColorAsState(
        targetValue   = accentColor,
        animationSpec = tween(300),
        label         = "header_color",
    )

    // Add-category screen state
    var showAddCategory by remember { mutableStateOf(false) }
    val iconifyRepository = remember { IconifyRepository() }

    // Combine the displayed category list with a special "+" Add button item
    val gridItems = remember(displayedCategories) {
        displayedCategories + Category("+ Add", Icons.Default.Add)
    }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralOffWhite),
    ) {
        // ── Coloured header ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerColor)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            // Back arrow + title
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = NeutralWhite,
                    )
                }
                Text(
                    text       = if (isEditing) "Edit Transaction" else "Add Transaction",
                    color      = NeutralWhite,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Income / Expense toggle ───────────────────────────────────────
            TypeToggle(
                selected  = type,
                onSelect  = { type = it; selectedCat = null },
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )

            Spacer(Modifier.height(20.dp))

            // ── Amount input ──────────────────────────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text     = "Amount",
                    color    = NeutralWhite.copy(alpha = 0.80f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text       = currencySymbol,
                        color      = NeutralWhite,
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value         = amount,
                        onValueChange = { v ->
                            val filtered = v.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1) amount = filtered
                        },
                        placeholder   = {
                            Text(
                                text     = "0.00",
                                color    = NeutralWhite.copy(alpha = 0.45f),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        textStyle     = androidx.compose.ui.text.TextStyle(
                            color      = NeutralWhite,
                            fontSize   = 28.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor          = NeutralWhite,
                        ),
                        singleLine    = true,
                        modifier      = Modifier
                            .weight(1f)
                            .focusRequester(amountFocusRequester),
                    )
                }

                // Live insufficient-balance warning — updates as the user types,
                // instead of only failing after they tap Save.
                if (insufficientBalance) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = NeutralWhite,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Insufficient balance — ",
                            color = NeutralWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        MaskableAmountText(
                            amount = availableForThisTransaction.coerceAtLeast(0.0),
                            visibility = amountVisibility,
                            color = NeutralWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = " available",
                            color = NeutralWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Conditionally show Note field directly under Amount if user has entered an amount
                if (amount.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { 
                            Text(
                                "Add a note (description)...", 
                                color = NeutralWhite.copy(alpha = 0.60f), 
                                fontSize = 14.sp
                            ) 
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = NeutralWhite, fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeutralWhite,
                            unfocusedBorderColor = NeutralWhite.copy(alpha = 0.40f),
                            cursorColor = NeutralWhite,
                            focusedLabelColor = NeutralWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                // Inline "expect this back?" toggle — only for NEW transactions,
                // a hold can't be attached retroactively while editing.
                if (!isEditing && amount.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (type == TransactionType.EXPENSE) "Expect this back?" else "Need to pay this back?",
                            color = NeutralWhite,
                            fontSize = 14.sp,
                        )
                        Switch(checked = expectReturn, onCheckedChange = { expectReturn = it })
                    }
                    if (expectReturn) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = holdPersonName,
                            onValueChange = { holdPersonName = it },
                            placeholder = { Text("Who's this with?", color = NeutralWhite.copy(alpha = 0.60f), fontSize = 14.sp) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = NeutralWhite, fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeutralWhite,
                                unfocusedBorderColor = NeutralWhite.copy(alpha = 0.40f),
                                cursorColor = NeutralWhite,
                                focusedLabelColor = NeutralWhite
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHoldDatePicker = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = NeutralWhite)
                            Spacer(Modifier.width(8.dp))
                            Text("Expected return: $holdReturnDate", color = NeutralWhite, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        // ── Body — Date fixed at top, Category grid fills the rest of the ────
        // screen down to the pinned Save bar (not a page scroll — the grid
        // has its own internal scroll for when categories overflow it).
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Date ─────────────────────────────────────────────────────────
            SectionLabel("Date")
            Spacer(Modifier.height(12.dp))
            DateSelectorRow(
                date = transactionDate,
                accentColor = accentColor,
                onClick = { showDatePicker = true },
            )

            Spacer(Modifier.height(24.dp))

            // ── Category label + alphabet filter (once the pool earns it) ─────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Category")
                if (categoryPool.size > 10) {
                    IconButton(
                        onClick = { showAlphabetFilter = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter categories alphabetically",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Active-filter pill — lets the user clear it without reopening the sheet.
            if (selectedFilterLetters.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .clickable { selectedFilterLetters = emptySet() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Letters: ${selectedFilterLetters.sorted().joinToString(", ")}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear letter filter",
                            tint = accentColor,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Grid displaying the displayed categories + the special "+" Chip at the
            // end — fills all remaining vertical space down to the Save bar
            // (weight(1f), not a fixed height), and scrolls internally once
            // there are more categories than fit in that space.
            LazyVerticalGrid(
                columns             = GridCells.Fixed(4),
                contentPadding      = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                modifier            = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(gridItems) { cat ->
                    if (cat.label == "+ Add") {
                        // Render add custom category trigger chip
                        CategoryChip(
                            category   = cat,
                            isSelected = false,
                            color      = accentColor,
                            onClick    = { showAddCategory = true },
                        )
                    } else {
                        CategoryChip(
                            category   = cat,
                            isSelected = selectedCat == cat.label,
                            color      = accentColor,
                            onClick    = { selectedCat = cat.label },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // ── Save button — always pinned to the bottom of the screen ──────────
        // Same tactile press-scale PrimaryButton uses elsewhere in the app —
        // this is the single most-used action in SpendSync, it should feel
        // at least as considered as the login button does.
        val (saveButtonScale, saveButtonInteractionSource) = rememberPressScale()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeutralWhite)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(saveButtonScale)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (insufficientBalance) accentColor.copy(alpha = 0.5f) else accentColor)
                    .clickable(
                        interactionSource = saveButtonInteractionSource,
                        indication        = ripple(bounded = true, color = NeutralWhite),
                        onClick           = {
                            if (transactionDate.isAfter(LocalDate.now())) {
                                toast = ToastMessage("Transaction date cannot be in the future.", isError = true)
                                return@clickable
                            }
                            if (insufficientBalance) {
                                // ToastMessage.message is a plain String rendered by
                                // ToastBanner outside a Composable eye-icon affordance —
                                // same reasoning as HoldReminderNotifier's system
                                // notification text: no tap target is possible here,
                                // so this figure is left unmasked deliberately.
                                toast = ToastMessage(
                                    "Insufficient balance. You have $currencySymbol${"%,.2f".format(availableForThisTransaction.coerceAtLeast(0.0))} available.",
                                    isError = true,
                                )
                                return@clickable
                            }
                            if (!isEditing && expectReturn && holdPersonName.isBlank()) {
                                toast = ToastMessage(
                                    "Enter who this is with, or turn off \"Expect this back?\"",
                                    isError = true,
                                )
                                return@clickable
                            }
                            if (amountVal > 0.0 && !selectedCat.isNullOrBlank()) {
                                scope.launch {
                                     val apiType = if (type == TransactionType.INCOME) "credit" else "debit"
                                     val merchantName = if (note.isNotBlank()) note else selectedCat ?: "Other"
                                     val chosenCategory = selectedCat ?: "Other"
                                     val dateStr = "${transactionDate}T00:00:00.000Z"
                                     val res = if (isEditing) {
                                         financeRepository.updateTransaction(
                                             id = editTransaction.id,
                                             amount = amountVal,
                                             type = apiType,
                                             merchant = merchantName,
                                             category = chosenCategory,
                                             note = note,
                                             transactionDate = dateStr,
                                         )
                                     } else {
                                         financeRepository.createTransaction(
                                             amount = amountVal,
                                             type = apiType,
                                             merchant = merchantName,
                                             category = chosenCategory,
                                             note = note,
                                             transactionDate = dateStr,
                                         )
                                     }
                                     // Teach the suggestion engine: next time this merchant
                                     // is typed, suggestCategory() will offer this category.
                                     // Own coroutine so it never delays navigating back —
                                     // a duplicate keyword 409s harmlessly either way.
                                     if (note.isNotBlank()) {
                                         val keyword = note.trim().lowercase()
                                         scope.launch { financeRepository.createCategory(keyword, chosenCategory) }
                                     }
                                     if (res is AuthResult.Success) {
                                         if (!isEditing && expectReturn && holdPersonName.isNotBlank()) {
                                             val direction = if (type == TransactionType.EXPENSE) "owed_to_me" else "owed_by_me"
                                             val holdRes = financeRepository.createHold(
                                                 transactionId = res.data.id,
                                                 direction = direction,
                                                 personName = holdPersonName,
                                                 amount = amountVal,
                                                 expectedReturnDate = "${holdReturnDate}T00:00:00.000Z",
                                             )
                                             if (holdRes is AuthResult.Success) {
                                                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                     ContextCompat.checkSelfPermission(
                                                         context,
                                                         Manifest.permission.POST_NOTIFICATIONS,
                                                     ) != PackageManager.PERMISSION_GRANTED
                                                 ) {
                                                     notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                 }
                                                 HoldReminderWorker.schedule(
                                                     context = context,
                                                     holdId = holdRes.data.id,
                                                     personName = holdPersonName,
                                                     amount = amountVal,
                                                     direction = direction,
                                                     expectedReturnDate = holdReturnDate,
                                                 )
                                             } else {
                                                 toast = ToastMessage(
                                                     "Transaction saved, but couldn't track the hold: ${(holdRes as AuthResult.Error).message}",
                                                     isError = true,
                                                 )
                                                 delay(1500)
                                                 onBack()
                                                 return@launch
                                             }
                                         }
                                         toast = ToastMessage(
                                             if (isEditing) "Transaction updated" else "Transaction added",
                                             isError = false
                                         )
                                         delay(500)
                                         onBack()
                                     } else {
                                         val message = (res as AuthResult.Error).message
                                         toast = ToastMessage(message, isError = true)
                                     }
                                }
                            }
                        },
                    )
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = when {
                        isEditing && type == TransactionType.INCOME  -> "Update Income"
                        isEditing                                    -> "Update Expense"
                        type == TransactionType.INCOME                -> "Save Income"
                        else                                          -> "Save Expense"
                    },
                    color      = NeutralWhite,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    // Add-category screen — search Iconify, pick a name + icon
    if (showAddCategory) {
        CategoryIconPickerScreen(
            iconifyRepository = iconifyRepository,
            accentColor = accentColor,
            existingCategoryNames = categoryPool.map { it.label }.toSet(),
            existingIconIds = categoryPool.mapNotNull { it.iconId }.toSet(),
            onDismiss = { showAddCategory = false },
            onCategoryCreated = { name, iconId ->
                val newCat = Category(name, iconId = iconId)
                if (type == TransactionType.INCOME) {
                    sessionIncomeCategories = sessionIncomeCategories + newCat
                    scope.launch { sessionDataStore.addCustomIncomeCategory(name, iconId) }
                } else {
                    sessionExpenseCategories = sessionExpenseCategories + newCat
                    scope.launch { sessionDataStore.addCustomExpenseCategory(name, iconId) }
                }
                selectedCat = name // Automatically select the newly created category
                justAddedCategory = name // ...and keep it pinned near the front of the list
                selectedFilterLetters = emptySet() // back to the pinned view so it's visible
                showAddCategory = false
            },
        )
    }

    // A-Z category filter — only reachable once the category section's filter
    // icon is shown (pool > 10), and only lists letters that have a match.
    // Stays open while toggling letters — the grid behind filters live, and
    // only closes on an outside tap/swipe (ModalBottomSheet's own dismiss).
    if (showAlphabetFilter) {
        CategoryAlphabetFilterSheet(
            availableLetters = availableFilterLetters,
            selectedLetters = selectedFilterLetters,
            matchCount = displayedCategories.size,
            accentColor = accentColor,
            onToggle = { letter ->
                selectedFilterLetters = if (letter in selectedFilterLetters) {
                    selectedFilterLetters - letter
                } else {
                    selectedFilterLetters + letter
                }
            },
            onClearAll = { selectedFilterLetters = emptySet() },
            onDismiss = { showAlphabetFilter = false },
        )
    }

    // Date picker — future dates are disabled, never backdate past "today"'s max
    if (showDatePicker) {
        MonthPickerDialog(
            current = transactionDate,
            maxDate = today,
            onConfirm = { picked ->
                transactionDate = picked
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }

    // Hold return-date picker — unlike the transaction date, this is meant to
    // be in the future, so no maxDate cap is passed.
    if (showHoldDatePicker) {
        MonthPickerDialog(
            current = holdReturnDate,
            onConfirm = { picked -> holdReturnDate = picked; showHoldDatePicker = false },
            onDismiss = { showHoldDatePicker = false },
        )
    }
    }
}

// ── Income / Expense toggle ──────────────────────────────────────────────────
@Composable
private fun TypeToggle(
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    Row(
        modifier  = modifier
            .clip(RoundedCornerShape(40.dp))
            .background(NeutralWhite.copy(alpha = 0.20f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        TransactionType.entries.forEach { t ->
            val isSelected = selected == t
            val bgColor by animateColorAsState(
                targetValue   = if (isSelected) NeutralWhite else Color.Transparent,
                animationSpec = tween(220),
                label         = "toggle_bg",
            )
            val textColor by animateColorAsState(
                targetValue   = if (isSelected) {
                    if (t == TransactionType.INCOME) SemanticSuccess else SemanticError
                } else NeutralWhite.copy(alpha = 0.70f),
                animationSpec = tween(220),
                label         = "toggle_text",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(36.dp))
                    .background(bgColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = ripple(bounded = true),
                        onClick           = { onSelect(t) },
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = if (t == TransactionType.INCOME) "Income" else "Expense",
                    color      = textColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize   = 14.sp,
                )
            }
        }
    }
}

// ── Category chip ────────────────────────────────────────────────────────────
@Composable
private fun CategoryChip(
    category: Category,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val bgColor by animateColorAsState(
        targetValue   = if (isSelected) color else NeutralSurfaceVariant,
        animationSpec = tween(200),
        label         = "cat_bg",
    )
    val contentColor by animateColorAsState(
        targetValue   = if (isSelected) NeutralWhite else NeutralMid,
        animationSpec = tween(200),
        label         = "cat_content",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true, color = color),
                onClick           = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier         = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            if (category.iconId != null) {
                AsyncImage(
                    model = IconifyApiClient.iconUrl(
                        category.iconId,
                        colorHex = if (isSelected) "#FFFFFF" else "#6B7280",
                    ),
                    contentDescription = category.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    imageVector        = category.icon ?: Icons.Default.Star,
                    contentDescription = category.label,
                    tint               = contentColor,
                    modifier           = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text      = category.label,
            fontSize  = 11.sp,
            color     = if (isSelected) NeutralBlack else NeutralMid,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines  = 1,
        )
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text       = text,
        fontSize   = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color      = NeutralMid,
    )
}

// ── Date selector row — opens the calendar, future dates are disabled ────────
@Composable
private fun DateSelectorRow(
    date: LocalDate,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val today = remember { LocalDate.now() }
    val label = remember(date) {
        when (date) {
            today            -> "Today"
            today.minusDays(1) -> "Yesterday"
            else              -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accentColor.copy(alpha = 0.08f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true, color = accentColor),
                onClick           = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector        = Icons.Default.CalendarMonth,
            contentDescription = null,
            tint               = accentColor,
            modifier           = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text       = label,
            fontSize   = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color      = NeutralBlack,
        )
    }
}

// ── A-Z category filter sheet ────────────────────────────────────────────────
// A shortcut alongside the scrollable grid — lets the user jump straight to
// one or more letters instead of scrolling through a long list. Letters with
// no matching category are shown but disabled,
// not hidden, so the alphabet always reads as a complete, stable reference.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryAlphabetFilterSheet(
    availableLetters: Set<Char>,
    selectedLetters: Set<Char>,
    matchCount: Int,
    accentColor: Color,
    onToggle: (Char) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NeutralWhite,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = "Filter by letter",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NeutralBlack,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Pick one or more letters — the category list updates as you go",
                fontSize = 12.sp,
                color = NeutralMid,
            )
            Spacer(Modifier.height(20.dp))

            // Rows of equal-width cells (via weight) so the grid always spans
            // the sheet's full width edge to edge — no leftover gap on the
            // right like a fixed-size FlowRow leaves on rows that don't
            // divide evenly. The trailing row is padded with blank weighted
            // spacers (not stretched letters) so every circle stays the same size.
            val lettersPerRow = 6
            val letterRows = remember { ('A'..'Z').toList().chunked(lettersPerRow) }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                letterRows.forEach { rowLetters ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowLetters.forEach { letter ->
                            val hasMatches = letter in availableLetters
                            val isSelected = letter in selectedLetters
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> accentColor
                                            hasMatches -> NeutralSurfaceVariant
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable(enabled = hasMatches) { onToggle(letter) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = letter.toString(),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = when {
                                        isSelected -> NeutralWhite
                                        hasMatches -> NeutralBlack
                                        else -> NeutralMid.copy(alpha = 0.35f)
                                    },
                                )
                            }
                        }
                        repeat(lettersPerRow - rowLetters.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Live feedback — updates the instant a letter is toggled, without
            // needing to close the sheet to see the effect.
            Text(
                text = if (selectedLetters.isEmpty()) {
                    "Showing your top categories"
                } else {
                    "$matchCount ${if (matchCount == 1) "category matches" else "categories match"}"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = NeutralMid,
            )

            if (selectedLetters.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeutralSurfaceVariant)
                        .clickable(onClick = onClearAll)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Clear letters",
                        color = NeutralBlack,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
