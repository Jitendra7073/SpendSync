package com.example.spendsync.ui.search

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.theme.BrandBlue

private data class SettingsSearchItem(
    val label: String,
    val keywords: List<String>,
    val icon: ImageVector,
)

private val SETTINGS_ITEMS = listOf(
    SettingsSearchItem("Dark Mode", listOf("theme", "appearance", "night"), Icons.Default.DarkMode),
    SettingsSearchItem("Push Notifications", listOf("alerts"), Icons.Default.NotificationsActive),
    SettingsSearchItem("Auto Backup", listOf("backup", "sync"), Icons.Default.Backup),
    SettingsSearchItem("Accent Color", listOf("color", "theme"), Icons.Default.ColorLens),
    SettingsSearchItem("Language", listOf("locale", "translation"), Icons.Default.Language),
    SettingsSearchItem("Currency", listOf("money"), Icons.Default.CurrencyExchange),
    SettingsSearchItem("Date Format", listOf("date"), Icons.Default.CalendarMonth),
    SettingsSearchItem("Security PIN", listOf("pin", "passcode"), Icons.Default.Lock),
    SettingsSearchItem("Biometric Lock", listOf("fingerprint", "face"), Icons.Default.Fingerprint),
    SettingsSearchItem("Privacy Policy", listOf("legal", "data"), Icons.Default.PrivacyTip),
    SettingsSearchItem("Support & FAQs", listOf("help", "contact"), Icons.AutoMirrored.Filled.HelpOutline),
    SettingsSearchItem("Sign Out", listOf("logout", "log out"), Icons.AutoMirrored.Filled.Logout),
)

/**
 * Full-screen modal that searches across the whole app — transactions (by
 * merchant/category/note) and Settings items (by label/keyword). Opened from
 * the Home top bar. Tapping a transaction opens its detail dialog on Home;
 * tapping a settings item jumps straight to the Settings screen.
 */
@Composable
fun GlobalSearchDialog(
    financeRepository: FinanceRepository,
    onDismiss: () -> Unit,
    onTransactionSelected: (TransactionDto) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant

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

    val matchingSettings = remember(query) {
        if (query.isBlank()) emptyList() else SETTINGS_ITEMS.filter { item ->
            item.label.contains(query, ignoreCase = true) ||
                item.keywords.any { it.contains(query, ignoreCase = true) }
        }
    }

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
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search",
                        tint = NeutralBlack,
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search transactions, settings...", color = NeutralMid, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeutralMid) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = NeutralMid)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NeutralWhite,
                        unfocusedContainerColor = NeutralWhite,
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = Color(0xFFE2E8F0),
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
                        text = "Search transactions and settings",
                        fontSize = 14.sp,
                        color = NeutralMid,
                    )
                }
                isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    CircularProgressIndicator(color = BrandBlue)
                }
                matchingSettings.isEmpty() && matchingTransactions.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = "No results for \"$query\"",
                        fontSize = 14.sp,
                        color = NeutralMid,
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (matchingSettings.isNotEmpty()) {
                        item { SectionHeader("Settings") }
                        items(matchingSettings) { settingsItem ->
                            SettingsResultRow(settingsItem, onClick = onOpenSettings)
                        }
                    }
                    if (matchingTransactions.isNotEmpty()) {
                        item { SectionHeader("Transactions") }
                        items(matchingTransactions) { tx ->
                            TransactionResultRow(tx) { onTransactionSelected(tx) }
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
private fun SettingsResultRow(item: SettingsSearchItem, onClick: () -> Unit) {
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
                .background(BrandBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text = item.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = NeutralBlack)
    }
}

@Composable
private fun TransactionResultRow(transaction: TransactionDto, onClick: () -> Unit) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val isCredit = transaction.type == "credit"
    val categoryIcon = when (transaction.category) {
        "Salary", "Freelance", "Side Income" -> Icons.Default.Savings
        "Groceries", "Shopping" -> Icons.Default.ShoppingCart
        "Rent", "Bills" -> Icons.Default.Home
        else -> Icons.Default.Star
    }

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
                .background(if (isCredit) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                categoryIcon,
                contentDescription = null,
                tint = if (isCredit) Color(0xFF15803D) else Color(0xFF475569),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchant.ifBlank { transaction.category },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = NeutralBlack,
            )
            Text(text = transaction.category, fontSize = 12.sp, color = NeutralMid)
        }
        Text(
            text = "${if (isCredit) "+" else "-"} ${transaction.amount.toDoubleOrNull() ?: 0.0}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isCredit) Color(0xFF16A34A) else Color(0xFFDC2626),
        )
    }
}
