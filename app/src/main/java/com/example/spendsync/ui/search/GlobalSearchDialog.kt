package com.example.spendsync.ui.search

import com.example.spendsync.ui.i18n.categoryLabel
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import com.example.spendsync.ui.components.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.example.spendsync.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.IconifyApiClient
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.transaction.builtInCategoryIcon
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.MaskableAmountText
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppIconButton
import com.example.spendsync.ui.components.ButtonSize

/**
 * Full-screen modal that searches across the whole app — transactions (by
 * merchant/category/note) and Settings items (by label/keyword). Opened from
 * the Home top bar. Tapping a transaction opens its detail dialog on Home;
 * tapping a settings item jumps to the Profile tab and scrolls to it.
 */
@Composable
fun GlobalSearchDialog(
    financeRepository: FinanceRepository,
    sessionDataStore: SessionDataStore,
    amountVisibility: AmountVisibilityState,
    onDismiss: () -> Unit,
    onTransactionSelected: (TransactionDto) -> Unit,
    onNavigate: (SearchDest) -> Unit,
) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant

    // Same cross-reference HomeScreen uses — a transaction only carries the
    // category name, so a custom category's icon has to be looked up here.
    val customIncomeCategories by sessionDataStore.customIncomeCategories.collectAsState(initial = emptyList())
    val customExpenseCategories by sessionDataStore.customExpenseCategories.collectAsState(initial = emptyList())
    val customCategoryIcons = remember(customIncomeCategories, customExpenseCategories) {
        (customIncomeCategories + customExpenseCategories)
            .mapNotNull { cat -> cat.iconId?.let { cat.name to it } }
            .toMap()
    }

    var query by remember { mutableStateOf("") }
    var transactions by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    // Broad, uncached-by-date fetch — relies on FinanceRepository's own cache
    // so re-opening search doesn't re-hit the network unless data changed.
    LaunchedEffect(Unit) {
        when (val res = financeRepository.getTransactions(limit = 200)) {
            is AuthResult.Success -> transactions = res.data
            is AuthResult.Error -> Unit
        }
        isLoading = false
        focusRequester.requestFocus()
    }

    val matchingTransactions = remember(query, transactions) {
        if (query.isBlank()) emptyList() else transactions.filter { tx ->
            tx.merchant.contains(query, ignoreCase = true) ||
                tx.category.contains(query, ignoreCase = true) ||
                (tx.note ?: "").contains(query, ignoreCase = true)
        }.take(20)
    }

    val entries = remember { appSearchEntries() }
    val matchingSettings = remember(query) { searchApp(entries, query).take(30) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NeutralOffWhite),
        ) {
            // ── Search bar ────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.close_search), onClick = onDismiss)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(tr(R.string.search_transactions_settings), color = NeutralMid, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeutralMid) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            AppIconButton(Icons.Default.Close, tr(R.string.clear_search), onClick = { query = "" }, tint = NeutralMid)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NeutralWhite,
                        unfocusedContainerColor = NeutralWhite,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
            }

            when {
                query.isBlank() -> Box(
                    modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = tr(R.string.search_transactions_and_settings),
                        fontSize = 14.sp,
                        color = NeutralMid,
                    )
                }
                isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                matchingSettings.isEmpty() && matchingTransactions.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = tr(R.string.no_results_for_1, query),
                        fontSize = 14.sp,
                        color = NeutralMid,
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (matchingSettings.isNotEmpty()) {
                        item { SectionHeader(tr(R.string.search_in_app)) }
                        items(matchingSettings) { entry ->
                            SettingsResultRow(entry, onClick = { onNavigate(entry.dest) })
                        }
                    }
                    if (matchingTransactions.isNotEmpty()) {
                        item { SectionHeader(tr(R.string.transactions)) }
                        items(matchingTransactions) { tx ->
                            TransactionResultRow(tx, customCategoryIcons, amountVisibility) { onTransactionSelected(tx) }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = NeutralMid,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun SettingsResultRow(item: AppSearchEntry, onClick: () -> Unit) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = NeutralBlack)
            Text(text = item.where, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransactionResultRow(
    transaction: TransactionDto,
    customCategoryIcons: Map<String, String>,
    amountVisibility: AmountVisibilityState,
    onClick: () -> Unit,
) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val isCredit = transaction.type == "credit"
    val iconTint = if (isCredit) com.example.spendsync.ui.theme.incomeColor() else com.example.spendsync.ui.theme.expenseColor()
    val customIconId = customCategoryIcons[transaction.category]

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (customIconId != null) {
                AsyncImage(
                    model = IconifyApiClient.iconUrl(
                        customIconId,
                        colorHex = "#%06X".format(0xFFFFFF and iconTint.toArgb()),
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    builtInCategoryIcon(transaction.category) ?: Icons.Default.Star,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchant.ifBlank { categoryLabel(transaction.category) },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = NeutralBlack,
            )
            Text(text = categoryLabel(transaction.category), fontSize = 12.sp, color = NeutralMid)
        }
        MaskableAmountText(
            amount = transaction.amount.toDoubleOrNull() ?: 0.0,
            visibility = amountVisibility,
            prefix = if (isCredit) "+ " else "- ",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = iconTint,
        )
    }
}
