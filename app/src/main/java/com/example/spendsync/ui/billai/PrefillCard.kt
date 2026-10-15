package com.example.spendsync.ui.billai

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spendsync.R
import com.example.spendsync.data.billai.BillAi
import com.example.spendsync.data.remote.model.BillReadDto
import com.example.spendsync.ui.components.*
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.i18n.categoryLabel
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.utils.formatInr

sealed interface PrefillState {
    data object Hidden : PrefillState
    data object Reading : PrefillState
    data class Ready(val prefill: BillAi.Prefill, val read: BillReadDto, val model: String) : PrefillState
}

/** "Bill says ₹2,150 · Zara · 9 Oct — Use these?" Nothing changes until the user taps. */
@Composable
fun PrefillCard(state: PrefillState, masked: Boolean, onUse: (BillAi.Prefill) -> Unit, onIgnore: () -> Unit) {
    if (state is PrefillState.Hidden) return
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().glassCard(16).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AutoAwesome, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            Text(tr(if (state is PrefillState.Reading) R.string.bai_reading else R.string.bai_bill_says), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        }
        when (state) {
            PrefillState.Reading -> Skeleton(loading = true) { Text("Placeholder bill summary text", style = MaterialTheme.typography.bodyMedium) }
            is PrefillState.Ready -> {
                val p = state.prefill
                val parts = listOfNotNull(
                    p.amount?.let { if (masked) "★★★★★" else formatInr(it) }, p.note, p.date?.toString(), p.category?.let { categoryLabel(it) },
                )
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(tr(R.string.bai_use_these), onClick = { onUse(p) }, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
                    AppButton(tr(R.string.bai_ignore), onClick = onIgnore, variant = ButtonVariant.Text, size = ButtonSize.Small)
                }
            }
            else -> Unit
        }
    }
}
