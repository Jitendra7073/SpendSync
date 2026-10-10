package com.example.spendsync.ui.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.spendsync.R
import com.example.spendsync.data.bills.BillEvents
import com.example.spendsync.data.bills.BillImages
import com.example.spendsync.data.bills.BillQueue
import com.example.spendsync.data.bills.BillUploadWorker
import com.example.spendsync.data.bills.QueuedBill
import com.example.spendsync.data.remote.model.BillDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.components.Skeleton
import com.example.spendsync.ui.components.SkeletonBlock
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.shared.AmountVisibilityState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Bills" section for a saved transaction: strip, add (scan/photos/PDF), viewer with replace/delete. */
@Composable
fun BillsSection(txId: String, financeRepository: FinanceRepository, amountVisibility: AmountVisibilityState, autoScan: Boolean = false, onChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by remember(txId) { mutableStateOf<List<BillDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(autoScan) }
    var replacing by remember { mutableStateOf<BillDto?>(null) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    val queued by remember(txId) { BillQueue.forTx(txId) }.collectAsState(initial = emptyList())
    val progress by BillQueue.progress.collectAsState()
    val masked = amountVisibility.isMaskingEnabled && !amountVisibility.isVisible

    suspend fun reload() { when (val r = financeRepository.listBills(txId)) { is AuthResult.Success -> server = r.data; is AuthResult.Error -> { error = r.message; if (server == null) server = emptyList() } } }
    LaunchedEffect(txId) { BillQueue.init(context); reload() }
    LaunchedEffect(txId) { BillEvents.changed.filter { it == txId }.collect { reload(); onChanged() } }

    val tiles = remember(server, queued, progress) { mergeTiles(server.orEmpty(), queued, progress) }
    val remaining = 5 - tiles.size

    fun enqueue(picked: List<Picked>, replaces: BillDto?) = scope.launch {
        val prepared = withContext(Dispatchers.IO) { picked.map { BillImages.prepare(context, it.uri, it.mime) } }
        prepared.firstOrNull { it.isFailure }?.exceptionOrNull()?.let {
            error = tr(if (it is BillImages.TooBigException) R.string.bills_too_big else R.string.bills_unreadable)
        }
        val ok = prepared.mapNotNull { it.getOrNull() }
        if (ok.isNotEmpty()) BillQueue.enqueuePrepared(context, txId, if (replaces != null) ok.take(1) else ok, replaces = replaces?.id, position = replaces?.position)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(R.string.bills_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Skeleton(loading = server == null) {
            if (server == null) SkeletonBlock(height = 72.dp)
            else BillStrip(
                tiles = tiles, canAdd = remaining > 0, masked = masked,
                onAdd = { picking = true },
                onOpen = { viewing = it },
                onRetry = { item -> scope.launch { BillQueue.update(context, item.copy(state = QueuedBill.State.Waiting, error = null)); BillUploadWorker.schedule(context) } },
                onRemove = { item -> scope.launch { BillQueue.remove(context, item.clientKey) } },
            )
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    }

    if (picking || replacing != null) {
        BillSourceSheet(
            remaining = if (replacing != null) 1 else remaining,
            autoScan = autoScan && replacing == null,
            onPicked = { picked -> val r = replacing; picking = false; replacing = null; enqueue(picked, r) },
            onDismiss = { picking = false; replacing = null },
        )
    }
    viewing?.let { start ->
        BillViewer(
            bills = server.orEmpty(), start = start, masked = masked,
            onUnlock = { amountVisibility.requestUnlock() },
            onDismiss = { viewing = null },
            onReplace = { b -> viewing = null; replacing = b },
            onDelete = { b ->
                viewing = null
                scope.launch {
                    if (financeRepository.deleteBill(b.id) is AuthResult.Success) { server = server.orEmpty().filter { it.id != b.id }; onChanged() }
                }
            },
        )
    }
}
