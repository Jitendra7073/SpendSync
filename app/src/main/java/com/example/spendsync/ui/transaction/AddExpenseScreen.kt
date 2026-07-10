package com.example.spendsync.ui.transaction

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.window.DialogProperties
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.NeutralBlack
import com.example.spendsync.ui.theme.NeutralLight
import com.example.spendsync.ui.theme.NeutralMid
import com.example.spendsync.ui.theme.NeutralOffWhite
import com.example.spendsync.ui.theme.NeutralWhite
import com.example.spendsync.ui.theme.SemanticError
import com.example.spendsync.ui.theme.SemanticSuccess

// ─────────────────────────────────────────────────────────────────────────────
//  Data
// ─────────────────────────────────────────────────────────────────────────────

private enum class TransactionType { INCOME, EXPENSE }

private data class Category(
    val label: String,
    val icon: ImageVector,
)

private val incomeCategories = listOf(
    Category("Salary",     Icons.Default.Work),
    Category("Freelance",  Icons.Default.AttachMoney),
    Category("Business",   Icons.Default.Home),
    Category("Gift",       Icons.Default.CardGiftcard),
    Category("Investment", Icons.Default.AttachMoney),
    Category("Other",      Icons.Default.MoreHoriz),
)

private val expenseCategories = listOf(
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
    Category("Other",       Icons.Default.MoreHoriz),
)

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AddExpenseScreen(
    sessionDataStore: SessionDataStore,
    financeRepository: FinanceRepository,
    editTransaction: TransactionDto? = null,
    onBack: () -> Unit
) {
    val isEditing = editTransaction != null
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
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

    var type        by rememberSaveable {
        mutableStateOf(if (editTransaction?.type == "credit") TransactionType.INCOME else TransactionType.EXPENSE)
    }
    var amount      by rememberSaveable { mutableStateOf(editTransaction?.amount ?: "") }
    var selectedCat by rememberSaveable { mutableStateOf(editTransaction?.category) }
    var note        by rememberSaveable { mutableStateOf(editTransaction?.note ?: "") } // Unifies description/note

    // Mutable states for lists of categories to allow adding custom ones
    var customIncomeCategories by remember { mutableStateOf(incomeCategories) }
    var customExpenseCategories by remember { mutableStateOf(expenseCategories) }

    // If editing a transaction whose category isn't one of the built-ins,
    // inject it so the grid can show it as an existing, selected chip.
    LaunchedEffect(Unit) {
        val editCategory = editTransaction?.category ?: return@LaunchedEffect
        if (editTransaction.type == "credit") {
            if (customIncomeCategories.none { it.label == editCategory }) {
                customIncomeCategories = customIncomeCategories + Category(editCategory, Icons.Default.Star)
            }
        } else {
            if (customExpenseCategories.none { it.label == editCategory }) {
                customExpenseCategories = customExpenseCategories + Category(editCategory, Icons.Default.Star)
            }
        }
    }

    // Restore previously-added custom categories (persisted locally — icons
    // aren't serializable, so restored entries get a generic star icon).
    val savedIncomeNames by sessionDataStore.customIncomeCategoryNames.collectAsState(initial = emptyList())
    val savedExpenseNames by sessionDataStore.customExpenseCategoryNames.collectAsState(initial = emptyList())
    LaunchedEffect(savedIncomeNames) {
        val missing = savedIncomeNames.filter { name -> customIncomeCategories.none { it.label == name } }
        if (missing.isNotEmpty()) {
            customIncomeCategories = customIncomeCategories + missing.map { Category(it, Icons.Default.Star) }
        }
    }
    LaunchedEffect(savedExpenseNames) {
        val missing = savedExpenseNames.filter { name -> customExpenseCategories.none { it.label == name } }
        if (missing.isNotEmpty()) {
            customExpenseCategories = customExpenseCategories + missing.map { Category(it, Icons.Default.Star) }
        }
    }

    val categories  = if (type == TransactionType.INCOME) customIncomeCategories else customExpenseCategories

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
                        categories.any { it.label == suggested }
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

    // Dialog state for adding a custom category
    var showAddCategory by remember { mutableStateOf(false) }

    // Combine current category list with a special "+" Add button item
    val gridItems = remember(categories) {
        categories + Category("+ Add", Icons.Default.Add)
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
                    fontSize   = 20.sp,
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
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text       = currencySymbol,
                        color      = NeutralWhite,
                        fontSize   = 36.sp,
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
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        textStyle     = androidx.compose.ui.text.TextStyle(
                            color      = NeutralWhite,
                            fontSize   = 42.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor          = NeutralWhite,
                        ),
                        singleLine    = true,
                        modifier      = Modifier.weight(1f),
                    )
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
            }

            Spacer(Modifier.height(12.dp))
        }

        // ── Scrollable body ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Category label ────────────────────────────────────────────────
            SectionLabel("Category")
            Spacer(Modifier.height(12.dp))

            // Grid displaying existing categories + the special "+" Chip at the end
            LazyVerticalGrid(
                columns             = GridCells.Fixed(4),
                userScrollEnabled   = false,
                contentPadding      = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                modifier            = Modifier
                    .fillMaxWidth()
                    .height(
                        (((gridItems.size + 3) / 4) * 90).dp
                    ),
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

            Spacer(Modifier.height(32.dp))

            // ── Save button ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = ripple(bounded = true, color = NeutralWhite),
                        onClick           = {
                            val amountVal = amount.toDoubleOrNull() ?: 0.0
                            if (amountVal > 0.0 && !selectedCat.isNullOrBlank()) {
                                scope.launch {
                                     val apiType = if (type == TransactionType.INCOME) "credit" else "debit"
                                     val merchantName = if (note.isNotBlank()) note else selectedCat ?: "Other"
                                     val chosenCategory = selectedCat ?: "Other"
                                     val res = if (isEditing) {
                                         financeRepository.updateTransaction(
                                             id = editTransaction.id,
                                             amount = amountVal,
                                             type = apiType,
                                             merchant = merchantName,
                                             category = chosenCategory,
                                             note = note
                                         )
                                     } else {
                                         financeRepository.createTransaction(
                                             amount = amountVal,
                                             type = apiType,
                                             merchant = merchantName,
                                             category = chosenCategory,
                                             note = note
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

            Spacer(Modifier.height(40.dp))
        }
    }

    // Custom Category Creator Dialog
    if (showAddCategory) {
        AddCategoryDialog(
            onDismiss = { showAddCategory = false },
            onAdd = { name, icon ->
                val newCat = Category(name, icon)
                if (type == TransactionType.INCOME) {
                    customIncomeCategories = customIncomeCategories + newCat
                    scope.launch { sessionDataStore.addCustomIncomeCategory(name) }
                } else {
                    customExpenseCategories = customExpenseCategories + newCat
                    scope.launch { sessionDataStore.addCustomExpenseCategory(name) }
                }
                selectedCat = name // Automatically select the newly created category
                showAddCategory = false
            },
            accentColor = accentColor
        )
    }
    }
}

// ── Custom Category Add Dialog with Searchable Icon Picker ───────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, ImageVector) -> Unit,
    accentColor: Color,
) {
    var name by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf<ImageVector?>(null) }

    // Curated standard icons that compile safely in core Material package
    val availableIcons = remember {
        listOf(
            "Food" to Icons.Default.Fastfood,
            "Transport" to Icons.Default.DirectionsCar,
            "Shopping" to Icons.Default.ShoppingBag,
            "Housing" to Icons.Default.Home,
            "Health" to Icons.Default.LocalHospital,
            "Education" to Icons.Default.School,
            "Travel" to Icons.Default.Flight,
            "Bills/Wifi" to Icons.Default.Wifi,
            "Fitness" to Icons.Default.FitnessCenter,
            "Movies" to Icons.Default.MovieFilter,
            "Work" to Icons.Default.Work,
            "Gift" to Icons.Default.CardGiftcard,
            "Other" to Icons.Default.MoreHoriz,
            "Cart" to Icons.Default.ShoppingCart,
            "Star" to Icons.Default.Star,
            "Phone" to Icons.Default.Phone,
            "Tools" to Icons.Default.Build,
            "Email" to Icons.Default.Email,
            "Call" to Icons.Default.Call,
            "Settings" to Icons.Default.Settings,
            "Person" to Icons.Default.Person,
            "Lock" to Icons.Default.Lock,
            "Warning" to Icons.Default.Warning,
            "Info" to Icons.Default.Info,
            "Favorite" to Icons.Default.Favorite,
            "Savings" to Icons.Default.Savings
        )
    }

    val filteredIcons = remember(searchQuery) {
        availableIcons.filter { it.first.contains(searchQuery, ignoreCase = true) }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(NeutralWhite)
                .padding(20.dp),
        ) {
            Text(
                text       = "Add Custom Category",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = NeutralBlack,
            )
            Spacer(Modifier.height(16.dp))

            // Category Name Field
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Category Name", fontSize = 14.sp) },
                placeholder   = { Text("e.g. Subscriptions", color = NeutralMid) },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = NeutralLight,
                    cursorColor          = accentColor,
                    focusedLabelColor    = accentColor,
                ),
                shape         = RoundedCornerShape(12.dp),
                modifier      = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text       = "Select Icon",
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = NeutralMid,
            )
            Spacer(Modifier.height(8.dp))

            // Search Icons Input
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder   = { Text("Search icons...", color = NeutralMid, fontSize = 13.sp) },
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, tint = NeutralMid, modifier = Modifier.size(18.dp)) },
                trailingIcon  = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = NeutralMid, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = NeutralLight,
                    cursorColor          = accentColor,
                ),
                shape         = RoundedCornerShape(12.dp),
                modifier      = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Icons selector grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                if (filteredIcons.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = "No icons match search.",
                            color = NeutralMid,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns             = GridCells.Fixed(5),
                        contentPadding      = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(8.dp),
                        modifier            = Modifier.fillMaxSize(),
                    ) {
                        items(filteredIcons) { (iconLabel, iconVector) ->
                            val isIconSelected = selectedIcon == iconVector
                            val iconBg = if (isIconSelected) accentColor else NeutralLight
                            val iconTint = if (isIconSelected) NeutralWhite else NeutralMid
                            
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(iconBg)
                                    .clickable { selectedIcon = iconVector }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = iconLabel,
                                    tint               = iconTint,
                                    modifier           = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NeutralLight),
            )

            Spacer(Modifier.height(12.dp))

            // Confirm & Cancel Actions
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = "Cancel",
                        color      = NeutralMid,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.width(8.dp))

                val isEnabled = name.trim().isNotEmpty() && selectedIcon != null
                val addBg = if (isEnabled) accentColor else NeutralLight
                val addText = if (isEnabled) NeutralWhite else NeutralMid

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(addBg)
                        .clickable(enabled = isEnabled) {
                            onAdd(name.trim(), selectedIcon!!)
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = "Add",
                        color      = addText,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
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
    val bgColor by animateColorAsState(
        targetValue   = if (isSelected) color else NeutralLight,
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
            Icon(
                imageVector        = category.icon,
                contentDescription = category.label,
                tint               = contentColor,
                modifier           = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text      = category.label,
            fontSize  = 10.sp,
            color     = if (isSelected) NeutralBlack else NeutralMid,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines  = 1,
        )
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Text(
        text       = text,
        fontSize   = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color      = NeutralMid,
    )
}
