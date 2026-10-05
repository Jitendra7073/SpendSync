package com.example.spendsync.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.spendsync.R
import com.example.spendsync.data.assistant.ASSISTANT_LOCKED_ACTIONS
import com.example.spendsync.data.assistant.AssistantPrefs
import com.example.spendsync.data.assistant.AssistantToolInfo
import com.example.spendsync.ui.components.AppConfirmDialog
import com.example.spendsync.ui.components.AppOptionDialog
import com.example.spendsync.ui.components.AppTextField
import com.example.spendsync.ui.components.DialogOption
import com.example.spendsync.ui.i18n.tr
import kotlinx.coroutines.delay

/** One model the server can use, as listed on Settings -> Assistant. */
data class AssistantModelInfo(val id: String, val label: String, val healthy: Boolean)

private fun toolIcon(tool: AssistantToolInfo): ImageVector = when (tool) {
    AssistantToolInfo.SearchHelp -> Icons.Default.Help
    AssistantToolInfo.GetBalance, AssistantToolInfo.GetSpendingSummary, AssistantToolInfo.GetTopMerchants -> Icons.Default.Star
    AssistantToolInfo.SearchTransactions -> Icons.Default.Search
    AssistantToolInfo.GetBudgetStatus, AssistantToolInfo.GetPlanStatus, AssistantToolInfo.ListHolds -> Icons.Default.Notifications
    AssistantToolInfo.GetSettings -> Icons.Default.SettingsSuggest
    AssistantToolInfo.ProposeEntry, AssistantToolInfo.PrepareFollowup, AssistantToolInfo.ShareMessage -> Icons.Default.Edit
    AssistantToolInfo.OpenScreen -> Icons.Default.AutoAwesome
}

private fun toolTitle(tool: AssistantToolInfo): String = tr(
    when (tool) {
        AssistantToolInfo.SearchHelp -> R.string.asst_tool_search_help
        AssistantToolInfo.GetBalance -> R.string.asst_tool_get_balance
        AssistantToolInfo.GetSpendingSummary -> R.string.asst_tool_get_spending_summary
        AssistantToolInfo.SearchTransactions -> R.string.asst_tool_search_transactions
        AssistantToolInfo.GetBudgetStatus -> R.string.asst_tool_get_budget_status
        AssistantToolInfo.GetPlanStatus -> R.string.asst_tool_get_plan_status
        AssistantToolInfo.ListHolds -> R.string.asst_tool_list_holds
        AssistantToolInfo.GetTopMerchants -> R.string.asst_tool_get_top_merchants
        AssistantToolInfo.GetSettings -> R.string.asst_tool_get_settings
        AssistantToolInfo.ProposeEntry -> R.string.asst_tool_propose_entry
        AssistantToolInfo.PrepareFollowup -> R.string.asst_tool_prepare_followup
        AssistantToolInfo.ShareMessage -> R.string.asst_tool_share_message
        AssistantToolInfo.OpenScreen -> R.string.asst_tool_open_screen
    },
)

private fun toolSub(tool: AssistantToolInfo): String = tr(
    when (tool) {
        AssistantToolInfo.SearchHelp -> R.string.asst_tool_search_help_sub
        AssistantToolInfo.GetBalance -> R.string.asst_tool_get_balance_sub
        AssistantToolInfo.GetSpendingSummary -> R.string.asst_tool_get_spending_summary_sub
        AssistantToolInfo.SearchTransactions -> R.string.asst_tool_search_transactions_sub
        AssistantToolInfo.GetBudgetStatus -> R.string.asst_tool_get_budget_status_sub
        AssistantToolInfo.GetPlanStatus -> R.string.asst_tool_get_plan_status_sub
        AssistantToolInfo.ListHolds -> R.string.asst_tool_list_holds_sub
        AssistantToolInfo.GetTopMerchants -> R.string.asst_tool_get_top_merchants_sub
        AssistantToolInfo.GetSettings -> R.string.asst_tool_get_settings_sub
        AssistantToolInfo.ProposeEntry -> R.string.asst_tool_propose_entry_sub
        AssistantToolInfo.PrepareFollowup -> R.string.asst_tool_prepare_followup_sub
        AssistantToolInfo.ShareMessage -> R.string.asst_tool_share_message_sub
        AssistantToolInfo.OpenScreen -> R.string.asst_tool_open_screen_sub
    },
)

private fun lockedTitle(id: String): String = tr(
    when (id) {
        "clear_data" -> R.string.asst_locked_clear
        "sign_out" -> R.string.asst_locked_signout
        else -> R.string.asst_locked_delete
    },
)

fun assistantSummary(m: SettingsModel): String =
    if (!m.assistantEnabled) tr(R.string.off)
    else tr(R.string.asst_summary_on, AssistantToolInfo.entries.count { it.id !in m.assistant.disabledTools })

@Composable
internal fun AssistantPage(m: SettingsModel, a: SettingsActions) {
    val p = m.assistant
    var pickModel by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { a.refreshAssistantStatus() }

    val autoLabel = tr(R.string.asst_model_auto)
    val modelLabel = if (p.model == "auto") autoLabel else m.assistantModels?.firstOrNull { it.id == p.model }?.label ?: p.model

    SettingsGroup(Modifier.cascadeIn(0), footer = tr(R.string.asst_privacy_note)) {
        SettingsToggleRow(
            Icons.Default.AutoAwesome, tr(R.string.assistant_setting_title), tr(R.string.assistant_setting_sub),
            m.assistantEnabled, onCheckedChange = a.setAssistant,
        )
        ScopeTag(synced = false)
    }

    SettingsGroupLabel(tr(R.string.asst_group_use))
    SettingsGroup(Modifier.cascadeIn(1), footer = tr(R.string.asst_use_footer)) {
        AssistantToolInfo.entries.forEachIndexed { i, tool ->
            if (i > 0) SettingsDivider()
            SettingsToggleRow(
                toolIcon(tool), toolTitle(tool), toolSub(tool),
                checked = tool.id !in p.disabledTools,
                enabled = m.assistantEnabled,
                onCheckedChange = { on ->
                    a.setAssistantPrefs(p.copy(disabledTools = if (on) p.disabledTools - tool.id else p.disabledTools + tool.id))
                },
            )
        }
        ScopeTag(synced = true)
    }

    SettingsGroupLabel(tr(R.string.asst_group_locked))
    SettingsGroup(Modifier.cascadeIn(2), footer = tr(R.string.asst_locked_footer)) {
        ASSISTANT_LOCKED_ACTIONS.forEachIndexed { i, id ->
            if (i > 0) SettingsDivider()
            SettingsNavRow(Icons.Default.Lock, lockedTitle(id), value = tr(R.string.asst_locked_value), chevron = false, enabled = false, onClick = {})
        }
    }

    SettingsGroupLabel(tr(R.string.asst_group_model))
    SettingsGroup(Modifier.cascadeIn(3), footer = tr(R.string.asst_model_footer)) {
        SettingsNavRow(
            Icons.Default.AutoAwesome, tr(R.string.asst_model_row), value = modelLabel,
            enabled = m.assistantEnabled, onClick = { pickModel = true },
        )
        ScopeTag(synced = true)
    }

    SettingsGroupLabel(tr(R.string.asst_group_style))
    SettingsGroup(Modifier.cascadeIn(4)) {
        SettingsSegmented(
            options = AssistantPrefs.STYLES.map { SegmentOption(it, tr(styleRes(it)), Icons.Default.Edit) },
            selected = p.style,
            onSelect = { a.setAssistantPrefs(p.copy(style = it)) },
        )
        ScopeTag(synced = true)
    }

    SettingsGroupLabel(tr(R.string.asst_group_tone))
    SettingsGroup(Modifier.cascadeIn(5)) {
        SettingsSegmented(
            options = AssistantPrefs.TONES.map { SegmentOption(it, tr(toneRes(it)), Icons.Default.Edit) },
            selected = p.tone,
            onSelect = { a.setAssistantPrefs(p.copy(tone = it)) },
        )
        ScopeTag(synced = true)
    }

    SettingsGroupLabel(tr(R.string.asst_group_instructions))
    SettingsGroup(Modifier.cascadeIn(6)) {
        InstructionsField(p, a)
        ScopeTag(synced = true)
    }

    SettingsGroupLabel(tr(R.string.asst_group_history))
    SettingsGroup(Modifier.cascadeIn(7)) {
        SettingsNavRow(
            Icons.Default.DeleteSweep, tr(R.string.asst_clear_history), tr(R.string.asst_clear_history_sub),
            destructive = true, onClick = { confirmClear = true },
        )
        ScopeTag(synced = false)
    }

    if (pickModel) {
        val options = buildList {
            add(DialogOption("auto", autoLabel, tr(R.string.asst_model_auto_desc)))
            m.assistantModels?.forEach { add(DialogOption(it.id, it.label, if (it.healthy) null else tr(R.string.asst_model_resting))) }
        }
        AppOptionDialog(
            title = tr(R.string.asst_model_title),
            message = if (m.assistantModels == null) tr(R.string.asst_model_unavailable) else null,
            options = options,
            selectedKey = p.model,
            onSelect = { a.setAssistantPrefs(p.copy(model = it)); pickModel = false },
            onDismiss = { pickModel = false },
            cancelLabel = tr(R.string.cancel),
        )
    }
    if (confirmClear) {
        AppConfirmDialog(
            title = tr(R.string.assistant_clear_title),
            message = tr(R.string.assistant_clear_body),
            confirmLabel = tr(R.string.assistant_clear),
            cancelLabel = tr(R.string.cancel),
            destructive = true,
            onConfirm = { a.clearAssistantHistory(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
}

/** Saves a moment after typing stops, so every keystroke does not trigger a sync. */
@Composable
private fun InstructionsField(p: AssistantPrefs, a: SettingsActions) {
    var text by remember(p.instructions) { mutableStateOf(p.instructions) }
    val latest by rememberUpdatedState(p)
    LaunchedEffect(text) {
        if (text != latest.instructions) {
            delay(700)
            a.setAssistantPrefs(latest.copy(instructions = text.take(AssistantPrefs.MAX_INSTRUCTIONS)))
        }
    }
    AppTextField(
        value = text,
        onValueChange = { text = it.take(AssistantPrefs.MAX_INSTRUCTIONS) },
        label = tr(R.string.asst_instructions_label),
        placeholder = tr(R.string.asst_instructions_hint),
        singleLine = false,
        supportingText = tr(R.string.asst_instructions_support, text.length, AssistantPrefs.MAX_INSTRUCTIONS),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}

private fun styleRes(key: String): Int = when (key) {
    "short" -> R.string.asst_style_short
    "detailed" -> R.string.asst_style_detailed
    else -> R.string.asst_style_balanced
}

private fun toneRes(key: String): Int = when (key) {
    "simple" -> R.string.asst_tone_simple
    "professional" -> R.string.asst_tone_professional
    else -> R.string.asst_tone_friendly
}
