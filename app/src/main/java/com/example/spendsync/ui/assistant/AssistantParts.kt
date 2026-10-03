package com.example.spendsync.ui.assistant

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.BuildConfig
import com.example.spendsync.R
import com.example.spendsync.data.assistant.ActivityStep
import com.example.spendsync.data.assistant.ConversationSummary
import com.example.spendsync.data.assistant.StepKind
import com.example.spendsync.data.assistant.StepState
import com.example.spendsync.data.assistant.SupportTicketSummary
import com.example.spendsync.data.assistant.formatElapsed
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppChip
import com.example.spendsync.ui.components.AppDialog
import com.example.spendsync.ui.components.AppSheet
import com.example.spendsync.ui.components.AppTextField
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.DialogAction
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.LanguageManager
import com.example.spendsync.ui.i18n.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Transparency: what the assistant is doing ────────────────────────────────

private fun stepLabel(step: ActivityStep): String = when (step.kind) {
    StepKind.Understand -> tr(R.string.asst_step_understand)
    StepKind.Model -> tr(R.string.asst_step_model, step.arg)
    StepKind.Backup -> if (step.arg.isBlank()) tr(R.string.asst_step_retry) else tr(R.string.asst_step_backup, step.arg)
    StepKind.Tool -> if (step.arg == "propose_entry") tr(R.string.asst_step_prepare) else toolLabel(step.arg)
    StepKind.Prepare -> tr(R.string.asst_step_prepare)
    StepKind.Write -> tr(R.string.asst_step_write)
}

/** One line per thing the assistant did, with a spinner while it runs and a tick when it is done. */
@Composable
internal fun ActivityPanel(steps: List<ActivityStep>, footer: String? = null, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    Column(
        modifier
            .animateContentSize()
            .clip(shape)
            .background(scheme.surface)
            .border(0.5.dp, scheme.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        steps.forEach { step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    when (step.state) {
                        StepState.Running -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = scheme.primary)
                        StepState.Done -> Icon(Icons.Default.Check, null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                        StepState.Failed -> Icon(Icons.Default.Close, null, tint = scheme.error, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stepLabel(step), fontSize = 13.sp,
                    fontWeight = if (step.state == StepState.Running) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (step.state == StepState.Running) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
        if (footer != null) Text(footer, fontSize = 11.sp, color = scheme.onSurfaceVariant)
    }
}

/** A running "2.4 s" while the answer is being prepared. */
@Composable
internal fun rememberElapsed(sinceMs: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sinceMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(100)
        }
    }
    return formatElapsed((now - sinceMs).coerceAtLeast(0))
}

// ── Small actions under messages ─────────────────────────────────────────────

@Composable
internal fun MiniAction(icon: ImageVector, description: String, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color? = null) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint ?: scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun UserMessageActions(onEdit: () -> Unit, onCopy: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (enabled) MiniAction(Icons.Default.Edit, tr(R.string.asst_edit), onEdit)
        MiniAction(Icons.Default.ContentCopy, tr(R.string.asst_copy), onCopy)
    }
}

@Composable
internal fun AnswerActions(feedback: String, onCopy: () -> Unit, onUp: () -> Unit, onDown: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        MiniAction(Icons.Default.ContentCopy, tr(R.string.asst_copy), onCopy)
        MiniAction(
            if (feedback == "up") Icons.Default.ThumbUp else Icons.Outlined.ThumbUp, tr(R.string.asst_helpful), onUp,
            tint = if (feedback == "up") scheme.primary else null,
        )
        MiniAction(
            if (feedback == "down") Icons.Default.ThumbDown else Icons.Outlined.ThumbDown, tr(R.string.asst_not_helpful), onDown,
            tint = if (feedback == "down") scheme.error else null,
        )
    }
}

// ── Thumbs-down: why? ────────────────────────────────────────────────────────

private val FEEDBACK_REASONS = listOf(
    "wrong" to R.string.asst_fb_wrong,
    "not_understood" to R.string.asst_fb_not_understood,
    "no_action" to R.string.asst_fb_no_action,
    "too_slow" to R.string.asst_fb_too_slow,
    "too_long" to R.string.asst_fb_too_long,
    "too_short" to R.string.asst_fb_too_short,
    "wrong_language" to R.string.asst_fb_wrong_language,
    "confusing" to R.string.asst_fb_confusing,
    "other" to R.string.asst_fb_other,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackDialog(onDismiss: () -> Unit, onSend: (List<String>, String) -> Unit) {
    var picked by remember { mutableStateOf(setOf<String>()) }
    var comment by remember { mutableStateOf("") }
    AppDialog(
        onDismiss = onDismiss,
        title = tr(R.string.asst_fb_title),
        message = tr(R.string.asst_fb_hint),
        primary = DialogAction(tr(R.string.asst_fb_send), { onSend(picked.toList(), comment.trim()) }, enabled = picked.isNotEmpty() || comment.isNotBlank()),
        secondary = DialogAction(tr(R.string.cancel), onDismiss),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FEEDBACK_REASONS.forEach { (code, res) ->
                AppChip(
                    tr(res), selected = code in picked,
                    onClick = { picked = if (code in picked) picked - code else picked + code },
                    role = Role.Checkbox,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        AppTextField(
            value = comment, onValueChange = { comment = it.take(500) },
            label = tr(R.string.asst_fb_comment), singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Chat history ─────────────────────────────────────────────────────────────

@Composable
internal fun HistorySheet(
    chats: List<ConversationSummary>,
    currentId: String,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AppSheet(onDismiss = onDismiss, title = tr(R.string.asst_history), subtitle = tr(R.string.asst_history_sub)) {
        if (chats.isEmpty()) {
            Text(tr(R.string.asst_history_empty), fontSize = 14.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        }
        chats.forEach { chat ->
            val current = chat.id == currentId
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (current) scheme.primary.copy(alpha = 0.10f) else scheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable(role = Role.Button) { onOpen(chat.id) }
                    .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        chat.title.ifBlank { tr(R.string.asst_history_untitled) }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface, maxLines = 2,
                    )
                    Text(
                        tr(R.string.asst_history_meta, chat.messages, DateUtils.getRelativeTimeSpanString(chat.lastAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()),
                        fontSize = 12.sp, color = scheme.onSurfaceVariant,
                    )
                }
                MiniAction(Icons.Default.Delete, tr(R.string.asst_history_delete), { onDelete(chat.id) }, tint = scheme.error)
            }
        }
        if (chats.isNotEmpty()) {
            Spacer(Modifier.size(12.dp))
            AppButton(tr(R.string.asst_history_clear_all), onClick = onClearAll, variant = ButtonVariant.Outline, fullWidth = true)
        }
    }
}

// ── Support ──────────────────────────────────────────────────────────────────

private val SUPPORT_CATEGORIES = listOf(
    Triple("bug", R.string.asst_sup_cat_bug, listOf(R.string.asst_sup_q_crash, R.string.asst_sup_q_button)),
    Triple("wrong_data", R.string.asst_sup_cat_data, listOf(R.string.asst_sup_q_balance, R.string.asst_sup_q_missing)),
    Triple("assistant", R.string.asst_sup_cat_assistant, listOf(R.string.asst_sup_q_understand, R.string.asst_sup_q_wrong_answer, R.string.asst_sup_q_no_entry)),
    Triple("account", R.string.asst_sup_cat_account, listOf(R.string.asst_sup_q_signin, R.string.asst_sup_q_settings)),
    Triple("feature", R.string.asst_sup_cat_feature, listOf(R.string.asst_sup_q_feature)),
    Triple("other", R.string.asst_sup_cat_other, emptyList()),
)

private fun categoryLabel(code: String): String =
    tr(SUPPORT_CATEGORIES.firstOrNull { it.first == code }?.second ?: R.string.asst_sup_cat_other)

/** The "Contact support" box: pick what happened, add details, send. Also lists the user's earlier reports. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SupportSheet(
    hasChat: Boolean,
    submit: suspend (category: String, message: String, includeChat: Boolean) -> String?,
    loadTickets: suspend () -> List<SupportTicketSummary>?,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var includeChat by remember { mutableStateOf(hasChat) }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var sentRef by remember { mutableStateOf<String?>(null) }
    var tickets by remember { mutableStateOf<List<SupportTicketSummary>?>(null) }
    LaunchedEffect(sentRef) { tickets = loadTickets() }

    AppSheet(onDismiss = onDismiss, title = tr(R.string.asst_sup_title), subtitle = tr(R.string.asst_sup_intro)) {
        if (sentRef != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.primary.copy(alpha = 0.10f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(tr(R.string.asst_sup_sent_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                Text(tr(R.string.asst_sup_sent_body, sentRef.orEmpty()), fontSize = 14.sp, color = scheme.onSurface)
            }
            Spacer(Modifier.size(12.dp))
            AppButton(tr(R.string.asst_sup_done), onClick = onDismiss, fullWidth = true)
        } else {
            Text(tr(R.string.asst_sup_what), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
            Spacer(Modifier.size(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SUPPORT_CATEGORIES.forEach { (code, res, _) ->
                    AppChip(tr(res), selected = category == code, onClick = { category = code })
                }
            }
            val quick = SUPPORT_CATEGORIES.firstOrNull { it.first == category }?.third.orEmpty()
            if (quick.isNotEmpty()) {
                Spacer(Modifier.size(12.dp))
                Text(tr(R.string.asst_sup_quick), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.forEach { res ->
                        val line = tr(res)
                        AppChip(line, selected = message.startsWith(line), onClick = { message = if (message.isBlank()) "$line. " else "$line. $message" }, role = Role.Button)
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            AppTextField(
                value = message, onValueChange = { message = it.take(2000) },
                label = tr(R.string.asst_sup_message), singleLine = false, modifier = Modifier.fillMaxWidth(),
                supportingText = tr(R.string.asst_sup_message_hint),
            )
            if (hasChat) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { includeChat = !includeChat }.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeChat, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr(R.string.asst_sup_include_chat), fontSize = 14.sp, color = scheme.onSurface)
                }
            }
            if (failed) Text(tr(R.string.asst_sup_failed), fontSize = 13.sp, color = scheme.error, modifier = Modifier.padding(vertical = 6.dp))
            Spacer(Modifier.size(8.dp))
            AppButton(
                tr(R.string.asst_sup_send),
                onClick = {
                    val c = category ?: return@AppButton
                    sending = true; failed = false
                    scope.launch {
                        val ref = submit(c, message.trim(), includeChat && hasChat)
                        sending = false
                        if (ref != null) sentRef = ref else failed = true
                    }
                },
                loading = sending,
                enabled = category != null && message.trim().length >= 5,
                fullWidth = true,
            )
            Text(tr(R.string.asst_sup_privacy), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }

        val list = tickets
        if (!list.isNullOrEmpty()) {
            Spacer(Modifier.size(20.dp))
            Text(tr(R.string.asst_sup_my_reports), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
            Spacer(Modifier.size(8.dp))
            list.forEach { t ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                        .border(BorderStroke(0.5.dp, scheme.outlineVariant), RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${t.ref} · ${categoryLabel(t.category)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Text(
                            tr(when (t.status) { "resolved" -> R.string.asst_sup_status_resolved; "in_progress" -> R.string.asst_sup_status_progress; else -> R.string.asst_sup_status_open }),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (t.status == "resolved") scheme.primary else scheme.onSurfaceVariant,
                        )
                    }
                    Text(t.message, fontSize = 13.sp, color = scheme.onSurfaceVariant, maxLines = 2)
                }
            }
        }
    }
}

/** App and device facts that go with a report. */
internal fun deviceSummary(): Triple<String, String, String> =
    Triple("${Build.MANUFACTURER} ${Build.MODEL}".trim(), "Android ${Build.VERSION.RELEASE}", BuildConfig.VERSION_NAME)

internal fun currentLanguageName(): String = LanguageManager.current.storedName
