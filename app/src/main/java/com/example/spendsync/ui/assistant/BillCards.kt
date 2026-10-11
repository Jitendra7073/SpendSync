package com.example.spendsync.ui.assistant

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spendsync.R
import com.example.spendsync.data.assistant.EmailBillsProposal
import com.example.spendsync.data.assistant.EmailCard
import com.example.spendsync.data.assistant.EmailCardState
import com.example.spendsync.data.billai.BillAi
import com.example.spendsync.data.bills.BillShare
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.model.BillDto
import com.example.spendsync.data.remote.model.BillReadStatusDto
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.billai.BillExplainSheet
import com.example.spendsync.ui.bills.BillViewer
import com.example.spendsync.ui.bills.BillsSection
import com.example.spendsync.ui.bills.billImageUrl
import com.example.spendsync.ui.components.*
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.i18n.categoryLabel
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.MaskableAmountText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class Row(val tx: TransactionDto, val bills: List<BillDto>, val read: BillReadStatusDto?)

/** Matches from find_bills: thumb, shop, date · category, amount, AI line; Open / Explain / Attach; Email to me / Share. */
@Composable
internal fun BillResultsCard(transactionIds: List<String>, key: String, financeRepository: FinanceRepository, sessionDataStore: SessionDataStore, vis: AmountVisibilityState) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember(transactionIds) { mutableStateOf<List<Row>?>(null) }
    var viewing by remember { mutableStateOf<Row?>(null) }
    var explaining by remember { mutableStateOf<BillReadStatusDto?>(null) }
    var attaching by remember { mutableStateOf<TransactionDto?>(null) }
    var emailing by remember { mutableStateOf<EmailBillsProposal?>(null) }
    var sharing by remember { mutableStateOf<String?>(null) }
    val masked = vis.isMaskingEnabled && !vis.isVisible

    suspend fun load() {
        rows = transactionIds.mapNotNull { id ->
            val tx = (financeRepository.getTransaction(id) as? AuthResult.Success)?.data ?: return@mapNotNull null
            val bills = (financeRepository.listBills(id) as? AuthResult.Success)?.data.orEmpty()
            val read = if (bills.isNotEmpty()) (financeRepository.billRead(id) as? AuthResult.Success)?.data else null
            Row(tx, bills, read)
        }
    }
    LaunchedEffect(transactionIds) { load() }

    Column(Modifier.fillMaxWidth().glassCard(16).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val list = rows
        if (list == null) Skeleton(loading = true) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(2) { SkeletonBlock(height = 56.dp) } } }
        else list.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.semantics { contentDescription = tr(R.string.abl_cd_row, r.tx.merchant, r.tx.createdAt.take(10)) }) {
                val first = r.bills.firstOrNull()
                if (first != null) AsyncImage(
                    model = billImageUrl(first, masked, Build.VERSION.SDK_INT, thumb = true), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).then(if (masked && Build.VERSION.SDK_INT >= 31) Modifier.blur(12.dp) else Modifier),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.read?.data?.merchant?.let { BillAi.maskText(it, masked) } ?: r.tx.merchant, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1)
                    Text(listOfNotNull(r.tx.createdAt.take(10), categoryLabel(r.tx.category), r.bills.size.takeIf { it > 1 }?.let { tr(R.string.abl_pages, it) }).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    r.read?.data?.summary?.let { Text(BillAi.maskText(it, masked), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1) }
                    if (r.bills.isEmpty()) Text(tr(R.string.abl_no_bill), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                MaskableAmountText(amount = r.tx.amount.toDoubleOrNull() ?: 0.0, visibility = vis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (r.bills.isEmpty()) AppButton(tr(R.string.bills_attach), onClick = { attaching = r.tx }, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
                else {
                    AppButton(tr(R.string.abl_open), onClick = { viewing = r }, variant = ButtonVariant.Text, size = ButtonSize.Small)
                    if (r.read?.status == "done") AppButton(tr(R.string.bai_explain), onClick = { explaining = r.read }, variant = ButtonVariant.Text, size = ButtonSize.Small)
                }
            }
        }
        val withBills = rows.orEmpty().filter { it.bills.isNotEmpty() }
        if (withBills.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(tr(R.string.abl_email_me), variant = ButtonVariant.Tonal, size = ButtonSize.Small, onClick = {
                if (masked) { vis.requestUnlock(); return@AppButton }
                scope.launch {
                    com.example.spendsync.data.assistant.EmailCardMemory.reset("$key:email")
                    val email = sessionDataStore.userEmail.first().orEmpty()
                    emailing = EmailBillsProposal(withBills.map { it.tx.id }.take(10), null, null, email.replace(Regex("^(.).*(@.*)$"), "$1***$2"))
                }
            })
            AppButton(tr(R.string.abl_share), variant = ButtonVariant.Text, size = ButtonSize.Small, enabled = sharing != tr(R.string.abl_preparing), onClick = {
                if (masked) { vis.requestUnlock(); return@AppButton }
                sharing = tr(R.string.abl_preparing)
                scope.launch {
                    val uris = BillShare.prepare(context, financeRepository, withBills.map { it.tx.id })
                    sharing = if (uris.isEmpty()) tr(R.string.abl_share_failed) else null
                    BillShare.open(context, uris)
                }
            })
        }
        sharing?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
    }

    emailing?.let { EmailBillsCard(it, "$key:email", financeRepository, vis, onDismiss = { emailing = null }) }
    viewing?.let { r -> BillViewer(bills = r.bills, start = 0, masked = masked, onUnlock = { vis.requestUnlock() }, onDismiss = { viewing = null }, onReplace = null, onDelete = null) }
    explaining?.let { s -> BillExplainSheet(s, masked, onReadAgain = { explaining = null }, onDismiss = { explaining = null }) }
    attaching?.let { tx ->
        AppSheet(onDismiss = { attaching = null; scope.launch { load() } }, title = tr(R.string.bills_title)) {
            BillsSection(tx.id, financeRepository, vis, autoScan = true, onChanged = { scope.launch { load() } })
        }
    }
}

/** "Email n bills to j***@…?" → Send → Sent / error / cooldown. */
@Composable
internal fun EmailBillsCard(p: EmailBillsProposal, key: String, financeRepository: FinanceRepository, vis: AmountVisibilityState, onDismiss: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Kept outside the list so scrolling away never re-enables Send on a card that already sent.
    val memory = com.example.spendsync.data.assistant.EmailCardMemory
    val state = memory.state(key)
    if (memory.dismissed(key)) return
    Column(Modifier.fillMaxWidth().glassCard(16).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(R.string.abl_email_title, p.transactionIds.size, p.to), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        if (p.files != null && p.approxBytes != null) Text(tr(R.string.abl_email_size, p.files, android.text.format.Formatter.formatShortFileSize(context, p.approxBytes)), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        Text(tr(R.string.abl_email_note), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        when (val s = state) {
            is EmailCardState.Sent -> {
                Text(tr(R.string.abl_sent, s.to), style = MaterialTheme.typography.bodyMedium, color = scheme.primary)
                if (s.notAttached > 0) Text(tr(R.string.abl_sent_partial, s.notAttached), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (s.skipped > 0) Text(tr(R.string.abl_skipped, s.skipped), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            else -> {
                if (s is EmailCardState.Failed) Text(s.message, style = MaterialTheme.typography.bodySmall, color = scheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        tr(if (s is EmailCardState.Failed && !s.cooldown) R.string.abl_try_again else R.string.abl_send),
                        loading = s is EmailCardState.Sending, enabled = s !is EmailCardState.Sending, size = ButtonSize.Small,
                        onClick = {
                            if (vis.isMaskingEnabled && !vis.isVisible) { vis.requestUnlock(); return@AppButton }
                            memory.set(key, EmailCardState.Sending)
                            scope.launch { memory.set(key, EmailCard.from(financeRepository.emailBills(p.transactionIds))) }
                        },
                    )
                    AppButton(tr(R.string.abl_cancel), onClick = { memory.dismiss(key); onDismiss() }, variant = ButtonVariant.Text, size = ButtonSize.Small, enabled = s !is EmailCardState.Sending)
                }
            }
        }
    }
}
