package com.example.spendsync.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.spendsync.R
import com.example.spendsync.data.local.CaptureSettings
import com.example.spendsync.ui.components.AppChip
import com.example.spendsync.ui.components.AppIconButton
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.tr

fun sourceLabel(wire: String): String = tr(
    when (wire) {
        "sms" -> R.string.cap_src_sms
        "email" -> R.string.cap_src_email
        else -> R.string.cap_src_upi
    },
)

fun smartCaptureSummary(m: SettingsModel): String =
    if (!m.autoCapture) tr(R.string.off)
    else listOf("sms", "email", "upi").filter { it in m.capture.sources }.joinToString(" · ") { sourceLabel(it) }.ifEmpty { tr(R.string.cap_no_sources) }

private fun retentionLabel(days: Int) = if (days == 0) tr(R.string.cap_keep_forever) else tr(R.string.cap_keep_days, days)

@Composable
internal fun SmartCapturePage(m: SettingsModel, a: SettingsActions) {
    val c = m.capture
    fun set(next: CaptureSettings) = a.setCapture(next)

    if (m.autoCapture && !m.notificationAccessGranted) {
        Box(Modifier.cascadeIn(0)) {
            SettingsBanner(icon = Icons.Default.Warning, text = tr(R.string.notification_access_is_off_so_payments), actionLabel = tr(R.string.fix), onAction = a.openNotificationAccess)
        }
    }
    SettingsGroup(Modifier.cascadeIn(1), footer = tr(R.string.cap_footer_privacy)) {
        SettingsToggleRow(Icons.Default.SettingsSuggest, tr(R.string.cap_title), tr(R.string.cap_master_sub), m.autoCapture, onCheckedChange = a.setAutoCapture)
        ScopeTag(synced = true)
    }
    // Everything stays visible while Smart capture is off (so the user sees what it offers); only the
    // capture controls are greyed out. History and deleting copies always work.
    val on = m.autoCapture
    val dim = Modifier.graphicsLayer { alpha = if (on) 1f else 0.5f }

    SettingsGroupLabel(tr(R.string.cap_sources))
    SettingsGroup(Modifier.cascadeIn(2), footer = tr(R.string.cap_sources_footer)) {
        listOf(
            Triple("sms", Icons.Default.Sms, R.string.cap_src_sms_sub),
            Triple("email", Icons.Default.Email, R.string.cap_src_email_sub),
            Triple("upi", Icons.Default.AccountBalanceWallet, R.string.cap_src_upi_sub),
        ).forEachIndexed { i, (wire, icon, sub) ->
            if (i > 0) SettingsDivider()
            SettingsToggleRow(icon, sourceLabel(wire), tr(sub), wire in c.sources, enabled = on, onCheckedChange = { checked ->
                set(c.copy(sources = if (checked) c.sources + wire else c.sources - wire))
            })
        }
        ScopeTag(synced = true)
    }

    SettingsGroupLabel(tr(R.string.cap_new_payments))
    SettingsGroup(Modifier.cascadeIn(3), footer = tr(if (c.mode == "ask") R.string.cap_mode_ask_footer else R.string.cap_mode_auto_footer)) {
        SettingsSegmented(
            options = listOf(
                SegmentOption("auto", tr(R.string.cap_mode_auto), Icons.Default.AddTask),
                SegmentOption("ask", tr(R.string.cap_mode_ask), Icons.Default.NotificationsActive),
            ),
            selected = c.mode,
            onSelect = { if (on) set(c.copy(mode = it)) },
            modifier = dim,
        )
    }

    SettingsGroupLabel(tr(R.string.cap_duplicates))
    SettingsGroup(Modifier.cascadeIn(4), footer = tr(R.string.cap_duplicates_footer)) {
        SettingsSegmented(
            options = listOf(
                SegmentOption("ask", tr(R.string.cap_dup_ask), Icons.Default.HelpOutline),
                SegmentOption("keep", tr(R.string.cap_dup_keep), Icons.Default.ContentCopy),
            ),
            selected = c.duplicates,
            onSelect = { if (on) set(c.copy(duplicates = it)) },
            modifier = dim,
        )
    }

    SettingsGroupLabel(tr(R.string.cap_messages))
    SettingsGroup(Modifier.cascadeIn(5), footer = tr(R.string.cap_keep_footer)) {
        SettingsNavRow(Icons.Default.Inbox, tr(R.string.cap_messages), tr(R.string.cap_messages_sub), onClick = a.openCapturedMessages)
        SettingsDivider()
        SettingsToggleRow(Icons.Default.Save, tr(R.string.cap_keep), tr(R.string.cap_keep_sub), c.keepMessages, onCheckedChange = { set(c.copy(keepMessages = it)) })
        if (c.keepMessages) {
            FlowRow(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 90, 365, 0).forEach { d -> AppChip(retentionLabel(d), selected = c.retentionDays == d, onClick = { set(c.copy(retentionDays = d)) }) }
            }
        }
        SettingsDivider()
        SettingsNavRow(Icons.Default.DeleteSweep, tr(R.string.cap_delete_all), null, destructive = true, chevron = false, onClick = a.deleteCapturedMessages)
        ScopeTag(synced = true)
    }

    SettingsGroupLabel(tr(R.string.cap_blocked))
    SettingsGroup(Modifier.cascadeIn(6), footer = tr(R.string.cap_blocked_footer)) {
        if (c.blockedSenders.isEmpty()) {
            Text(tr(R.string.cap_blocked_none), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        c.blockedSenders.sorted().forEachIndexed { i, sender ->
            if (i > 0) SettingsDivider()
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sender, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                AppIconButton(Icons.Default.Close, tr(R.string.cap_unblock, sender), onClick = { set(c.copy(blockedSenders = c.blockedSenders - sender)) })
            }
        }
    }
}
