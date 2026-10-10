package com.example.spendsync.ui.profile

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import android.content.Intent
import com.example.spendsync.data.repository.SettingField
import com.example.spendsync.data.repository.SettingsSynchronizer
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.Skeleton
import com.example.spendsync.ui.i18n.AppLanguage
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.spendsync.ui.settings.SettingsActions
import com.example.spendsync.ui.settings.SettingsBackdrop
import com.example.spendsync.ui.settings.SettingsContentWidth
import com.example.spendsync.ui.settings.SettingsHub
import com.example.spendsync.ui.settings.SettingsModel
import com.example.spendsync.ui.settings.SettingsPage
import com.example.spendsync.ui.settings.SettingsPageScreen
import com.example.spendsync.ui.settings.cascadeIn
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import com.example.spendsync.ui.components.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.spendsync.ui.components.Text
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
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.notifications.HoldReminderWorker
import com.example.spendsync.data.remote.model.DashboardSummaryDto
import androidx.compose.runtime.LaunchedEffect
import com.example.spendsync.notifications.NotificationAppAllowlist
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.MaskableAmountText
import com.example.spendsync.ui.shared.PinSetupDialog
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
import kotlinx.coroutines.flow.first
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
    amountVisibility: AmountVisibilityState,
    openSettingsRequestId: Int = 0,
    /** The Settings page a search result asked for; null opens the Settings hub. */
    openSettingsPage: SettingsPage? = null,
    /** Something on another tab changed (e.g. a Trash restore): Home must reload. */
    onDataChanged: () -> Unit = {},
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

    val themeMode by sessionDataStore.themeMode.collectAsState(initial = "System")
    val accent by sessionDataStore.accentColor.collectAsState(initial = "Brand Blue")
    val languageName by sessionDataStore.language.collectAsState(initial = "English")
    val notifications by sessionDataStore.notificationsEnabled.collectAsState(initial = true)
    val emailNotifications by sessionDataStore.emailNotifications.collectAsState(initial = true)
    val autoBackup by sessionDataStore.autoBackup.collectAsState(initial = true)
    val dateFormat by sessionDataStore.dateFormat.collectAsState(initial = "DD / MM / YYYY")
    val autoCaptureEnabled by sessionDataStore.autoCaptureEnabled.collectAsState(initial = false)
    val autoCapturePackages by sessionDataStore.autoCapturePackages.collectAsState(initial = emptySet())
    val amountMaskingEnabled by sessionDataStore.amountMaskingEnabled.collectAsState(initial = false)
    val amountVisibilityDurationSeconds by sessionDataStore.amountVisibilityDurationSeconds.collectAsState(initial = 60)
    val pinHash by sessionDataStore.pinHash.collectAsState(initial = null)
    val assistantConsent by sessionDataStore.assistantConsent.collectAsState(initial = false)
    val planifySettings by sessionDataStore.planifySettings.collectAsState(initial = com.example.spendsync.data.local.PlanifySettings())
    val motionPrefs by sessionDataStore.animationPrefs.collectAsState(initial = com.example.spendsync.ui.theme.MotionPrefs())
    val exportOnLogin by sessionDataStore.exportOnLogin.collectAsState(initial = false)
    val customColors by sessionDataStore.customColors.collectAsState(initial = com.example.spendsync.ui.theme.CustomColors())
    val assistantPrefs by sessionDataStore.assistantPrefs.collectAsState(initial = com.example.spendsync.data.assistant.AssistantPrefs())
    var assistantModels by remember { mutableStateOf<List<com.example.spendsync.ui.settings.AssistantModelInfo>?>(null) }
    val assistantRepository = remember { com.example.spendsync.data.assistant.AssistantRepository(sessionDataStore) }
    val chatStore = remember { com.example.spendsync.data.assistant.ChatStore(context.applicationContext) }

    // Notification-listener access is granted in system settings, so re-check on every resume.
    var notificationAccessGranted by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalView.current.context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        fun refresh() {
            notificationAccessGranted = context.packageName in
                NotificationManagerCompat.getEnabledListenerPackages(context)
        }
        refresh()
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

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
    var showLanguageDialog by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var deleteAccountError by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAutoCaptureExplainer by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showVisibilityDurationDialog by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Deep-link to Notification Access only once the permission dialog has
        // resolved — launching both at once races and the Settings screen wins,
        // so the user never sees the permission prompt.
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
    // Planify alerts need the notification permission (Android 13+); ask when the user switches them on.
    val planifyPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askNotificationPermissionIfNeeded = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) planifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    // Which settings page is open; null = Profile home (hero + hub).
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }
    // Global search's "open settings" jump lands on the hub.
    LaunchedEffect(openSettingsRequestId) {
        if (openSettingsRequestId > 0) page = openSettingsPage
    }

    // Every preference is written here first, then pushed to the account. If the push fails it
    // stays queued and is retried on the next launch / sign-in (see SettingsSynchronizer).
    val synchronizer = remember { SettingsSynchronizer(sessionDataStore, financeRepository) { repository.hasLocalSession() } }
    fun commit(vararg fields: SettingField, local: suspend () -> Unit) {
        scope.launch {
            local()
            if (!synchronizer.changed(*fields)) {
                Toast.makeText(context, tr(R.string.saved_on_this_phone_it_will), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val joinedDate = remember(userCreatedAt) {
        try {
            java.time.ZonedDateTime.parse(userCreatedAt)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy"))
        } catch (e: Exception) {
            if (userCreatedAt.isNullOrBlank()) tr(R.string.just_now) else userCreatedAt.orEmpty()
        }
    }

    val model = SettingsModel(
        userName = userName.orEmpty(),
        userEmail = userEmail.orEmpty(),
        userId = userId.orEmpty(),
        joinedDate = joinedDate,
        isSignedIn = isSignedIn,
        themeMode = themeMode,
        accent = accent,
        language = AppLanguage.fromStored(languageName),
        dateFormat = dateFormat,
        pushNotifications = notifications,
        emailNotifications = emailNotifications,
        autoBackup = autoBackup,
        autoCapture = autoCaptureEnabled,
        autoCapturePackages = autoCapturePackages,
        notificationAccessGranted = notificationAccessGranted,
        maskingEnabled = amountMaskingEnabled,
        visibilitySeconds = amountVisibilityDurationSeconds,
        assistantEnabled = assistantConsent,
        assistant = assistantPrefs,
        assistantModels = assistantModels,
        planify = planifySettings,
        motion = motionPrefs,
        colors = customColors,
        exportOnLogin = exportOnLogin,
    )

    val actions = SettingsActions(
        editProfile = { showEditProfile = true },
        setThemeMode = { mode -> commit(SettingField.Theme) { sessionDataStore.updateThemeMode(mode) } },
        setAccent = { name -> commit(SettingField.Accent) { sessionDataStore.updateAccentColor(name) } },
        pickLanguage = { showLanguageDialog = true },
        pickDateFormat = { showDateFormatDialog = true },
        setPush = { v -> commit(SettingField.Push) { sessionDataStore.updateNotifications(v) } },
        setEmail = { v -> commit(SettingField.Email) { sessionDataStore.updateEmailNotifications(v) } },
        setAutoBackup = { v -> commit(SettingField.Backup) { sessionDataStore.updateAutoBackup(v) } },
        setAutoCapture = { turningOn ->
            if (turningOn) showAutoCaptureExplainer = true
            else commit(SettingField.AutoCapture) { sessionDataStore.updateAutoCaptureEnabled(false) }
        },
        setCapturePackage = { pkg, on ->
            val updated = if (on) autoCapturePackages + pkg else autoCapturePackages - pkg
            commit(SettingField.AutoCapturePackages) { sessionDataStore.updateAutoCapturePackages(updated) }
        },
        openNotificationAccess = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        },
        setMasking = { turningOn ->
            if (turningOn) showPinSetupDialog = true
            else commit(SettingField.Masking) { sessionDataStore.updateAmountMaskingEnabled(false) }
        },
        setAssistant = { v -> scope.launch { sessionDataStore.updateAssistantConsent(v) } },
        setMotion = { m -> scope.launch { sessionDataStore.updateAnimationPrefs(m) } },
        setExportOnLogin = { v -> scope.launch { sessionDataStore.updateExportOnLogin(v) } },
        setColors = { c -> scope.launch { sessionDataStore.updateCustomColors(c) } },
        loadReports = { assistantRepository.tickets() },
        closeReport = { ref -> assistantRepository.closeTicket(ref) },
        loadTrash = { cursor -> (financeRepository.getTrash(cursor) as? AuthResult.Success)?.data },
        restoreTrashItem = { item ->
            if (item.kind == "transaction") {
                val r = financeRepository.restoreTransaction(item.id)
                if (r is AuthResult.Success) {
                    r.data.holds.forEach { HoldReminderWorker.scheduleFor(context, it) }
                    onDataChanged()
                }
                r is AuthResult.Success
            } else {
                val r = financeRepository.restoreHold(item.id)
                if (r is AuthResult.Success) {
                    HoldReminderWorker.scheduleFor(context, r.data)
                    onDataChanged()
                }
                r is AuthResult.Success
            }
        },
        deleteTrashItem = { item -> financeRepository.deleteForever(item.kind, item.id) is AuthResult.Success },
        emptyTrash = { financeRepository.emptyTrash() is AuthResult.Success },
        amountVisibility = amountVisibility,
        setPlanify = { p ->
            commit(SettingField.Planify) { sessionDataStore.updatePlanifySettings(p) }
            // the evening job follows the switch right away
            com.example.spendsync.notifications.PlanDailyWorker.sync(context.applicationContext, p.daily, reschedule = true)
            if (p.daily || p.alerts) askNotificationPermissionIfNeeded()
        },
        setAssistantPrefs = { p -> commit(SettingField.Assistant) { sessionDataStore.updateAssistantPrefs(p) } },
        refreshAssistantStatus = { scope.launch { assistantModels = assistantRepository.models() } },
        clearAssistantHistory = { scope.launch { chatStore.clear(sessionDataStore.userId.first().orEmpty()) } },
        changePin = { showChangePinDialog = true },
        pickVisibility = { showVisibilityDurationDialog = true },
        export = { showExportDialog = true },
        clearLocalData = { showClearDataDialog = true },
        privacyPolicy = { showPrivacyDialog = true },
        deleteAccount = { showDeleteAccountDialog = true },
        signOut = {
            scope.launch {
                repository.signOut()
                onSignOut()
            }
        },
    )

    AnimatedContent(
        targetState = page,
        transitionSpec = com.example.spendsync.ui.theme.motionSpec(com.example.spendsync.ui.theme.LocalMotion.current.enabled(com.example.spendsync.ui.theme.MotionKind.Transitions)) {
            val forward = targetState != null
            val enter = slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(320))
            val exit = slideOutHorizontally(tween(320)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200))
            enter togetherWith exit
        },
        label = "settings_nav",
    ) { current ->
        if (current != null) {
            SettingsPageScreen(current, model, actions, onBack = { page = null })
        } else {
            SettingsBackdrop {
                com.example.spendsync.ui.components.AppPullToRefresh(
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
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SettingsContentWidth {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Spacer(Modifier.statusBarsPadding().height(12.dp))
                                ProfileHero(
                                    name = model.userName,
                                    email = model.userEmail,
                                    isStatsLoading = isStatsLoading,
                                    transactions = dashboardSummary?.totals?.totalTransactions ?: 0,
                                    spent = dashboardSummary?.totals?.totalSpent ?: 0.0,
                                    net = dashboardSummary?.totals?.netAmount ?: 0.0,
                                    amountVisibility = amountVisibility,
                                    onEdit = { showEditProfile = true },
                                    modifier = Modifier.cascadeIn(0),
                                )
                                Box(Modifier.cascadeIn(1)) { SettingsHub(model, onOpen = { page = it }) }
                                Spacer(Modifier.height(110.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────
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
            },
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            selected = AppLanguage.fromStored(languageName),
            onSelect = { lang ->
                showLanguageDialog = false
                commit(SettingField.Language) { sessionDataStore.updateLanguage(lang.storedName) }
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showDateFormatDialog) {
        DateFormatDialog(
            selected = dateFormat,
            onSelect = { picked ->
                showDateFormatDialog = false
                commit(SettingField.DateFormat) { sessionDataStore.updateDateFormat(picked) }
            },
            onDismiss = { showDateFormatDialog = false },
        )
    }

    if (showExportDialog) {
        ExportSheet(financeRepository = financeRepository, onDismiss = { showExportDialog = false })
    }

    if (showAutoCaptureExplainer) {
        AutoCaptureExplainerDialog(
            onDismiss = { showAutoCaptureExplainer = false },
            onContinue = {
                showAutoCaptureExplainer = false
                commit(SettingField.AutoCapture) { sessionDataStore.updateAutoCaptureEnabled(true) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // The launcher's callback opens Notification Access.
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            },
        )
    }

    if (showClearDataDialog) {
        ClearDataConfirmationDialog(
            onDismiss = { showClearDataDialog = false },
            onConfirm = {
                scope.launch {
                    sessionDataStore.clearLocalData()
                    financeRepository.clearCache()
                }
            },
        )
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyDialog = false })
    }

    if (showDeleteAccountDialog) {
        DeleteAccountWarningDialog(
            loading = deletingAccount,
            error = deleteAccountError,
            onDismiss = { if (!deletingAccount) { showDeleteAccountDialog = false; deleteAccountError = null } },
            onDelete = {
                scope.launch {
                    deletingAccount = true
                    deleteAccountError = null
                    // Guests have nothing on the server; signed-in users are deleted there first,
                    // so a failed request never leaves them signed out of an account that still exists.
                    val result = if (isSignedIn) financeRepository.deleteAccount() else AuthResult.Success(Unit)
                    deletingAccount = false
                    when (result) {
                        is AuthResult.Success -> {
                            sessionDataStore.clearSession()
                            showDeleteAccountDialog = false
                            onSignOut()
                        }
                        is AuthResult.Error -> deleteAccountError = result.message
                    }
                }
            },
        )
    }

    if (showPinSetupDialog) {
        PinSetupDialog(
            sessionDataStore = sessionDataStore,
            requireCurrentPin = pinHash != null,
            onDone = {
                showPinSetupDialog = false
                commit(SettingField.Masking) { sessionDataStore.updateAmountMaskingEnabled(true) }
            },
            onDismiss = { showPinSetupDialog = false },
        )
    }

    if (showChangePinDialog) {
        PinSetupDialog(
            sessionDataStore = sessionDataStore,
            requireCurrentPin = true,
            onDone = { showChangePinDialog = false },
            onDismiss = { showChangePinDialog = false },
        )
    }

    if (showVisibilityDurationDialog) {
        VisibilityDurationDialog(
            selectedSeconds = amountVisibilityDurationSeconds,
            onSelect = { seconds ->
                showVisibilityDurationDialog = false
                commit(SettingField.MaskingSeconds) { sessionDataStore.updateAmountVisibilityDurationSeconds(seconds) }
            },
            onDismiss = { showVisibilityDurationDialog = false },
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
private fun StatItem(
    label: String,
    value: Double,
    amountVisibility: AmountVisibilityState,
    modifier: Modifier = Modifier,
) {
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MaskableAmountText(
            amount     = value,
            visibility = amountVisibility,
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
internal fun FAQItem(question: String, answer: String) {
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

/** Identity card + this-month stats. Colours come from the theme so it follows light/dark and the accent. */
@Composable
private fun ProfileHero(
    name: String,
    email: String,
    isStatsLoading: Boolean,
    transactions: Int,
    spent: Double,
    net: Double,
    amountVisibility: AmountVisibilityState,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surface.copy(alpha = 0.88f))
            .border(BorderStroke(0.5.dp, scheme.outlineVariant.copy(alpha = 0.6f)), RoundedCornerShape(28.dp))
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary))),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(76.dp).clip(CircleShape).background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (name.firstOrNull() ?: '?').toString().uppercase(),
                    color = scheme.onPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = name.ifBlank { tr(R.string.guest) },
            color = scheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        if (email.isNotBlank()) {
            Text(text = email, color = scheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        AppButton(tr(R.string.edit_profile), onClick = onEdit, variant = ButtonVariant.Tonal, size = ButtonSize.Small, leadingIcon = Icons.Default.Edit)

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f), thickness = 0.6.dp)
        Spacer(Modifier.height(16.dp))

        Skeleton(loading = isStatsLoading) {
            Row(Modifier.fillMaxWidth()) {
                StatItem(label = tr(R.string.transactions), value = if (isStatsLoading) "000" else transactions.toString(), modifier = Modifier.weight(1f))
                StatItem(label = tr(R.string.this_month), value = if (isStatsLoading) 12345.0 else spent, amountVisibility = amountVisibility, modifier = Modifier.weight(1f))
                StatItem(label = tr(R.string.net), value = if (isStatsLoading) 12345.0 else net, amountVisibility = amountVisibility, modifier = Modifier.weight(1f))
            }
        }
    }
}
