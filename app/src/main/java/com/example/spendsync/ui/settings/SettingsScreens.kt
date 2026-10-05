package com.example.spendsync.ui.settings

import com.example.spendsync.ui.i18n.accentLabel
import com.example.spendsync.ui.i18n.themeLabel
import androidx.annotation.StringRes
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.AppIconButton
import androidx.compose.material3.MaterialTheme
import com.example.spendsync.ui.components.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.PieChart
import com.example.spendsync.ui.components.AppChip
import com.example.spendsync.utils.formatInr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.notifications.NotificationAppAllowlist
import com.example.spendsync.ui.i18n.AppLanguage
import com.example.spendsync.ui.profile.FAQItem
import com.example.spendsync.ui.theme.AccentOptions

// ── Model ────────────────────────────────────────────────────────────────────

/** Immutable snapshot of everything the settings UI reads. */
data class SettingsModel(
    val userName: String,
    val userEmail: String,
    val userId: String,
    val joinedDate: String,
    val isSignedIn: Boolean,
    val themeMode: String,
    val accent: String,
    val language: AppLanguage,
    val dateFormat: String,
    val pushNotifications: Boolean,
    val emailNotifications: Boolean,
    val autoBackup: Boolean,
    val autoCapture: Boolean,
    val autoCapturePackages: Set<String>,
    val notificationAccessGranted: Boolean,
    val maskingEnabled: Boolean,
    val visibilitySeconds: Int,
    val assistantEnabled: Boolean,
    val assistant: com.example.spendsync.data.assistant.AssistantPrefs,
    /** Models the server reports; null while loading or if it could not be reached. */
    val assistantModels: List<AssistantModelInfo>?,
    val planify: com.example.spendsync.data.local.PlanifySettings = com.example.spendsync.data.local.PlanifySettings(),
)

/** Every user-triggered change. The host decides persistence, syncing and dialogs. */
class SettingsActions(
    val editProfile: () -> Unit,
    val setThemeMode: (String) -> Unit,
    val setAccent: (String) -> Unit,
    val pickLanguage: () -> Unit,
    val pickDateFormat: () -> Unit,
    val setPush: (Boolean) -> Unit,
    val setEmail: (Boolean) -> Unit,
    val setAutoBackup: (Boolean) -> Unit,
    val setAutoCapture: (Boolean) -> Unit,
    val setCapturePackage: (String, Boolean) -> Unit,
    val openNotificationAccess: () -> Unit,
    val setMasking: (Boolean) -> Unit,
    val setAssistant: (Boolean) -> Unit,
    val setPlanify: (com.example.spendsync.data.local.PlanifySettings) -> Unit,
    val setAssistantPrefs: (com.example.spendsync.data.assistant.AssistantPrefs) -> Unit,
    val refreshAssistantStatus: () -> Unit,
    val clearAssistantHistory: () -> Unit,
    val changePin: () -> Unit,
    val pickVisibility: () -> Unit,
    val export: () -> Unit,
    val clearLocalData: () -> Unit,
    val privacyPolicy: () -> Unit,
    val deleteAccount: () -> Unit,
    val signOut: () -> Unit,
)

fun durationLabel(seconds: Int): String = when (seconds) {
    30 -> tr(R.string.s_30_seconds)
    300 -> tr(R.string.s_5_minutes)
    900 -> tr(R.string.s_15_minutes)
    else -> tr(R.string.s_1_minute)
}

// ── Pages ────────────────────────────────────────────────────────────────────

enum class SettingsPage(@StringRes val titleRes: Int, val icon: ImageVector, val summary: (SettingsModel) -> String) {
    Account(R.string.account, Icons.Default.Person, { if (it.isSignedIn) it.userEmail.ifBlank { tr(R.string.signed_in) } else tr(R.string.guest_not_synced) }),
    Appearance(R.string.appearance_format, Icons.Default.Palette, { "${themeLabel(it.themeMode)} · ${accentLabel(it.accent)} · ${it.language.nativeName}" }),
    Notifications(R.string.notifications, Icons.Default.Notifications, {
        val on = listOf(it.pushNotifications, it.emailNotifications).count { v -> v }
        if (on == 0) tr(R.string.all_off) else tr(R.string.s_1_of_2_on, on)
    }),
    Privacy(R.string.privacy_security, Icons.Default.Security, {
        if (it.maskingEnabled) tr(R.string.amounts_hidden_unlock_lasts, durationLabel(it.visibilitySeconds)) else tr(R.string.amounts_always_visible)
    }),
    Assistant(R.string.assistant_title, Icons.Default.AutoAwesome, { assistantSummary(it) }),
    Planify(R.string.pl_set_title, Icons.Default.PieChart, {
        tr(R.string.pl_set_summary, tr(if (it.planify.alerts) R.string.pl_set_on else R.string.pl_set_off), tr(if (it.planify.daily) R.string.pl_set_on else R.string.pl_set_off))
    }),
    AutoCapture(R.string.auto_capture, Icons.Default.SettingsSuggest, {
        if (!it.autoCapture) tr(R.string.off) else tr(R.string.on_1_app_s, it.autoCapturePackages.size)
    }),
    Data(R.string.data_backup, Icons.Default.Backup, { if (it.autoBackup) tr(R.string.daily_backup_on) else tr(R.string.backup_off) }),
    About(R.string.help_about, Icons.Default.Help, { tr(R.string.faq_privacy_policy) });

    val title: String get() = tr(titleRes)
}

/** Every searchable setting, registered once. Add a row here to make it findable from the hub. */
private fun searchIndex(): List<Pair<String, SettingsPage>> = listOf(
    tr(R.string.edit_profile_name_email) to SettingsPage.Account,
    tr(R.string.sign_out_log_out) to SettingsPage.Account,
    tr(R.string.delete_account) to SettingsPage.Account,
    tr(R.string.theme_dark_light_system_mode) to SettingsPage.Appearance,
    tr(R.string.accent_colour_color) to SettingsPage.Appearance,
    tr(R.string.language_translate_english_hindi_spanish_fre) to SettingsPage.Appearance,
    tr(R.string.date_format_2) to SettingsPage.Appearance,
    tr(R.string.push_notifications_reminders_alerts) to SettingsPage.Notifications,
    tr(R.string.email_notifications) to SettingsPage.Notifications,
    tr(R.string.hide_amounts_mask_pin) to SettingsPage.Privacy,
    tr(R.string.visibility_duration_unlock_timer) to SettingsPage.Privacy,
    tr(R.string.asst_group_use) to SettingsPage.Assistant,
    tr(R.string.asst_model_row) to SettingsPage.Assistant,
    tr(R.string.asst_group_instructions) to SettingsPage.Assistant,
    tr(R.string.auto_detect_capture_transactions_notificatio) to SettingsPage.AutoCapture,
    tr(R.string.google_pay_phonepe_paytm_messages_apps) to SettingsPage.AutoCapture,
    tr(R.string.auto_backup_cloud) to SettingsPage.Data,
    tr(R.string.export_csv_pdf_download) to SettingsPage.Data,
    tr(R.string.clear_local_data_cache) to SettingsPage.Data,
    tr(R.string.privacy_policy_2) to SettingsPage.About,
    tr(R.string.faq_help_support) to SettingsPage.About,
    tr(R.string.pl_set_search) to SettingsPage.Planify,
)

// ── Hub ──────────────────────────────────────────────────────────────────────

@Composable
fun SettingsHub(model: SettingsModel, onOpen: (SettingsPage) -> Unit) {
    var query by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    val q = query.trim()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(tr(R.string.search_settings)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (q.isNotEmpty()) {
                    AppIconButton(Icons.Default.Close, tr(R.string.clear_search), onClick = { query = "" })
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = scheme.surface.copy(alpha = 0.88f),
                unfocusedContainerColor = scheme.surface.copy(alpha = 0.88f),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        )

        if (q.isEmpty()) {
            SettingsGroup {
                SettingsPage.entries.forEachIndexed { i, page ->
                    if (i > 0) SettingsDivider()
                    SettingsNavRow(
                        icon = page.icon,
                        title = page.title,
                        subtitle = page.summary(model),
                        onClick = { onOpen(page) },
                    )
                }
            }
        } else {
            val hits = searchIndex().filter { (words, page) ->
                q.split(' ').all { t -> words.contains(t, ignoreCase = true) || page.title.contains(t, ignoreCase = true) }
            }.map { it.second }.distinct()
            if (hits.isEmpty()) {
                Text(
                    tr(R.string.no_settings_match_1, q),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                SettingsGroup {
                    hits.forEachIndexed { i, page ->
                        if (i > 0) SettingsDivider()
                        SettingsNavRow(icon = page.icon, title = page.title, subtitle = page.summary(model), onClick = { onOpen(page) })
                    }
                }
            }
        }
    }
}

// ── Category pages ───────────────────────────────────────────────────────────

@Composable
fun SettingsPageScreen(page: SettingsPage, model: SettingsModel, actions: SettingsActions, onBack: () -> Unit) {
    SettingsBackdrop {
        Column(Modifier.fillMaxSize()) {
            SettingsTopBar(page.title, onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                SettingsContentWidth {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Spacer(Modifier.height(4.dp))
                        when (page) {
                            SettingsPage.Account -> AccountPage(model, actions)
                            SettingsPage.Appearance -> AppearancePage(model, actions)
                            SettingsPage.Notifications -> NotificationsPage(model, actions)
                            SettingsPage.Privacy -> PrivacyPage(model, actions)
                            SettingsPage.Assistant -> AssistantPage(model, actions)
                            SettingsPage.Planify -> PlanifySettingsPage(model, actions)
                            SettingsPage.AutoCapture -> AutoCapturePage(model, actions)
                            SettingsPage.Data -> DataPage(model, actions)
                            SettingsPage.About -> AboutPage(actions)
                        }
                        // Clears the floating bottom bar.
                        Spacer(Modifier.height(110.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountPage(m: SettingsModel, a: SettingsActions) {
    SettingsGroup(Modifier.cascadeIn(0)) {
        SettingsInfoRow(tr(R.string.name), m.userName.ifBlank { tr(R.string.guest) })
        SettingsDivider()
        SettingsInfoRow(tr(R.string.email), m.userEmail.ifBlank { "—" })
        SettingsDivider()
        SettingsInfoRow(tr(R.string.member_since), m.joinedDate)
        SettingsDivider()
        SettingsInfoRow(tr(R.string.cloud_sync), if (m.isSignedIn) tr(R.string.active) else tr(R.string.not_synced_guest))
        SettingsDivider()
        SettingsInfoRow(tr(R.string.user_id), m.userId.ifBlank { tr(R.string.guest_mode) })
    }
    SettingsGroup(Modifier.cascadeIn(1)) {
        SettingsNavRow(Icons.Default.Edit, tr(R.string.edit_profile), tr(R.string.change_your_name_or_email), onClick = a.editProfile)
    }
    SettingsGroup(Modifier.cascadeIn(2)) {
        SettingsNavRow(Icons.AutoMirrored.Filled.Logout, tr(R.string.sign_out_2), tr(R.string.you_can_always_sign_back_in), chevron = false, onClick = a.signOut)
        SettingsDivider()
        SettingsNavRow(
            Icons.Default.DeleteForever, tr(R.string.delete_account), tr(R.string.permanently_removes_all_your_data),
            destructive = true, onClick = a.deleteAccount,
        )
    }
}

@Composable
private fun AppearancePage(m: SettingsModel, a: SettingsActions) {
    SettingsGroupLabel(tr(R.string.theme))
    SettingsGroup(Modifier.cascadeIn(0), footer = tr(R.string.system_follows_your_phone_s_light)) {
        SettingsSegmented(
            options = listOf(
                SegmentOption("System", themeLabel("System"), Icons.Default.Brightness4),
                SegmentOption("Light", themeLabel("Light"), Icons.Default.LightMode),
                SegmentOption("Dark", themeLabel("Dark"), Icons.Default.DarkMode),
            ),
            selected = m.themeMode,
            onSelect = a.setThemeMode,
        )
        ScopeTag(synced = true)
    }
    SettingsGroupLabel(tr(R.string.accent_colour))
    SettingsGroup(Modifier.cascadeIn(1)) {
        SettingsSwatches(AccentOptions, m.accent, a.setAccent)
        Text(
            accentLabel(m.accent),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
        )
        ScopeTag(synced = true)
    }
    SettingsGroupLabel(tr(R.string.language_formats))
    SettingsGroup(Modifier.cascadeIn(2)) {
        SettingsNavRow(Icons.Default.Language, tr(R.string.language), value = m.language.nativeName, onClick = a.pickLanguage)
        SettingsDivider()
        SettingsNavRow(Icons.Default.CalendarMonth, tr(R.string.date_format_2), value = m.dateFormat, onClick = a.pickDateFormat)
        ScopeTag(synced = true)
    }
}

@Composable
private fun NotificationsPage(m: SettingsModel, a: SettingsActions) {
    SettingsGroup(Modifier.cascadeIn(0), footer = tr(R.string.push_covers_hold_reminders_and_spending)) {
        SettingsToggleRow(Icons.Default.NotificationsActive, tr(R.string.push_notifications_2), tr(R.string.reminders_and_alerts), m.pushNotifications, onCheckedChange = a.setPush)
        SettingsDivider()
        SettingsToggleRow(Icons.Default.Email, tr(R.string.email_notifications), tr(R.string.summaries_and_account_notices), m.emailNotifications, onCheckedChange = a.setEmail)
        ScopeTag(synced = true)
    }
}

@Composable
private fun PrivacyPage(m: SettingsModel, a: SettingsActions) {
    SettingsGroup(
        Modifier.cascadeIn(0),
        footer = tr(R.string.your_pin_never_leaves_this_phone),
    ) {
        SettingsToggleRow(
            Icons.Default.Lock, tr(R.string.hide_large_amounts), tr(R.string.amounts_over_1_000_show_as),
            m.maskingEnabled, onCheckedChange = a.setMasking,
        )
        SettingsDivider()
        SettingsNavRow(Icons.Default.Fingerprint, tr(R.string.change_pin), enabled = m.maskingEnabled, onClick = a.changePin)
        SettingsDivider()
        SettingsNavRow(
            Icons.Default.Timer, tr(R.string.visibility_duration), tr(R.string.how_long_amounts_stay_visible_after),
            value = durationLabel(m.visibilitySeconds), enabled = m.maskingEnabled, onClick = a.pickVisibility,
        )
        ScopeTag(synced = false)
    }
}

@Composable
private fun AutoCapturePage(m: SettingsModel, a: SettingsActions) {
    if (m.autoCapture && !m.notificationAccessGranted) {
        Box(Modifier.cascadeIn(0)) {
            SettingsBanner(
                icon = Icons.Default.Warning,
                text = tr(R.string.notification_access_is_off_so_payments),
                actionLabel = tr(R.string.fix),
                onAction = a.openNotificationAccess,
            )
        }
    }
    SettingsGroup(
        Modifier.cascadeIn(1),
        footer = tr(R.string.only_the_apps_you_pick_below),
    ) {
        SettingsToggleRow(
            Icons.Default.SettingsSuggest, tr(R.string.auto_detect_transactions), tr(R.string.turn_payment_notifications_into_transactions),
            m.autoCapture, onCheckedChange = a.setAutoCapture,
        )
        ScopeTag(synced = false)
    }
    if (m.autoCapture) {
        SettingsGroupLabel(tr(R.string.apps_to_watch))
        SettingsGroup(Modifier.cascadeIn(2)) {
            NotificationAppAllowlist.APPS.forEachIndexed { i, app ->
                if (i > 0) SettingsDivider()
                SettingsToggleRow(
                    Icons.Default.NotificationsActive, app.displayName,
                    checked = app.packageName in m.autoCapturePackages,
                    onCheckedChange = { a.setCapturePackage(app.packageName, it) },
                )
            }
        }
    }
}

@Composable
private fun DataPage(m: SettingsModel, a: SettingsActions) {
    SettingsGroup(Modifier.cascadeIn(0)) {
        SettingsToggleRow(Icons.Default.Backup, tr(R.string.auto_backup_2), tr(R.string.back_up_your_data_to_the), m.autoBackup, onCheckedChange = a.setAutoBackup)
        ScopeTag(synced = true)
    }
    SettingsGroup(Modifier.cascadeIn(1)) {
        SettingsNavRow(Icons.Default.FileDownload, tr(R.string.export_data), tr(R.string.download_your_transactions_as_csv_or), onClick = a.export)
    }
    SettingsGroup(Modifier.cascadeIn(2), footer = tr(R.string.clearing_removes_custom_categories_stored_on)) {
        SettingsNavRow(
            Icons.Default.DeleteSweep, tr(R.string.clear_local_data), tr(R.string.remove_cached_data_and_local_categories),
            destructive = true, onClick = a.clearLocalData,
        )
    }
}

@Composable
private fun AboutPage(a: SettingsActions) {
    SettingsGroup(Modifier.cascadeIn(0)) {
        SettingsNavRow(Icons.Default.PrivacyTip, tr(R.string.privacy_policy_2), onClick = a.privacyPolicy)
    }
    SettingsGroupLabel(tr(R.string.frequently_asked))
    SettingsGroup(Modifier.cascadeIn(1)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FAQItem(tr(R.string.how_do_i_back_up_my), tr(R.string.auto_backup_is_on_by_default))
            FAQItem(tr(R.string.is_my_financial_data_secure), tr(R.string.transactions_are_encrypted_in_transit_and))
            FAQItem(tr(R.string.how_do_i_hide_my_balances), tr(R.string.privacy_security_hide_large_amounts_you))
            FAQItem(tr(R.string.how_do_i_delete_my_account), tr(R.string.account_delete_account_this_permanently_remo))
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlanifySettingsPage(m: SettingsModel, a: SettingsActions) {
    val p = m.planify
    SettingsGroup(Modifier.cascadeIn(0), footer = tr(R.string.pl_set_footer)) {
        SettingsToggleRow(Icons.Default.NotificationsActive, tr(R.string.pl_set_alerts), tr(R.string.pl_set_alerts_sub), p.alerts, onCheckedChange = { a.setPlanify(p.copy(alerts = it)) })
        SettingsDivider()
        SettingsToggleRow(Icons.Default.Notifications, tr(R.string.pl_set_daily), tr(R.string.pl_set_daily_sub), p.daily, onCheckedChange = { a.setPlanify(p.copy(daily = it)) })
        ScopeTag(synced = true)
    }
    SettingsGroupLabel(tr(R.string.pl_set_salary))
    SettingsGroup(Modifier.cascadeIn(1), footer = tr(R.string.pl_set_salary_sub)) {
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(2000, 5000, 10000, 25000).forEach { v ->
                AppChip(formatInr(v.toDouble()), selected = p.salaryMin == v, onClick = { a.setPlanify(p.copy(salaryMin = v)) })
            }
        }
        ScopeTag(synced = true)
    }
}
