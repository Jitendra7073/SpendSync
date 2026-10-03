package com.example.spendsync.ui.profile

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.components.AppConfirmDialog
import com.example.spendsync.ui.components.AppDialog
import com.example.spendsync.ui.components.AppOptionDialog
import com.example.spendsync.ui.components.AppTextField
import com.example.spendsync.ui.components.DialogAction
import com.example.spendsync.ui.components.DialogOption
import com.example.spendsync.ui.components.DialogTone
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.AppLanguage
import com.example.spendsync.utils.TransactionExporter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Edit profile ─────────────────────────────────────────────────────────────

@Composable
internal fun EditProfileDialog(
    currentName: String,
    currentEmail: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }
    val emailValid = email.contains('@') && email.substringAfter('@').contains('.')
    val canSave = name.isNotBlank() && emailValid

    AppDialog(
        onDismiss = onDismiss,
        title = tr(R.string.edit_your_profile),
        message = tr(R.string.this_is_how_you_appear_in),
        icon = Icons.Default.Person,
        primary = DialogAction(tr(R.string.save), { onSave(name.trim(), email.trim()) }, enabled = canSave),
        secondary = DialogAction(tr(R.string.cancel), onDismiss),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(name, { name = it }, label = tr(R.string.name), leadingIcon = Icons.Default.Person)
            AppTextField(
                email, { email = it }, label = tr(R.string.email_address_2),
                keyboardType = KeyboardType.Email,
                isError = email.isNotEmpty() && !emailValid,
                supportingText = if (email.isNotEmpty() && !emailValid) tr(R.string.enter_an_email_like_name_example) else null,
            )
        }
    }
}

// ── Pickers ──────────────────────────────────────────────────────────────────

@Composable
internal fun LanguageDialog(selected: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AppOptionDialog(
        title = tr(R.string.choose_your_language),
        message = tr(R.string.the_whole_app_switches_right_away),
        icon = Icons.Default.Translate,
        options = AppLanguage.entries.map { DialogOption(it.storedName, it.nativeName) },
        selectedKey = selected.storedName,
        onSelect = { key -> onSelect(AppLanguage.fromStored(key)) },
        onDismiss = onDismiss,
        cancelLabel = tr(R.string.cancel),
    )
}

@Composable
internal fun DateFormatDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    val patterns = mapOf(
        "DD / MM / YYYY" to "dd / MM / yyyy",
        "MM / DD / YYYY" to "MM / dd / yyyy",
        "YYYY - MM - DD" to "yyyy - MM - dd",
    )
    AppOptionDialog(
        title = tr(R.string.how_should_dates_look),
        message = tr(R.string.pick_the_style_you_read_fastest),
        options = patterns.map { (key, pattern) ->
            DialogOption(key, today.format(DateTimeFormatter.ofPattern(pattern)), key)
        },
        selectedKey = selected,
        onSelect = onSelect,
        onDismiss = onDismiss,
        cancelLabel = tr(R.string.cancel),
    )
}

// ── Export ───────────────────────────────────────────────────────────────────

@Composable
internal fun ExportDataDialog(financeRepository: FinanceRepository, onDismiss: () -> Unit) {
    var format by remember { mutableStateOf("CSV") }
    var exporting by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun export() {
        exporting = true
        error = null
        scope.launch {
            when (val res = financeRepository.getTransactions(limit = 2000)) {
                is AuthResult.Success -> {
                    val intent = if (format == "CSV") TransactionExporter.exportCsv(context, res.data) else TransactionExporter.exportPdf(context, res.data)
                    context.startActivity(Intent.createChooser(intent, tr(R.string.export_transactions)))
                    exporting = false
                    done = true
                }
                is AuthResult.Error -> {
                    exporting = false
                    error = res.message
                }
            }
        }
    }

    when {
        exporting -> AppDialog(
            onDismiss = {},
            dismissOnOutside = false,
            title = tr(R.string.preparing_your_file),
            message = tr(R.string.this_only_takes_a_moment),
            icon = Icons.Default.FileDownload,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
            }
        }
        error != null -> AppDialog(
            onDismiss = onDismiss,
            title = tr(R.string.couldn_t_export),
            message = error,
            icon = Icons.Default.ErrorOutline,
            tone = DialogTone.Danger,
            primary = DialogAction(tr(R.string.try_again), { export() }),
            secondary = DialogAction(tr(R.string.close), onDismiss),
        )
        done -> AppDialog(
            onDismiss = onDismiss,
            title = tr(R.string.your_file_is_ready),
            message = tr(R.string.choose_where_to_save_or_send, format),
            icon = Icons.Default.CheckCircle,
            tone = DialogTone.Success,
            primary = DialogAction(tr(R.string.done), onDismiss),
        )
        else -> AppDialog(
            onDismiss = onDismiss,
            title = tr(R.string.export_your_transactions),
            message = tr(R.string.pick_a_file_type_you_can),
            icon = Icons.Default.FileDownload,
            primary = DialogAction(tr(R.string.export), { export() }),
            secondary = DialogAction(tr(R.string.cancel), onDismiss),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormatCard("CSV", tr(R.string.opens_in_excel_or_google_sheets), Icons.Default.TableChart, format == "CSV") { format = "CSV" }
                FormatCard("PDF", tr(R.string.easy_to_read_and_print), Icons.Default.PictureAsPdf, format == "PDF") { format = "PDF" }
            }
        }
    }
}

@Composable
private fun FormatCard(title: String, description: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.10f) else scheme.surfaceVariant.copy(alpha = 0.5f))
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant), RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (selected) scheme.primary else scheme.onSurface)
            Text(description, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
    }
}

// ── Clear / delete ───────────────────────────────────────────────────────────

@Composable
internal fun ClearDataConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var cleared by remember { mutableStateOf(false) }
    if (!cleared) {
        AppDialog(
            onDismiss = onDismiss,
            title = tr(R.string.clear_data_on_this_phone),
            message = tr(R.string.this_removes_saved_categories_and_temporary),
            icon = Icons.Default.DeleteSweep,
            tone = DialogTone.Danger,
            primary = DialogAction(tr(R.string.clear), { onConfirm(); cleared = true }),
            secondary = DialogAction(tr(R.string.cancel), onDismiss),
        )
    } else {
        AppDialog(
            onDismiss = onDismiss,
            title = tr(R.string.all_clear),
            message = tr(R.string.the_data_on_this_phone_has),
            icon = Icons.Default.CheckCircle,
            tone = DialogTone.Success,
            primary = DialogAction(tr(R.string.done), onDismiss),
        )
    }
}

@Composable
internal fun DeleteAccountWarningDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    loading: Boolean = false,
    error: String? = null,
) {
    var typed by remember { mutableStateOf("") }
    val valid = typed.trim() == "DELETE"
    AppDialog(
        onDismiss = onDismiss,
        title = tr(R.string.delete_your_account),
        message = error ?: tr(R.string.your_account_and_all_your_records),
        icon = Icons.Default.DeleteForever,
        tone = DialogTone.Danger,
        dismissOnOutside = !loading,
        primary = DialogAction(tr(R.string.delete_account), onDelete, enabled = valid, loading = loading),
        secondary = DialogAction(tr(R.string.keep_my_account), onDismiss),
    ) {
        AppTextField(
            typed, { typed = it },
            label = tr(R.string.type_delete_to_confirm),
            placeholder = "DELETE",
            isError = typed.isNotEmpty() && !valid,
        )
    }
}

// ── Policy / explainers ──────────────────────────────────────────────────────

@Composable
internal fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AppDialog(
        onDismiss = onDismiss,
        title = tr(R.string.your_privacy),
        message = tr(R.string.here_is_how_spendsync_treats_your),
        icon = Icons.Default.PrivacyTip,
        primary = DialogAction(tr(R.string.got_it), onDismiss),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PolicyPoint(tr(R.string.what_we_keep), tr(R.string.your_transactions_and_settings_they_are))
            PolicyPoint(tr(R.string.who_can_see_it), tr(R.string.only_you_we_never_sell_or))
            PolicyPoint(tr(R.string.your_pin), tr(R.string.it_stays_on_this_phone_and))
            PolicyPoint(tr(R.string.your_control), tr(R.string.you_can_clear_data_on_this))
        }
    }
}

@Composable
private fun PolicyPoint(title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        Text(body, fontSize = 13.sp, lineHeight = 19.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
internal fun AutoCaptureExplainerDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        onDismiss = onDismiss,
        title = tr(R.string.add_payments_automatically),
        message = tr(R.string.spendsync_can_notice_payment_alerts_from),
        icon = Icons.Default.NotificationsActive,
        primary = DialogAction(tr(R.string.continue_label), onContinue),
        secondary = DialogAction(tr(R.string.not_now), onDismiss),
    )
}

@Composable
internal fun VisibilityDurationDialog(selectedSeconds: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val choices = listOf(30 to tr(R.string.s_30_seconds), 60 to tr(R.string.s_1_minute), 300 to tr(R.string.s_5_minutes), 900 to tr(R.string.s_15_minutes))
    AppOptionDialog(
        title = tr(R.string.how_long_should_amounts_stay_visible),
        message = tr(R.string.after_you_enter_your_pin_amounts),
        options = choices.map { (seconds, label) -> DialogOption(seconds.toString(), label) },
        selectedKey = (choices.firstOrNull { it.first == selectedSeconds }?.first ?: 60).toString(),
        onSelect = { onSelect(it.toInt()) },
        onDismiss = onDismiss,
        cancelLabel = tr(R.string.cancel),
    )
}
