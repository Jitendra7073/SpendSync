package com.example.spendsync.ui.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.utils.LocalizationUtils
import com.example.spendsync.ui.theme.NeutralBlack
import com.example.spendsync.ui.theme.NeutralLight
import com.example.spendsync.ui.theme.NeutralMid
import com.example.spendsync.ui.theme.NeutralOffWhite
import com.example.spendsync.ui.theme.NeutralWhite
import com.example.spendsync.ui.theme.SemanticError
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    sessionDataStore: SessionDataStore,
    onBack: () -> Unit
) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val BrandBlue = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()

    // Read reactive flows from DataStore
    val darkMode by sessionDataStore.darkMode.collectAsState(initial = false)
    val notifications by sessionDataStore.notificationsEnabled.collectAsState(initial = true)
    val biometrics by sessionDataStore.biometricLock.collectAsState(initial = false)
    val autoBackup by sessionDataStore.autoBackup.collectAsState(initial = true)

    val accentColor by sessionDataStore.accentColor.collectAsState(initial = "Brand Blue")
    val language by sessionDataStore.language.collectAsState(initial = "English")
    val currency by sessionDataStore.currency.collectAsState(initial = "USD")
    val dateFormat by sessionDataStore.dateFormat.collectAsState(initial = "DD / MM / YYYY")
    val securityPin by sessionDataStore.securityPin.collectAsState(initial = "")

    // State controllers for dialog visibility overlays
    var showAccentDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showDateFormatDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralOffWhite),
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeutralWhite)
                .statusBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.padding(end = 16.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = NeutralBlack,
                    )
                }
                Text(
                    text       = "Settings",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color      = NeutralBlack,
                )
            }
        }

        HorizontalDivider(color = NeutralLight, thickness = 1.dp)

        // ── Scrollable settings body ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            // ── Personalisation ───────────────────────────────────────────────
            SectionHeader(LocalizationUtils.getTranslation("personalisation", language))
            SettingsCard {
                ToggleRow(
                    icon    = Icons.Default.DarkMode,
                    label   = LocalizationUtils.getTranslation("dark_mode", language),
                    sub     = "Switch to a dark colour theme",
                    checked = darkMode,
                    onToggle = { scope.launch { sessionDataStore.updateDarkMode(it) } },
                )
                Divider()
                NavigationRow(
                    icon  = Icons.Default.ColorLens,
                    label = LocalizationUtils.getTranslation("accent_colour", language),
                    sub   = accentColor,
                    onClick = { showAccentDialog = true }
                )
                Divider()
                NavigationRow(
                    icon  = Icons.Default.Language,
                    label = LocalizationUtils.getTranslation("language", language),
                    sub   = language,
                    onClick = { showLanguageDialog = true }
                )
                Divider()
                val currencySub = when (currency) {
                    "EUR" -> "EUR — €"
                    "GBP" -> "GBP — £"
                    "INR" -> "INR — ₹"
                    "JPY" -> "JPY — ¥"
                    else  -> "USD — $"
                }
                NavigationRow(
                    icon  = Icons.Default.CurrencyExchange,
                    label = LocalizationUtils.getTranslation("currency", language),
                    sub   = currencySub,
                    onClick = { showCurrencyDialog = true }
                )
                Divider()
                NavigationRow(
                    icon  = Icons.Default.Tune,
                    label = LocalizationUtils.getTranslation("date_format", language),
                    sub   = dateFormat,
                    onClick = { showDateFormatDialog = true }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── General ───────────────────────────────────────────────────────
            SectionHeader(LocalizationUtils.getTranslation("general", language))
            SettingsCard {
                ToggleRow(
                    icon    = Icons.Default.NotificationsActive,
                    label   = LocalizationUtils.getTranslation("push_notifications", language),
                    sub     = "Reminders and alerts",
                    checked = notifications,
                    onToggle = { scope.launch { sessionDataStore.updateNotifications(it) } },
                )
                Divider()
                ToggleRow(
                    icon    = Icons.Default.Security,
                    label   = LocalizationUtils.getTranslation("biometric_lock", language),
                    sub     = "Use fingerprint / face to unlock",
                    checked = biometrics,
                    onToggle = { scope.launch { sessionDataStore.updateBiometrics(it) } },
                )
                Divider()
                NavigationRow(
                    icon  = Icons.Default.Security,
                    label = LocalizationUtils.getTranslation("change_pin", language),
                    sub   = if (securityPin.isEmpty()) "Not Set" else "Active (4 digits)",
                    onClick = { showPinDialog = true }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Data ──────────────────────────────────────────────────────────
            SectionHeader(LocalizationUtils.getTranslation("data", language))
            SettingsCard {
                ToggleRow(
                    icon    = Icons.Default.Backup,
                    label   = LocalizationUtils.getTranslation("auto_backup", language),
                    sub     = "Back up data to the cloud daily",
                    checked = autoBackup,
                    onToggle = { scope.launch { sessionDataStore.updateAutoBackup(it) } },
                )
                Divider()
                NavigationRow(
                    icon  = Icons.Default.DataUsage,
                    label = LocalizationUtils.getTranslation("export_data", language),
                    sub   = "Download as CSV or PDF",
                    onClick = { showExportDialog = true }
                )
                Divider()
                NavigationRow(
                    icon  = Icons.Default.PrivacyTip,
                    label = LocalizationUtils.getTranslation("privacy_policy", language),
                    onClick = { showPrivacyDialog = true }
                )
                Divider()
                NavigationRow(
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
            SectionHeader(LocalizationUtils.getTranslation("support", language))
            SettingsCard {
                NavigationRow(
                    icon  = Icons.Default.Feedback,
                    label = LocalizationUtils.getTranslation("send_feedback", language),
                    sub   = "Tell us what you think",
                    onClick = { showFeedbackDialog = true }
                )
                Divider()
                NavigationRow(
                    icon  = Icons.Default.RateReview,
                    label = LocalizationUtils.getTranslation("rate_app", language),
                    sub   = "Leave us a review on the Play Store",
                    onClick = { showRateDialog = true }
                )
                Divider()
                NavigationRow(
                    icon  = Icons.Default.Info,
                    label = LocalizationUtils.getTranslation("about_spendsync", language),
                    sub   = "Version 1.0.0",
                    onClick = { showAboutDialog = true }
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // ── Dialog 1: Accent Color Dialog ────────────────────────────────────────
    if (showAccentDialog) {
        OptionSelectionDialog(
            title = "Select Accent Colour",
            options = listOf("Brand Blue", "Kakariki Green", "Emerald Green", "Crimson Red", "Coral Orange"),
            selectedOption = accentColor,
            onDismiss = { showAccentDialog = false },
            onSelect = {
                scope.launch {
                    sessionDataStore.updateAccentColor(it)
                    showAccentDialog = false
                }
            }
        )
    }

    // ── Dialog 2: Language Dialog ───────────────────────────────────────────
    if (showLanguageDialog) {
        OptionSelectionDialog(
            title = "Select Language",
            options = listOf("English", "Spanish", "French", "German", "Hindi"),
            selectedOption = language,
            onDismiss = { showLanguageDialog = false },
            onSelect = {
                scope.launch {
                    sessionDataStore.updateLanguage(it)
                    showLanguageDialog = false
                }
            }
        )
    }

    // ── Dialog 3: Currency Dialog ───────────────────────────────────────────
    if (showCurrencyDialog) {
        OptionSelectionDialog(
            title = "Select Currency",
            options = listOf("USD", "EUR", "GBP", "INR", "JPY"),
            selectedOption = currency,
            onDismiss = { showCurrencyDialog = false },
            onSelect = {
                scope.launch {
                    sessionDataStore.updateCurrency(it)
                    showCurrencyDialog = false
                }
            }
        )
    }

    // ── Dialog 4: Date Format Dialog ────────────────────────────────────────
    if (showDateFormatDialog) {
        OptionSelectionDialog(
            title = "Select Date Format",
            options = listOf("DD / MM / YYYY", "MM / DD / YYYY", "YYYY - MM - DD"),
            selectedOption = dateFormat,
            onDismiss = { showDateFormatDialog = false },
            onSelect = {
                scope.launch {
                    sessionDataStore.updateDateFormat(it)
                    showDateFormatDialog = false
                }
            }
        )
    }

    // ── Dialog 5: Change PIN Keypad Dialog ──────────────────────────────────
    if (showPinDialog) {
        PinKeypadDialog(
            onDismiss = { showPinDialog = false },
            onSave = {
                scope.launch {
                    sessionDataStore.updateSecurityPin(it)
                    showPinDialog = false
                }
            }
        )
    }

    // ── Dialog 6: Export Data Dialog ────────────────────────────────────────
    if (showExportDialog) {
        ExportDataDialog(
            onDismiss = { showExportDialog = false }
        )
    }

    // ── Dialog 7: Privacy Policy Dialog ─────────────────────────────────────
    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyDialog = false }
        )
    }

    // ── Dialog 8: Delete Account Warning Dialog ──────────────────────────────
    if (showDeleteAccountDialog) {
        DeleteAccountWarningDialog(
            onDismiss = { showDeleteAccountDialog = false },
            onDelete = {
                scope.launch {
                    sessionDataStore.clearSession()
                    showDeleteAccountDialog = false
                    onBack()
                }
            }
        )
    }

    // ── Dialog 9: Send Feedback Dialog ──────────────────────────────────────
    if (showFeedbackDialog) {
        FeedbackSubmissionDialog(
            onDismiss = { showFeedbackDialog = false }
        )
    }

    // ── Dialog 10: Rate the App Dialog ──────────────────────────────────────
    if (showRateDialog) {
        RateAppSimulationDialog(
            onDismiss = { showRateDialog = false }
        )
    }

    // ── Dialog 11: About Dialog ──────────────────────────────────────────────
    if (showAboutDialog) {
        AboutSpendSyncDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}

// ── Building blocks ─────────────────────────────────────────────────────────────

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
private fun SettingsCard(content: @Composable () -> Unit) {
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
private fun Divider() {
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 16.dp),
        color     = NeutralLight,
        thickness = 0.8.dp,
    )
}

@Composable
private fun NavigationRow(
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
        // Icon badge
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
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    sub: String? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
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

// ── Dialog Overlays Implementation ───────────────────────────────────────────

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
                    fontSize = 17.sp,
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
private fun PinKeypadDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val BrandBlue = MaterialTheme.colorScheme.primary
    var pin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

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
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Set Security PIN",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Create a 4-digit security PIN to restrict local access.",
                    fontSize = 12.sp,
                    color = NeutralMid,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(Modifier.height(24.dp))

                // Pin bubble indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { idx ->
                        val isFilled = idx < pin.length
                        val dotBg = if (isFilled) BrandBlue else Color(0xFFCBD5E1)
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(dotBg)
                        )
                    }
                }

                if (hasError) {
                    Spacer(Modifier.height(8.dp))
                    Text("PIN must be exactly 4 digits.", color = SemanticError, fontSize = 11.sp)
                }

                Spacer(Modifier.height(24.dp))

                // Grid keypad
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Clear", "0", "OK")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    keys.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(NeutralOffWhite)
                                        .clickable {
                                            hasError = false
                                            when (key) {
                                                "Clear" -> if (pin.isNotEmpty()) pin =
                                                    pin.substring(0, pin.length - 1)

                                                "OK" -> {
                                                    if (pin.length == 4) onSave(pin)
                                                    else hasError = true
                                                }

                                                else -> {
                                                    if (pin.length < 4) pin += key
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (key == "Clear" || key == "OK") BrandBlue else NeutralBlack
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportDataDialog(
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

    val scope = rememberCoroutineScope()

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
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack
                )
                Spacer(Modifier.height(16.dp))

                if (!isExporting && !isSuccess) {
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
                                    // Simulate file build
                                    scope.launch {
                                        kotlinx.coroutines.delay(2000)
                                        isExporting = false
                                        isSuccess = true
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
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Data Export Successful!",
                            fontWeight = FontWeight.Bold,
                            color = NeutralBlack,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Your $format file has been generated and is ready to download.",
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
                    .height(320.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Privacy Policy",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeutralBlack
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = NeutralMid)
                    }
                }

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
                        text = "3. Security Lock\nBiometric fingerprint checks and PIN codes are restricted to local systems, ensuring you remain in total control of your budget database.",
                        fontSize = 12.sp,
                        color = NeutralMid
                    )
                }

                Spacer(Modifier.height(16.dp))
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
                    fontSize = 17.sp,
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
                    placeholder = { Text("Type DELETE here", color = NeutralMid, fontSize = 13.sp) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackSubmissionDialog(
    onDismiss: () -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val BrandBlue = MaterialTheme.colorScheme.primary
    var stars by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

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
                    text = "Submit App Feedback",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack
                )
                Spacer(Modifier.height(16.dp))

                if (!submitted) {
                    Text("How would you rate SpendSync?", color = NeutralMid, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(5) { idx ->
                            val active = idx < stars
                            val starTint = if (active) Color(0xFFF59E0B) else Color(0xFFCBD5E1)
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = starTint,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { stars = idx + 1 }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("What can we do to improve?", color = NeutralMid, fontSize = 13.sp) },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBlue,
                            unfocusedBorderColor = NeutralLight,
                            cursorColor = BrandBlue
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
                            text = "Submit",
                            color = if (stars > 0 && text.isNotBlank()) NeutralWhite else NeutralMid,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (stars > 0 && text.isNotBlank()) BrandBlue else NeutralLight)
                                .clickable(enabled = stars > 0 && text.isNotBlank()) { submitted = true }
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Feedback Submitted!",
                            fontWeight = FontWeight.Bold,
                            color = NeutralBlack,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Thank you for helping us improve SpendSync.",
                            color = NeutralMid,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrandBlue)
                                .clickable { onDismiss() }
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text("Close", color = NeutralWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RateAppSimulationDialog(
    onDismiss: () -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
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
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Enjoying SpendSync?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "If you enjoy tracking your budget with SpendSync, please leave us a 5-star rating on the Google Play Store!",
                    fontSize = 12.sp,
                    color = NeutralMid,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(5) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "No, thanks",
                        color = NeutralMid,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Rate 5 Stars",
                        color = NeutralWhite,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandBlue)
                            .clickable { onDismiss() }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSpendSyncDialog(
    onDismiss: () -> Unit
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
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
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "About SpendSync",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralBlack,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(BrandBlue.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "SpendSync Budget Tracker",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NeutralBlack
                )
                Text(
                    text = "Version 1.0.0 (Build 2026.07)",
                    color = NeutralMid,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "SpendSync is a premium agent-engineered personal finance application featuring Canvas metrics, dynamic calendar filtering, and local secure preferences datastores.",
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
                    Text("OK", color = NeutralWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
