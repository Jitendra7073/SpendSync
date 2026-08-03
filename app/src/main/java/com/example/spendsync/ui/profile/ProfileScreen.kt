package com.example.spendsync.ui.profile

import android.content.Intent
import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.repository.AuthRepository
import com.example.spendsync.data.repository.CurrencyRepository
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.remote.model.DashboardSummaryDto
import androidx.compose.runtime.LaunchedEffect
import com.example.spendsync.notifications.NotificationAppAllowlist
import com.example.spendsync.ui.components.SkeletonLine
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.BrandBlueDark
import com.example.spendsync.ui.theme.BrandYellow
import com.example.spendsync.ui.theme.NeutralBlack
import com.example.spendsync.ui.theme.NeutralLight
import com.example.spendsync.ui.theme.NeutralMid
import com.example.spendsync.ui.theme.NeutralOffWhite
import com.example.spendsync.ui.theme.NeutralWhite
import com.example.spendsync.ui.theme.SemanticError
import com.example.spendsync.utils.LocalizationUtils
import com.example.spendsync.utils.TransactionExporter
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Single flat scrolling screen — account info, preferences, data controls and
 * support are all shown inline here instead of behind separate nav targets,
 * so nothing is a tap away in a nested screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    sessionDataStore: SessionDataStore,
    repository: AuthRepository,
    financeRepository: FinanceRepository,
    currencyRepository: CurrencyRepository,
    openSettingsRequestId: Int = 0,
    onSignOut: () -> Unit,
) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant

    val userName  by sessionDataStore.userName.collectAsState(initial = "")
    val userEmail by sessionDataStore.userEmail.collectAsState(initial = "")
    val userId    by sessionDataStore.userId.collectAsState(initial = "")
    val userCreatedAt by sessionDataStore.userCreatedAt.collectAsState(initial = "")
    val scope     = rememberCoroutineScope()
    val context   = LocalContext.current

    // Signed-in users sync these prefs to the backend; guests stay local-only.
    var isSignedIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isSignedIn = repository.hasLocalSession() }

    val language by sessionDataStore.language.collectAsState(initial = "English")
    val currencyCode by sessionDataStore.currency.collectAsState(initial = "INR")
    val currencySymbol = remember(currencyCode) {
        when (currencyCode) {
            "EUR" -> "€"
            "GBP" -> "£"
            "INR" -> "₹"
            "JPY" -> "¥"
            else  -> "$"
        }
    }

    // Read reactive preference flows from DataStore
    val darkMode by sessionDataStore.darkMode.collectAsState(initial = false)
    val notifications by sessionDataStore.notificationsEnabled.collectAsState(initial = true)
    val autoBackup by sessionDataStore.autoBackup.collectAsState(initial = true)
    val dateFormat by sessionDataStore.dateFormat.collectAsState(initial = "DD / MM / YYYY")
    val autoCaptureEnabled by sessionDataStore.autoCaptureEnabled.collectAsState(initial = false)
    val autoCapturePackages by sessionDataStore.autoCapturePackages.collectAsState(initial = emptySet())

    val today = remember { LocalDate.now() }

    var dashboardSummary by remember { mutableStateOf<DashboardSummaryDto?>(null) }
    var isStatsLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    suspend fun loadStats(forceRefresh: Boolean) {
        val activeMonth = LocalDate.now().toString().slice(0..6)
        when (val res = financeRepository.getDashboardSummary(month = activeMonth, forceRefresh = forceRefresh)) {
            is AuthResult.Success -> {
                dashboardSummary = res.data
            }
            is AuthResult.Error -> {
                // handle error
            }
        }
    }

    LaunchedEffect(Unit) {
        loadStats(forceRefresh = false)
        isStatsLoading = false
    }

    // Dialog / overlay state controllers
    var showEditProfile by remember { mutableStateOf(false) }
    var showDateFormatDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAutoCaptureExplainer by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: the follow-up notification just won't show if denied */ }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    // Global search's "open settings" jump scrolls straight to Preferences —
    // there's no separate screen to navigate to any more.
    val preferencesAnchor = remember { BringIntoViewRequester() }
    LaunchedEffect(openSettingsRequestId) {
        if (openSettingsRequestId > 0) preferencesAnchor.bringIntoView()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralOffWhite),
    ) {
        // ── Blue header band ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BrandBlue, BrandBlueDark),
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Avatar circle with initials
                Box(
                    modifier         = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(NeutralWhite.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = (userName?.firstOrNull() ?: "?")
                            .toString().uppercase(),
                        color      = NeutralWhite,
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text       = userName?.ifBlank { "Guest" } ?: "Guest",
                    color      = NeutralWhite,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = userEmail ?: "",
                    color    = NeutralWhite.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                )

                Spacer(Modifier.height(16.dp))

                // Yellow accent bar
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BrandYellow),
                )

                Spacer(Modifier.height(16.dp))

                // Edit profile chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(NeutralWhite.copy(alpha = 0.18f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = ripple(bounded = true, color = NeutralWhite),
                            onClick           = { showEditProfile = true },
                        )
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector        = Icons.Default.Edit,
                            contentDescription = "Edit profile",
                            tint               = NeutralWhite,
                            modifier           = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text      = "Edit Profile",
                            color     = NeutralWhite,
                            fontSize  = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // ── Dynamic stats strip ───────────────────────────────────────────────
        val totalTransactionsCount = dashboardSummary?.totals?.totalTransactions ?: 0
        val currentMonthSpent = dashboardSummary?.totals?.totalSpent ?: 0.0
        val savingsAccumulated = dashboardSummary?.totals?.netAmount ?: 0.0

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeutralWhite)
                .padding(vertical = 16.dp),
        ) {
            if (isStatsLoading) {
                repeat(3) { index ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SkeletonLine(modifier = Modifier.width(48.dp), height = 18.dp)
                        Spacer(Modifier.height(6.dp))
                        SkeletonLine(modifier = Modifier.width(64.dp), height = 11.dp)
                    }
                    if (index < 2) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(NeutralLight)
                                .align(Alignment.CenterVertically),
                        )
                    }
                }
            } else {
            StatItem(
                label  = "Transactions",
                value  = totalTransactionsCount.toString(),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(NeutralLight)
                    .align(Alignment.CenterVertically),
            )
            StatItem(
                label  = "This Month",
                value  = currencyRepository.formatAmount(currentMonthSpent, currencyCode, currencySymbol),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(NeutralLight)
                    .align(Alignment.CenterVertically),
            )
            StatItem(
                label  = "Savings",
                value  = currencyRepository.formatAmount(savingsAccumulated, currencyCode, currencySymbol),
                modifier = Modifier.weight(1f),
            )
            }
        }

        HorizontalDivider(color = NeutralLight, thickness = 1.dp)

        // ── Everything else (pull-to-refresh) ────────────────────────────────
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadStats(forceRefresh = true)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            // ── Account ──────────────────────────────────────────────────────
            SectionHeader("Account")
            val formattedJoinedDate = remember(userCreatedAt) {
                try {
                    val parsed = java.time.ZonedDateTime.parse(userCreatedAt)
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy")
                    parsed.format(formatter)
                } catch (e: Exception) {
                    if (userCreatedAt.isNullOrBlank()) "Just now" else userCreatedAt.orEmpty()
                }
            }
            ProfileMenuCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    AccountInfoRow(label = "User ID", value = (userId ?: "").ifBlank { "Guest Mode" })
                    AccountInfoRow(label = "Profile Name", value = userName?.ifBlank { "Guest" } ?: "Guest")
                    AccountInfoRow(label = "Email", value = userEmail ?: "guest@example.com")
                    AccountInfoRow(label = "Joined SpendSync", value = formattedJoinedDate)
                    AccountInfoRow(label = "Subscription Tier", value = if (isSignedIn) "Premium Account" else "Free Basic Plan")
                    AccountInfoRow(label = "Cloud Sync Status", value = if (isSignedIn) "Active / Secured" else "Offline / Not Synced")
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Preferences ──────────────────────────────────────────────────
            Column(modifier = Modifier.bringIntoViewRequester(preferencesAnchor)) {
                SectionHeader("Preferences")
            }
            ProfileMenuCard {
                SettingsToggleRow(
                    icon    = Icons.Default.DarkMode,
                    label   = LocalizationUtils.getTranslation("dark_mode", language),
                    sub     = "Switch to a dark colour theme",
                    checked = darkMode,
                    onToggle = {
                        scope.launch {
                            sessionDataStore.updateDarkMode(it)
                            if (isSignedIn) financeRepository.updateSettings(darkMode = it)
                        }
                    },
                )
                SettingsDivider()
                SettingsToggleRow(
                    icon    = Icons.Default.NotificationsActive,
                    label   = LocalizationUtils.getTranslation("push_notifications", language),
                    sub     = "Reminders and alerts",
                    checked = notifications,
                    onToggle = {
                        scope.launch {
                            sessionDataStore.updateNotifications(it)
                            if (isSignedIn) financeRepository.updateSettings(pushNotifications = it)
                        }
                    },
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon  = Icons.Default.Tune,
                    label = LocalizationUtils.getTranslation("date_format", language),
                    sub   = dateFormat,
                    onClick = { showDateFormatDialog = true }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Automation ───────────────────────────────────────────────────
            SectionHeader("Automation")
            ProfileMenuCard {
                SettingsToggleRow(
                    icon = Icons.Default.NotificationsActive,
                    label = "Auto-detect transactions",
                    sub = "Reads payment notifications from apps you choose below",
                    checked = autoCaptureEnabled,
                    onToggle = { turningOn ->
                        if (turningOn) {
                            showAutoCaptureExplainer = true
                        } else {
                            scope.launch { sessionDataStore.updateAutoCaptureEnabled(false) }
                        }
                    },
                )
                if (autoCaptureEnabled) {
                    NotificationAppAllowlist.APPS.forEach { app ->
                        SettingsDivider()
                        SettingsToggleRow(
                            icon = Icons.Default.NotificationsActive,
                            label = app.displayName,
                            checked = app.packageName in autoCapturePackages,
                            onToggle = { checked ->
                                val updated = if (checked) autoCapturePackages + app.packageName
                                    else autoCapturePackages - app.packageName
                                scope.launch { sessionDataStore.updateAutoCapturePackages(updated) }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Data ──────────────────────────────────────────────────────────
            SectionHeader("Data")
            ProfileMenuCard {
                SettingsToggleRow(
                    icon    = Icons.Default.Backup,
                    label   = LocalizationUtils.getTranslation("auto_backup", language),
                    sub     = "Back up data to the cloud daily",
                    checked = autoBackup,
                    onToggle = {
                        scope.launch {
                            sessionDataStore.updateAutoBackup(it)
                            if (isSignedIn) financeRepository.updateSettings(autoBackup = it)
                        }
                    },
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon  = Icons.Default.DataUsage,
                    label = LocalizationUtils.getTranslation("export_data", language),
                    sub   = "Download as CSV or PDF",
                    onClick = { showExportDialog = true }
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon      = Icons.Default.DeleteSweep,
                    label     = LocalizationUtils.getTranslation("clear_data", language),
                    sub       = "Clear cached data and local categories",
                    textColor = SemanticError,
                    iconTint  = SemanticError,
                    onClick = { showClearDataDialog = true }
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon  = Icons.Default.PrivacyTip,
                    label = LocalizationUtils.getTranslation("privacy_policy", language),
                    onClick = { showPrivacyDialog = true }
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon      = Icons.Default.DeleteForever,
                    label     = LocalizationUtils.getTranslation("delete_account", language),
                    sub       = "Permanently remove all data",
                    textColor = SemanticError,
                    iconTint  = SemanticError,
                    onClick = { showDeleteAccountDialog = true }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Support ───────────────────────────────────────────────────────
            SectionHeader("Support")
            ProfileMenuCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FAQItem(
                        question = "How do I back up my transactions?",
                        answer = "Auto Backup is enabled by default above. Your data is synced automatically to our secure cloud daily."
                    )
                    FAQItem(
                        question = "Is my financial data secure?",
                        answer = "Absolutely. We encrypt all transactions on-device and transit data to ensure your info stays private."
                    )
                    FAQItem(
                        question = "How to delete my account permanently?",
                        answer = "Use Delete Account under Data above. This wipes all your data permanently off our cloud databases."
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandBlue)
                            .clickable { /* Simulate email support launch */ }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Contact Email Support",
                            color = NeutralWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Logout standalone card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeutralWhite),
            ) {
                ProfileMenuItem(
                    icon      = Icons.AutoMirrored.Filled.Logout,
                    label     = "Sign Out",
                    sub       = "You can always sign back in",
                    iconTint  = SemanticError,
                    textColor = SemanticError,
                    onClick   = {
                        scope.launch {
                            repository.signOut()
                            onSignOut()
                        }
                    },
                    showChevron = false,
                )
            }

            Spacer(Modifier.height(100.dp))
        }
        }
    }

    // ── Dialog 1: Edit Profile Dialog ────────────────────────────────────────
    if (showEditProfile) {
        EditProfileDialog(
            currentName = userName ?: "",
            currentEmail = userEmail ?: "",
            onDismiss = { showEditProfile = false },
            onSave = { newName, newEmail ->
                scope.launch {
                    sessionDataStore.updateUserName(newName)
                    sessionDataStore.updateUserEmail(newEmail)
                    showEditProfile = false
                }
            }
        )
    }

    // ── Dialog 2: Date Format Dialog ────────────────────────────────────────
    if (showDateFormatDialog) {
        OptionSelectionDialog(
            title = "Select Date Format",
            options = listOf("DD / MM / YYYY", "MM / DD / YYYY", "YYYY - MM - DD"),
            selectedOption = dateFormat,
            onDismiss = { showDateFormatDialog = false },
            onSelect = {
                scope.launch {
                    sessionDataStore.updateDateFormat(it)
                    if (isSignedIn) financeRepository.updateSettings(dateFormat = it)
                    showDateFormatDialog = false
                }
            }
        )
    }

    // ── Dialog 3: Export Data Dialog ────────────────────────────────────────
    if (showExportDialog) {
        ExportDataDialog(
            financeRepository = financeRepository,
            onDismiss = { showExportDialog = false }
        )
    }

    // ── Dialog: Notification Access Explainer ───────────────────────────────
    if (showAutoCaptureExplainer) {
        BasicAlertDialog(onDismissRequest = { showAutoCaptureExplainer = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = NeutralWhite),
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Notification Access Required",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeutralBlack,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "SpendSync needs permission to read notifications so it can detect payments " +
                            "automatically. Only the apps you select below are read — nothing else, and " +
                            "nothing is sent anywhere else.",
                        fontSize = 13.sp,
                        color = NeutralMid,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAutoCaptureExplainer = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = {
                            showAutoCaptureExplainer = false
                            scope.launch { sessionDataStore.updateAutoCaptureEnabled(true) }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }) {
                            Text("Continue")
                        }
                    }
                }
            }
        }
    }

    // ── Dialog 4: Clear Data Confirmation Dialog ────────────────────────────
    if (showClearDataDialog) {
        ClearDataConfirmationDialog(
            onDismiss = { showClearDataDialog = false },
            onConfirm = {
                scope.launch {
                    sessionDataStore.clearLocalData()
                    financeRepository.clearCache()
                    showClearDataDialog = false
                }
            }
        )
    }

    // ── Dialog 5: Privacy Policy Dialog ─────────────────────────────────────
    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyDialog = false }
        )
    }

    // ── Dialog 6: Delete Account Warning Dialog ──────────────────────────────
    if (showDeleteAccountDialog) {
        DeleteAccountWarningDialog(
            onDismiss = { showDeleteAccountDialog = false },
            onDelete = {
                scope.launch {
                    sessionDataStore.clearSession()
                    showDeleteAccountDialog = false
                    onSignOut()
                }
            }
        )
    }
}

// ── Building blocks ─────────────────────────────────────────────────────────────

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text       = value,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = NeutralBlack,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text     = label,
            fontSize = 11.sp,
            color    = NeutralMid,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text      = title.uppercase(),
        fontSize  = 11.sp,
        fontWeight = FontWeight.Bold,
        color     = NeutralMid,
        modifier  = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ProfileMenuCard(content: @Composable () -> Unit) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NeutralWhite),
    ) {
        content()
    }
}

@Composable
private fun MenuDivider() {
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 16.dp),
        color     = NeutralLight,
        thickness = 0.8.dp,
    )
}

@Composable
private fun SettingsDivider() = MenuDivider()

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    label: String,
    sub: String? = null,
    iconTint: Color = BrandBlue,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                tint               = iconTint,
                modifier           = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = label,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = textColor,
            )
            if (sub != null) {
                Text(text = sub, fontSize = 12.sp, color = NeutralMid)
            }
        }
        if (showChevron) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint               = NeutralMid,
                modifier           = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    label: String,
    sub: String? = null,
    textColor: Color = Color.Unspecified,
    iconTint: Color = BrandBlue,
    onClick: () -> Unit
) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val actualTextColor = if (textColor == Color.Unspecified) NeutralBlack else textColor
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = ripple(bounded = true),
                onClick           = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                tint               = iconTint,
                modifier           = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = label,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                color      = actualTextColor,
            )
            if (sub != null) {
                Text(text = sub, fontSize = 12.sp, color = NeutralMid)
            }
        }
        Icon(
            imageVector        = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint               = NeutralMid,
            modifier           = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    sub: String? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val BrandBlue = MaterialTheme.colorScheme.primary
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BrandBlue.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                tint               = BrandBlue,
                modifier           = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = label,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                color      = NeutralBlack,
            )
            if (sub != null) {
                Text(text = sub, fontSize = 12.sp, color = NeutralMid)
            }
        }
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor       = NeutralWhite,
                checkedTrackColor       = BrandBlue,
                uncheckedThumbColor     = NeutralWhite,
                uncheckedTrackColor     = NeutralLight,
                uncheckedBorderColor    = NeutralLight,
            ),
        )
    }
}

@Composable
private fun AccountInfoRow(label: String, value: String) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = NeutralMid, fontSize = 12.sp)
        Text(text = value, color = NeutralBlack, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FAQItem(question: String, answer: String) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NeutralOffWhite)
            .clickable { expanded = !expanded }
            .padding(12.dp)
    ) {
        Text(
            text = question,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = NeutralBlack
        )
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = answer,
                fontSize = 12.sp,
                color = NeutralMid
            )
        }
    }
}

// ── Dialog Components ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileDialog(
    currentName: String,
    currentEmail: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }

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
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Edit Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack
                )

                Spacer(Modifier.height(16.dp))

                // Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", fontSize = 14.sp) },
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

                Spacer(Modifier.height(12.dp))

                // Email Input
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address", fontSize = 14.sp) },
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

                // Action buttons
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
                        text = "Save Changes",
                        color = NeutralWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandBlue)
                            .clickable(enabled = name.isNotBlank() && email.isNotBlank()) {
                                onSave(name, email)
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSelectionDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val BrandBlue = MaterialTheme.colorScheme.primary
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
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack
                )
                Spacer(Modifier.height(16.dp))

                options.forEach { option ->
                    val isSelected = option == selectedOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) BrandBlue.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { onSelect(option) }
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) BrandBlue else NeutralBlack
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(BrandBlue)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDataDialog(
    financeRepository: FinanceRepository,
    onDismiss: () -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val BrandBlue = MaterialTheme.colorScheme.primary
    var format by remember { mutableStateOf("CSV") }
    var isExporting by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                    text = "Export Transaction Data",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack
                )
                Spacer(Modifier.height(16.dp))

                if (!isExporting && !isSuccess && exportError == null) {
                    Text(
                        text = "Choose your preferred layout format below:",
                        fontSize = 12.sp,
                        color = NeutralMid
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        listOf("CSV", "PDF").forEach { fmt ->
                            val isSelected = format == fmt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) BrandBlue.copy(alpha = 0.10f) else Color.Transparent)
                                    .border(
                                        BorderStroke(1.dp, if (isSelected) BrandBlue else NeutralLight),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { format = fmt }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = fmt,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) BrandBlue else NeutralBlack
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Cancel",
                            color = NeutralMid,
                            modifier = Modifier
                                .clickable { onDismiss() }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Export Now",
                            color = NeutralWhite,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandBlue)
                                .clickable {
                                    isExporting = true
                                    scope.launch {
                                        when (val res = financeRepository.getTransactions(limit = 2000)) {
                                            is AuthResult.Success -> {
                                                val intent = if (format == "CSV") {
                                                    TransactionExporter.exportCsv(context, res.data)
                                                } else {
                                                    TransactionExporter.exportPdf(context, res.data)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Export transactions"))
                                                isExporting = false
                                                isSuccess = true
                                            }
                                            is AuthResult.Error -> {
                                                isExporting = false
                                                exportError = res.message
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                } else if (isExporting) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Compiling layout data...", color = NeutralBlack, fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(color = BrandBlue, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(16.dp))
                    }
                } else if (exportError != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Export failed",
                            fontWeight = FontWeight.Bold,
                            color = SemanticError,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = exportError.orEmpty(),
                            color = NeutralMid,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandBlue)
                                .clickable { exportError = null }
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text("Try Again", color = NeutralWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Data Export Successful!",
                            fontWeight = FontWeight.Bold,
                            color = NeutralBlack,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Your $format file is ready — choose where to save or send it.",
                            color = NeutralMid,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandBlue)
                                .clickable { onDismiss() }
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text("Done", color = NeutralWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearDataConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    var cleared by remember { mutableStateOf(false) }

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
                if (!cleared) {
                    Text(
                        text = "Clear Local Data?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SemanticError
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "This clears cached data and custom categories saved on this device. " +
                            "Your account and cloud-synced data won't be affected.",
                        fontSize = 12.sp,
                        color = NeutralMid
                    )
                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Cancel",
                            color = NeutralMid,
                            modifier = Modifier
                                .clickable { onDismiss() }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Clear Data",
                            color = NeutralWhite,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SemanticError)
                                .clickable {
                                    onConfirm()
                                    cleared = true
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Local Data Cleared",
                            fontWeight = FontWeight.Bold,
                            color = NeutralBlack,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Cached data and custom categories have been removed from this device.",
                            color = NeutralMid,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SemanticError)
                                .clickable { onDismiss() }
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text("Done", color = NeutralWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
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
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .heightIn(max = 320.dp)
            ) {
                Text(
                    text = "Privacy Policy",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. Information Collection\nWe encrypt and store all transaction data locally on your device. Selected settings and sync preferences are backed up to secure DataStore directories.",
                        fontSize = 12.sp,
                        color = NeutralMid
                    )
                    Text(
                        text = "2. Data Protection\nYour transaction statistics are completely private and never shared with third parties. Authentication sessions are managed using safe tokens.",
                        fontSize = 12.sp,
                        color = NeutralMid
                    )
                    Text(
                        text = "3. Local Storage\nCached data and custom categories can be cleared at any time from the Data section, independent of your account and cloud-synced records.",
                        fontSize = 12.sp,
                        color = NeutralMid
                    )
                }

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteAccountWarningDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    var confirmationText by remember { mutableStateOf("") }
    val isValid = confirmationText.trim().equals("DELETE", ignoreCase = false)

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
                    text = "Delete Account Permanently?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SemanticError
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "WARNING: This deletes your credentials and database logs permanently. This action cannot be undone.",
                    fontSize = 12.sp,
                    color = NeutralMid
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "To confirm, type \"DELETE\" below:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeutralBlack
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmationText,
                    onValueChange = { confirmationText = it },
                    placeholder = { Text("Type DELETE here", color = NeutralMid, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SemanticError,
                        unfocusedBorderColor = NeutralLight,
                        cursorColor = SemanticError
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
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Delete Account",
                        color = if (isValid) NeutralWhite else NeutralMid,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isValid) SemanticError else NeutralLight)
                            .clickable(enabled = isValid) { onDelete() }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
