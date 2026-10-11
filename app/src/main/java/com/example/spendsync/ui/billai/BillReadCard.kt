package com.example.spendsync.ui.billai

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.spendsync.R
import com.example.spendsync.data.billai.BillAi
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.model.BillReadStatusDto
import com.example.spendsync.data.remote.model.RunBillReadRequest
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.components.*
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.utils.formatInr
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Under the bill strip: reading / one-line summary + Explain / failed + Try again / no consent; plus the mismatch row. */
@Composable
fun BillReadCard(
    txId: String,
    recordedAmount: Double?,
    financeRepository: FinanceRepository,
    sessionDataStore: SessionDataStore,
    amountVisibility: AmountVisibilityState,
    pagesVersion: Int,
    readAgainRequest: Int,
    onState: (BillReadStatusDto?) -> Unit,
    onExplain: (BillReadStatusDto) -> Unit,
    onUpdateAmount: (Double) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var state by remember(txId) { mutableStateOf<BillReadStatusDto?>(null) }
    val masked = amountVisibility.isMaskingEnabled && !amountVisibility.isVisible

    suspend fun run(force: Boolean) {
        val consent = sessionDataStore.assistantConsent.first()
        val p = sessionDataStore.assistantPrefs.first()
        val prefs = BillAi.prefs(com.example.spendsync.ui.i18n.LanguageManager.current.storedName, p.style, p.tone)
        state = state?.copy(status = "queued")
        (financeRepository.runBillRead(txId, RunBillReadRequest(consent, force, prefs)) as? AuthResult.Success)?.data?.let { state = it }
    }

    LaunchedEffect(state) { onState(state) }
    LaunchedEffect(readAgainRequest) { if (readAgainRequest > 0) run(force = true) }

    LaunchedEffect(txId, pagesVersion) {
        val got = (financeRepository.billRead(txId) as? AuthResult.Success)?.data
        state = got
        // Webhook-confirmed bills and changed pages: read now if the user allows AI.
        if (got != null && (got.status == "none" || got.stale) && sessionDataStore.assistantConsent.first()) run(force = false)
    }

    val s = state ?: return
    if (s.status == "none") return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (s.status) {
            "queued" -> Skeleton(loading = true) { Text(tr(R.string.bai_reading), style = MaterialTheme.typography.bodySmall) }
            "no_consent" -> Text(tr(R.string.bai_no_consent), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            "failed" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(R.string.bai_failed), style = MaterialTheme.typography.bodySmall, color = scheme.error, modifier = Modifier.weight(1f))
                AppButton(tr(R.string.bai_try_again), onClick = { scope.launch { run(force = true) } }, variant = ButtonVariant.Text, size = ButtonSize.Small)
            }
            "done" -> s.data?.let { read ->
                Text(read.summary, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 2)
                AppButton(tr(R.string.bai_explain), onClick = { onExplain(s) }, variant = ButtonVariant.Tonal, size = ButtonSize.Small, leadingIcon = Icons.Default.AutoAwesome)
                if (recordedAmount != null && BillAi.differs(recordedAmount, read.total)) {
                    Text(
                        if (masked) tr(R.string.bai_mismatch_masked) else tr(R.string.bai_mismatch, formatInr(read.total!!), formatInr(recordedAmount)),
                        style = MaterialTheme.typography.bodySmall, color = scheme.error,
                    )
                    AppButton(tr(R.string.bai_update_amount), onClick = { onUpdateAmount(read.total!!) }, variant = ButtonVariant.Outline, size = ButtonSize.Small)
                }
            }
        }
    }
}
