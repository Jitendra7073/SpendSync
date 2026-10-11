package com.example.spendsync.ui.billai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spendsync.R
import com.example.spendsync.data.billai.BillAi
import com.example.spendsync.data.remote.model.BillReadStatusDto
import com.example.spendsync.ui.components.*
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.utils.formatInr

/** Summary, item table, totals, notes, and who read it. Every number from parsed fields; masked → stars. */
@Composable
fun BillExplainSheet(state: BillReadStatusDto, masked: Boolean, onReadAgain: () -> Unit, onDismiss: () -> Unit) {
    val read = state.data ?: return
    val scheme = MaterialTheme.colorScheme
    val money = { v: Double? -> when { v == null -> "—"; masked -> "★★★★★"; else -> formatInr(v) } }
    AppSheet(onDismiss = onDismiss, title = tr(R.string.bai_explain_title), subtitle = read.merchant) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (read.confidence < BillAi.MIN_CONFIDENCE) Text(tr(R.string.bai_low_confidence), style = MaterialTheme.typography.bodySmall, color = scheme.error)
            if (read.checks?.itemsMatchSubtotal == false || read.checks?.totalsAddUp == false) Text(tr(R.string.bai_bad_maths), style = MaterialTheme.typography.bodySmall, color = scheme.error)
            Text(read.summary, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            if (read.items.isNotEmpty()) {
                Text(tr(R.string.bai_items), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                read.items.forEach { i ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(listOfNotNull(i.name, i.qty?.let { q -> "× ${if (q % 1.0 == 0.0) q.toInt() else q}" }).joinToString(" "), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface, modifier = Modifier.weight(1f))
                        Text(money(i.amount), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface, textAlign = TextAlign.End)
                    }
                }
                HorizontalDivider(color = scheme.outlineVariant)
            }
            listOf(R.string.bai_subtotal to read.subtotal, R.string.bai_tax to read.tax, R.string.bai_discount to read.discount, R.string.bai_fees to read.fees)
                .filter { it.second != null }
                .forEach { (label, v) -> Row(Modifier.fillMaxWidth()) {
                    Text(tr(label), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(money(v), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                } }
            Row(Modifier.fillMaxWidth()) {
                Text(tr(R.string.bai_total), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface, modifier = Modifier.weight(1f))
                Text(money(read.total), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            }
            read.notes.forEach { n -> AppChip(BillAi.fillNote(n, read, masked), selected = false, onClick = {}) }
            state.model?.let { Text(tr(R.string.bai_read_by, it), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
            AppButton(tr(R.string.bai_read_again), onClick = onReadAgain, variant = ButtonVariant.Text, size = ButtonSize.Small)
        }
    }
}
