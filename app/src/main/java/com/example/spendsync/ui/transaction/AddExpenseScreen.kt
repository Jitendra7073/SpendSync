package com.example.spendsync.ui.transaction

import com.example.spendsync.ui.i18n.categoryLabel
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
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
import com.example.spendsync.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.example.spendsync.ui.components.Text
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppChip
import com.example.spendsync.ui.components.AppIconButton
import com.example.spendsync.ui.components.AppSheet
import com.example.spendsync.ui.components.AppTextField
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.settings.SettingsBackdrop
import com.example.spendsync.ui.settings.SettingsContentWidth
import com.example.spendsync.ui.settings.SettingsTopBar
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
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
    // Back closes this screen (and only this screen); the icon picker below installs its own handler on top.
    androidx.activity.compose.BackHandler(onBack = onBack)
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
    var saving by remember { mutableStateOf(false) }

    // The month's plan, so the category card can show what is left in that bucket (cached; cheap).
    var plan by remember { mutableStateOf<com.example.spendsync.data.remote.model.PlanViewDto?>(null) }
    val planMonth = com.example.spendsync.data.planify.PlanMath.monthKey(transactionDate)
    LaunchedEffect(planMonth) {
        plan = (financeRepository.getPlan(planMonth, today.toString()) as? AuthResult.Success)?.data?.takeIf { it.exists }
    }

    var expectReturn by remember { mutableStateOf(false) }
    // Bill pages picked before the transaction exists; queued for upload right after it is saved.
    var draftBills by remember { mutableStateOf<List<com.example.spendsync.ui.bills.Picked>>(emptyList()) }
    var pickingBills by remember { mutableStateOf(false) }
    var holdPersonName by remember { mutableStateOf("") }
    // Picking from contacts fills the name and keeps the number on this phone (for follow-up messages later).
    val holdContactPicker = com.example.spendsync.ui.contacts.rememberContactPicker { picked ->
        holdPersonName = picked.name
        scope.launch { sessionDataStore.saveHoldContact(picked.name, picked.name, picked.phone, picked.email) }
    }
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
                .filter { categoryLabel(it.label).firstOrNull()?.uppercaseChar() in selectedFilterLetters }
                .sortedBy { categoryLabel(it.label) }
        }
    }

    // Which starting letters actually have a category — disables dead letters
    // in the A-Z sheet instead of showing 26 options where most do nothing.
    val availableFilterLetters = remember(categoryPool) {
        categoryPool.mapNotNull { categoryLabel(it.label).firstOrNull()?.uppercaseChar() }.toSet()
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
    // Theme-aware green / red — readable on the surface in light and dark.
    val accentColor = if (type == TransactionType.INCOME) incomeColor() else expenseColor()

    // Add-category screen state
    var showAddCategory by remember { mutableStateOf(false) }
    val iconifyRepository = remember { IconifyRepository() }

    // Combine the displayed category list with a special "+" Add button item
    val gridItems = remember(displayedCategories) {
        displayedCategories + Category("+ Add", Icons.Default.Add)
    }

    val canSave = amountVal > 0.0 && !selectedCat.isNullOrBlank()

    fun save() {
        if (transactionDate.isAfter(LocalDate.now())) {
            toast = ToastMessage(tr(R.string.transaction_date_cannot_be_in_the), isError = true)
            return
        }
        if (insufficientBalance) {
            // Plain toast text can't carry a tap-to-unlock eye, so this figure stays unmasked on purpose.
            toast = ToastMessage(
                tr(R.string.insufficient_balance_have, "$currencySymbol${"%,.2f".format(availableForThisTransaction.coerceAtLeast(0.0))}"),
                isError = true,
            )
            return
        }
        if (!isEditing && expectReturn && holdPersonName.isBlank()) {
            toast = ToastMessage(tr(R.string.enter_who_this_is_with_or), isError = true)
            return
        }
        if (!canSave) return
        scope.launch {
            saving = true
            val apiType = if (type == TransactionType.INCOME) "credit" else "debit"
            val merchantName = if (note.isNotBlank()) note else selectedCat ?: "Other"
            val chosenCategory = selectedCat ?: "Other"
            val dateStr = "${transactionDate}T00:00:00.000Z"
            val res = if (isEditing) {
                financeRepository.updateTransaction(
                    id = editTransaction!!.id, amount = amountVal, type = apiType, merchant = merchantName,
                    category = chosenCategory, note = note, transactionDate = dateStr,
                )
            } else {
                financeRepository.createTransaction(
                    amount = amountVal, type = apiType, merchant = merchantName,
                    category = chosenCategory, note = note, transactionDate = dateStr,
                )
            }
            // Teach the suggestion engine: next time this merchant is typed, suggestCategory()
            // will offer this category. Own coroutine so it never delays navigating back.
            if (note.isNotBlank()) {
                val keyword = note.trim().lowercase()
                scope.launch { financeRepository.createCategory(keyword, chosenCategory) }
            }
            if (res is AuthResult.Success) {
                if (!isEditing && draftBills.isNotEmpty()) {
                    // Outlives this screen: the pages are copied and queued after it closes.
                    val appContext = context.applicationContext
                    val picked = draftBills
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        val prepared = picked.mapNotNull { com.example.spendsync.data.bills.BillImages.prepare(appContext, it.uri, it.mime).getOrNull() }
                        com.example.spendsync.data.bills.BillQueue.enqueuePrepared(appContext, res.data.id, prepared)
                    }
                }
                // Outlives this screen, so the alert still goes out after it closes.
                val appContext = context.applicationContext
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    com.example.spendsync.data.planify.PlanAlerts.onTransactionChanged(appContext, financeRepository, sessionDataStore, res.data)
                }
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
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
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
                        toast = ToastMessage(tr(R.string.transaction_saved_hold_failed, (holdRes as AuthResult.Error).message), isError = true)
                        delay(1500)
                        onBack()
                        return@launch
                    }
                }
                toast = ToastMessage(if (isEditing) tr(R.string.transaction_updated) else tr(R.string.transaction_added), isError = false)
                delay(500)
                onBack()
            } else {
                saving = false
                toast = ToastMessage((res as AuthResult.Error).message, isError = true)
            }
        }
    }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
    SettingsBackdrop {
    Column(Modifier.fillMaxSize()) {
        SettingsTopBar(if (isEditing) tr(R.string.edit_transaction) else tr(R.string.add_transaction), onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SettingsContentWidth {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // ── Type ─────────────────────────────────────────────────
                    TypeToggle(selected = type, onSelect = { type = it; selectedCat = null })

                    // ── Amount ───────────────────────────────────────────────
                    FormCard {
                        Text(tr(R.string.how_much), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currencySymbol, color = accentColor, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { v ->
                                    val filtered = v.filter { it.isDigit() || it == '.' }
                                    if (filtered.count { it == '.' } <= 1) amount = filtered
                                },
                                placeholder = { Text("0.00", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 32.sp, fontWeight = FontWeight.Bold) },
                                textStyle = androidx.compose.ui.text.TextStyle(color = accentColor, fontSize = 32.sp, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = accentColor,
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f).focusRequester(amountFocusRequester),
                            )
                        }
                        // Live warning — updates as you type instead of failing after Save.
                        AnimatedVisibility(insufficientBalance) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr(R.string.not_enough_balance_you_have), color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                MaskableAmountText(
                                    amount = availableForThisTransaction.coerceAtLeast(0.0),
                                    visibility = amountVisibility,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    // ── Details ──────────────────────────────────────────────
                    FormCard {
                        AppTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = tr(R.string.what_was_it_for_optional),
                            placeholder = tr(R.string.e_g_lunch_salary_uber),
                            leadingIcon = Icons.Default.Edit,
                        )
                        Spacer(Modifier.height(12.dp))
                        DateSelectorRow(label = tr(R.string.date), date = transactionDate, accentColor = accentColor, onClick = { showDatePicker = true })
                        Spacer(Modifier.height(12.dp))
                        if (isEditing) {
                            com.example.spendsync.ui.bills.BillsSection(editTransaction!!.id, financeRepository, amountVisibility)
                        } else {
                            Text(tr(R.string.bills_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(8.dp))
                            if (draftBills.isEmpty()) {
                                AppButton(tr(R.string.bills_attach), onClick = { pickingBills = true }, variant = ButtonVariant.Tonal, leadingIcon = Icons.Default.AttachFile)
                            } else {
                                com.example.spendsync.ui.bills.BillStrip(
                                    tiles = emptyList(), canAdd = draftBills.size < 5, masked = false,
                                    onAdd = { pickingBills = true }, onOpen = {}, onRetry = {}, onRemove = {},
                                    localPreview = draftBills,
                                )
                            }
                        }

                        // A hold can only be attached to a NEW transaction.
                        if (!isEditing) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .toggleable(value = expectReturn, role = Role.Switch, onValueChange = { expectReturn = it }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (type == TransactionType.EXPENSE) tr(R.string.expect_this_back) else tr(R.string.need_to_pay_this_back),
                                        fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(tr(R.string.we_ll_track_it_in_holds), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = expectReturn, onCheckedChange = null)
                            }
                            AnimatedVisibility(expectReturn) {
                                Column(Modifier.padding(top = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AppTextField(holdPersonName, { holdPersonName = it }, label = tr(R.string.who_is_it_with), leadingIcon = Icons.Default.Person, modifier = Modifier.weight(1f))
                                        AppIconButton(Icons.Default.Contacts, tr(R.string.fu_pick_contact), onClick = { holdContactPicker.pickPhone() }, tint = accentColor)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    DateSelectorRow(label = tr(R.string.expected_back_by), date = holdReturnDate, accentColor = accentColor, onClick = { showHoldDatePicker = true })
                                }
                            }
                        }
                    }

                    // ── Category ─────────────────────────────────────────────
                    FormCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel(tr(R.string.category))
                            if (categoryPool.size > 10) {
                                AppIconButton(Icons.Default.FilterList, tr(R.string.filter_categories_by_letter), onClick = { showAlphabetFilter = true }, tint = accentColor)
                            }
                        }
                        if (selectedFilterLetters.isNotEmpty()) {
                            AppChip(
                                label = tr(R.string.letters_chip, selectedFilterLetters.sorted().joinToString(", ")),
                                selected = true,
                                onClick = { selectedFilterLetters = emptySet() },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        val rows = remember(gridItems) { gridItems.chunked(4) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rows.forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { cat ->
                                        Box(Modifier.weight(1f)) {
                                            if (cat.label == "+ Add") {
                                                CategoryChip(cat, isSelected = false, color = accentColor, onClick = { showAddCategory = true })
                                            } else {
                                                CategoryChip(cat, isSelected = selectedCat == cat.label, color = accentColor, onClick = { selectedCat = cat.label })
                                            }
                                        }
                                    }
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        if (type == TransactionType.EXPENSE) {
                            com.example.spendsync.ui.planify.PlanBucketLine(
                                plan = plan,
                                category = selectedCat,
                                amount = amountVal,
                                // editing: this transaction's own amount is already inside the bucket's spent
                                alreadyCounted = if (editTransaction != null && editTransaction.type == "debit" && editTransaction.category == selectedCat &&
                                    com.example.spendsync.data.planify.PlanMath.monthKey(transactionDate) == planMonth) (editTransaction.amount.toDoubleOrNull() ?: 0.0) else 0.0,
                                vis = amountVisibility,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // ── Save bar — always pinned to the bottom ───────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppButton(
                text = when {
                    isEditing -> tr(R.string.save_changes)
                    type == TransactionType.INCOME -> tr(R.string.add_money_in)
                    else -> tr(R.string.add_money_out)
                },
                onClick = { save() },
                modifier = Modifier.widthIn(max = 600.dp),
                size = ButtonSize.Large,
                enabled = canSave,
                loading = saving,
                fullWidth = true,
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
                selectedCat = name // select the newly created category
                justAddedCategory = name // ...and keep it pinned near the front of the list
                selectedFilterLetters = emptySet() // back to the pinned view so it's visible
                showAddCategory = false
            },
        )
    }

    // A-Z category filter — stays open while toggling letters; the grid behind filters live.
    if (showAlphabetFilter) {
        CategoryAlphabetFilterSheet(
            availableLetters = availableFilterLetters,
            selectedLetters = selectedFilterLetters,
            matchCount = displayedCategories.size,
            accentColor = accentColor,
            onToggle = { letter ->
                selectedFilterLetters = if (letter in selectedFilterLetters) selectedFilterLetters - letter else selectedFilterLetters + letter
            },
            onClearAll = { selectedFilterLetters = emptySet() },
            onDismiss = { showAlphabetFilter = false },
        )
    }

    // Future dates are disabled — never backdate past "today"'s max
    if (pickingBills) {
        com.example.spendsync.ui.bills.BillSourceSheet(
            remaining = 5 - draftBills.size,
            onPicked = { draftBills = (draftBills + it).take(5); pickingBills = false },
            onDismiss = { pickingBills = false },
        )
    }

    if (showDatePicker) {
        MonthPickerDialog(
            current = transactionDate,
            maxDate = today,
            onConfirm = { picked -> transactionDate = picked; showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }

    // A hold's return date is meant to be in the future, so no maxDate cap here.
    if (showHoldDatePicker) {
        MonthPickerDialog(
            current = holdReturnDate,
            onConfirm = { picked -> holdReturnDate = picked; showHoldDatePicker = false },
            onDismiss = { showHoldDatePicker = false },
        )
    }
    }
}

// ── Building blocks ──────────────────────────────────────────────────────────

@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().glassCard().padding(16.dp), content = content)
}

/** Money in / Money out switch with a sliding tinted thumb. */
@Composable
private fun TypeToggle(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val options = listOf(TransactionType.INCOME to tr(R.string.money_in), TransactionType.EXPENSE to tr(R.string.money_out))
    val index = options.indexOfFirst { it.first == selected }
    val tint by animateColorAsState(if (selected == TransactionType.INCOME) incomeColor() else expenseColor(), tween(250), label = "type_tint")
    BoxWithConstraints(Modifier.fillMaxWidth().glassCard(radius = 28)) {
        val cell = maxWidth / 2
        val offset by animateDpAsState(cell * index, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium), label = "type_thumb")
        Box(
            Modifier
                .padding(4.dp)
                .offset(x = offset)
                .width(cell - 8.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(tint.copy(alpha = 0.16f)),
        )
        Row(Modifier.padding(4.dp)) {
            options.forEach { (t, label) ->
                val isSel = t == selected
                val fg by animateColorAsState(if (isSel) tint else scheme.onSurfaceVariant, tween(200), label = "type_fg")
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .selectable(selected = isSel, role = Role.RadioButton, onClick = { onSelect(t) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = fg, fontSize = 15.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(category: Category, isSelected: Boolean, color: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (isSelected) color.copy(alpha = 0.16f) else scheme.surfaceVariant.copy(alpha = 0.6f), tween(200), label = "cat_bg")
    val tint by animateColorAsState(if (isSelected) color else scheme.onSurfaceVariant, tween(200), label = "cat_tint")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(bg)
                .then(if (isSelected) Modifier.border(2.dp, color, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (category.iconId != null) {
                AsyncImage(
                    model = IconifyApiClient.iconUrl(category.iconId, colorHex = "#%06X".format(0xFFFFFF and tint.toArgb())),
                    contentDescription = category.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(category.icon ?: Icons.Default.Star, contentDescription = categoryLabel(category.label), tint = tint, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            if (category.label == "+ Add") tr(R.string.cat_add) else categoryLabel(category.label),
            fontSize = 11.sp,
            color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
}

/** Field-style row that opens the calendar. The label sits above the chosen date. */
@Composable
private fun DateSelectorRow(label: String, date: LocalDate, accentColor: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    val text = remember(date) {
        when (date) {
            today -> tr(R.string.today)
            today.minusDays(1) -> tr(R.string.yesterday)
            else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 12.sp, color = scheme.onSurfaceVariant)
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        }
    }
}

/** Pick one or more starting letters; the category list behind updates live. */
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
    val scheme = MaterialTheme.colorScheme
    AppSheet(onDismiss = onDismiss, title = tr(R.string.find_a_category), subtitle = tr(R.string.tap_one_or_more_letters_the)) {
        val lettersPerRow = 6
        val letterRows = remember { ('A'..'Z').toList().chunked(lettersPerRow) }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            letterRows.forEach { rowLetters ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowLetters.forEach { letter ->
                        val hasMatches = letter in availableLetters
                        val isSelected = letter in selectedLetters
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .heightIn(min = 44.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> accentColor.copy(alpha = 0.20f)
                                        hasMatches -> scheme.surfaceVariant.copy(alpha = 0.7f)
                                        else -> Color.Transparent
                                    }
                                )
                                .then(if (isSelected) Modifier.border(2.dp, accentColor, CircleShape) else Modifier)
                                .clickable(enabled = hasMatches, role = Role.Button) { onToggle(letter) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                letter.toString(),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isSelected -> accentColor
                                    hasMatches -> scheme.onSurface
                                    else -> scheme.onSurface.copy(alpha = 0.30f)
                                },
                            )
                        }
                    }
                    repeat(lettersPerRow - rowLetters.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (selectedLetters.isEmpty()) tr(R.string.showing_your_most_used_categories)
            else if (matchCount == 1) tr(R.string.s_1_category_matches) else tr(R.string.s_1_categories_match, matchCount),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurfaceVariant,
        )
        if (selectedLetters.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            AppButton(tr(R.string.clear_letters), onClearAll, variant = ButtonVariant.Tonal, fullWidth = true)
        }
    }
}
