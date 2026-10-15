package com.example.spendsync.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.model.CapturedMessageDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.data.repository.SettingField
import com.example.spendsync.data.repository.SettingsSynchronizer
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppChip
import com.example.spendsync.ui.components.AppSheet
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Skeleton
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.settings.sourceLabel
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.theme.SemanticError
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
import com.example.spendsync.utils.formatInr
import com.example.spendsync.utils.maskAmountsInText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Smart capture's history: every bank SMS / email / UPI alert it read, what it found, and what it did
 * (added, duplicate, needs review, ignored, waiting to sync). [transactionId] limits it to the message(s)
 * behind one transaction. Amounts follow the PIN-masking rule, in the list and in the message text.
 */
@Composable
fun CapturedMessagesSheet(
    finance: FinanceRepository,
    store: SessionDataStore,
    amountVisibility: AmountVisibilityState,
    onDismiss: () -> Unit,
    transactionId: String? = null,
) {
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<CapturedMessageDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<CapturedMessageDto?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load(force: Boolean) {
        when (val r = finance.getCapturedMessages(filter, transactionId, force)) {
            is AuthResult.Success -> { messages = r.data; error = null }
            is AuthResult.Error -> { error = r.message; if (messages == null) messages = emptyList() }
        }
    }
    LaunchedEffect(filter) { messages = null; load(false) }

    fun run(block: suspend () -> AuthResult<*>) {
        scope.launch {
            busy = true
            when (val r = block()) {
                is AuthResult.Success -> { open = null; load(true) }
                is AuthResult.Error -> error = r.message
            }
            busy = false
        }
    }

    val current = open
    AppSheet(onDismiss = onDismiss, title = tr(R.string.cap_messages), subtitle = if (current == null) tr(R.string.cap_messages_sheet_sub) else null) {
        error?.let { Text(it, color = SemanticError, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
        if (current != null) {
            MessageDetail(current, amountVisibility, busy, onBack = { open = null }, onAction = { action ->
                run {
                    when (action) {
                        "delete" -> finance.deleteCapturedMessage(current.id)
                        "block" -> {
                            val s = store.captureSettings.first()
                            store.updateCaptureSettings(s.copy(blockedSenders = s.blockedSenders + current.sender))
                            SettingsSynchronizer(store, finance).changed(SettingField.Capture)
                            AuthResult.Success(Unit)
                        }
                        else -> finance.actOnCapturedMessage(current.id, action)
                    }
                }
            })
            return@AppSheet
        }

        if (transactionId == null) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to R.string.cap_f_all, "review" to R.string.cap_st_review, "added" to R.string.cap_st_added, "duplicate" to R.string.cap_st_duplicate, "ignored" to R.string.cap_st_ignored)
                    .forEach { (key, label) -> AppChip(tr(label), selected = filter == key, onClick = { filter = key }) }
            }
        }

        val list = messages
        Skeleton(loading = list == null) {
            Column {
                val rows = list ?: List(4) { placeholder(it) }
                if (rows.isEmpty()) {
                    Text(tr(R.string.cap_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                }
                var lastDay: String? = null
                rows.forEach { m ->
                    val day = day(m.receivedAt)
                    if (day != lastDay) {
                        lastDay = day
                        Text(day, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                    MessageRow(m, amountVisibility, onClick = { if (list != null) open = m })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun MessageRow(m: CapturedMessageDto, vis: AmountVisibilityState, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(sourceIcon(m.source), contentDescription = sourceLabel(m.source), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(m.payee ?: m.sender, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${m.sender} · ${time(m.receivedAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            m.amount?.toDoubleOrNull()?.let { a ->
                Text(signed(m, maskedMoney(a, vis)), fontWeight = FontWeight.SemiBold, color = amountColor(m))
            }
            StatusChip(m)
        }
    }
}

@Composable
private fun MessageDetail(m: CapturedMessageDto, vis: AmountVisibilityState, busy: Boolean, onBack: () -> Unit, onAction: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(sourceIcon(m.source), contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            Text("${sourceLabel(m.source)} · ${m.sender}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
            StatusChip(m)
        }
        Text("${day(m.receivedAt)}, ${time(m.receivedAt)}", fontSize = 12.sp, color = scheme.onSurfaceVariant)
        Text(
            if (m.body.isBlank()) tr(R.string.cap_no_copy) else maskAmountsInText(m.body, vis.isMaskingEnabled, vis.isVisible).text,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().background(scheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp),
        )
        Text(statusExplanation(m), fontSize = 13.sp, color = scheme.onSurfaceVariant)

        val found = listOfNotNull(
            m.amount?.toDoubleOrNull()?.let { tr(R.string.cap_d_amount) to signed(m, maskedMoney(it, vis)) },
            m.payee?.let { tr(if (m.type == "credit") R.string.cap_d_from else R.string.cap_d_to) to it },
            m.refNo?.let { tr(R.string.cap_d_ref) to it },
            m.last4?.let { tr(R.string.cap_d_account) to "••$it" },
            m.balance?.toDoubleOrNull()?.let { tr(R.string.cap_d_balance) to maskedMoney(it, vis) },
        )
        if (found.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr(R.string.cap_d_found), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
                found.forEach { (k, v) ->
                    Row { Text(k, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f)); Text(v, fontWeight = FontWeight.Medium) }
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                m.status == "review" && m.reason == "possible_duplicate" -> {
                    AppButton(tr(R.string.cap_same), { onAction("same") }, loading = busy, size = ButtonSize.Small)
                    AppButton(tr(R.string.cap_different), { onAction("different") }, enabled = !busy, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
                }
                m.status == "review" -> {
                    AppButton(tr(R.string.cap_add), { onAction("add") }, loading = busy, size = ButtonSize.Small)
                    AppButton(tr(R.string.cap_ignore), { onAction("ignore") }, enabled = !busy, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
                }
                (m.status == "ignored" || m.status == "duplicate") && m.type != null ->
                    AppButton(tr(R.string.cap_add_anyway), { onAction("add") }, loading = busy, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
            }
        }
        if (m.status != "pending") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(tr(R.string.cap_block_sender), { onAction("block") }, enabled = !busy, variant = ButtonVariant.Outline, size = ButtonSize.Small)
                AppButton(tr(R.string.cap_delete_copy), { onAction("delete") }, enabled = !busy, variant = ButtonVariant.DangerText, size = ButtonSize.Small)
            }
        }
        AppButton(tr(R.string.back), onBack, variant = ButtonVariant.Text, size = ButtonSize.Small)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusChip(m: CapturedMessageDto) {
    val (label, color) = when (m.status) {
        "added" -> R.string.cap_st_added to incomeColor()
        "duplicate" -> R.string.cap_st_duplicate to MaterialTheme.colorScheme.onSurfaceVariant
        "review" -> R.string.cap_st_review to MaterialTheme.colorScheme.primary
        "pending" -> R.string.cap_st_pending to MaterialTheme.colorScheme.tertiary
        else -> R.string.cap_st_ignored to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(tr(label), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
}

private fun statusExplanation(m: CapturedMessageDto): String = tr(
    when (m.status) {
        "added" -> if (m.transactionId == null) R.string.cap_x_added_deleted else R.string.cap_x_added
        "duplicate" -> R.string.cap_x_duplicate
        "review" -> if (m.reason == "possible_duplicate") R.string.cap_x_review_dup else R.string.cap_x_review_ask
        "pending" -> R.string.cap_x_pending
        else -> when (m.reason) {
            "failed" -> R.string.cap_r_failed
            "future" -> R.string.cap_r_future
            "request" -> R.string.cap_r_request
            "promo" -> R.string.cap_r_promo
            "dismissed" -> R.string.cap_r_dismissed
            else -> R.string.cap_r_not_payment
        }
    },
)

private fun sourceIcon(source: String) = when (source) {
    "sms" -> Icons.Default.Sms
    "email" -> Icons.Default.Email
    else -> Icons.Default.AccountBalanceWallet
}

private fun maskedMoney(amount: Double, vis: AmountVisibilityState) = maskAmountsInText(formatInr(amount), vis.isMaskingEnabled, vis.isVisible).text

private fun signed(m: CapturedMessageDto, money: String) = when (m.type) {
    "credit" -> "+$money"
    "debit" -> "−$money"
    else -> money
}

@Composable
private fun amountColor(m: CapturedMessageDto): Color = when (m.type) {
    "credit" -> incomeColor()
    "debit" -> expenseColor()
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private fun local(iso: String) = runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()) }.getOrNull()
private fun day(iso: String) = local(iso)?.format(DAY) ?: ""
private fun time(iso: String) = local(iso)?.format(TIME) ?: ""

private fun placeholder(i: Int) = CapturedMessageDto(
    id = "p$i", source = "sms", sender = "VM-BANKXX", body = "", receivedAt = Instant.now().toString(),
    amount = "1000.00", type = "debit", payee = "Placeholder payee", status = "added",
)
