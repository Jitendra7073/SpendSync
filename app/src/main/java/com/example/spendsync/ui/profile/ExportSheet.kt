package com.example.spendsync.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.remote.model.ExportEmailRequest
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppChip
import com.example.spendsync.ui.components.AppSheet
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.utils.DataExporter
import com.example.spendsync.utils.ExportData
import com.example.spendsync.utils.ExportDelivery
import com.example.spendsync.utils.ExportFormat
import com.example.spendsync.utils.ExportKind
import com.example.spendsync.utils.ExportPlanLine
import com.example.spendsync.utils.ExportTypeFilter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private enum class Period { ThisMonth, LastMonth, Last3, ThisYear, All, Month }

private fun range(p: Period, month: YearMonth, today: LocalDate): Pair<LocalDate?, LocalDate?> = when (p) {
    Period.ThisMonth -> YearMonth.from(today).let { it.atDay(1) to it.atEndOfMonth() }
    Period.LastMonth -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
    Period.Last3 -> YearMonth.from(today).minusMonths(2).atDay(1) to YearMonth.from(today).atEndOfMonth()
    Period.ThisYear -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
    Period.All -> null to null
    Period.Month -> month.atDay(1) to month.atEndOfMonth()
}

/**
 * Export: choose the period, which data (transactions, holds, plan), income/expense, the format, and how it reaches
 * you (share sheet, saved in Downloads, or emailed to your account address). Everything is built from your own data.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportSheet(financeRepository: FinanceRepository, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    var period by remember { mutableStateOf(Period.ThisMonth) }
    var month by remember { mutableStateOf(YearMonth.from(today).minusMonths(1)) }
    var menu by remember { mutableStateOf(false) }
    var kinds by remember { mutableStateOf(setOf(ExportKind.Transactions)) }
    var type by remember { mutableStateOf(ExportTypeFilter.All) }
    var format by remember { mutableStateOf(ExportFormat.Csv) }
    var delivery by remember { mutableStateOf(ExportDelivery.Share) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // text, isError

    fun label(r: Int) = tr(r)

    suspend fun run() {
        working = true; message = null
        val (from, to) = range(period, month, today)
        val usedFormat = if (delivery == ExportDelivery.Email && format == ExportFormat.Pdf) ExportFormat.Csv else format
        if (delivery == ExportDelivery.Email) {
            val r = financeRepository.emailExport(
                ExportEmailRequest(from?.toString(), to?.toString(), kinds.map { it.name.lowercase() }, type.name.lowercase(), usedFormat.ext),
            )
            message = when (r) {
                is AuthResult.Success -> tr(R.string.ex_emailed, r.data.sentTo) to false
                is AuthResult.Error -> r.message to true
            }
            working = false
            return
        }
        // Gather the data on the phone, then build the files.
        var txs = emptyList<com.example.spendsync.data.remote.model.TransactionDto>()
        if (ExportKind.Transactions in kinds) {
            when (val r = financeRepository.getTransactions(startDate = from?.let { "${it}T00:00:00.000Z" }, endDate = to?.let { "${it}T23:59:59.999Z" }, limit = 2000, forceRefresh = true)) {
                is AuthResult.Success -> txs = r.data.filter { type == ExportTypeFilter.All || (type == ExportTypeFilter.Income) == (it.type == "credit") }
                is AuthResult.Error -> { message = r.message to true; working = false; return }
            }
        }
        var holds = emptyList<com.example.spendsync.data.remote.model.HoldDto>()
        if (ExportKind.Holds in kinds) (financeRepository.getHolds() as? AuthResult.Success)?.let { holds = it.data }
        val plan = mutableListOf<ExportPlanLine>()
        if (ExportKind.Plan in kinds) {
            val first = YearMonth.from(from ?: today.minusMonths(11))
            val last = YearMonth.from(to ?: today)
            var m = first
            var n = 0
            while (!m.isAfter(last) && n < 12) {
                (financeRepository.getPlan(m.toString(), today.toString(), forceRefresh = true) as? AuthResult.Success)?.data?.takeIf { it.exists }?.status?.buckets?.forEach { b ->
                    plan += ExportPlanLine(m.toString(), b.name.ifBlank { b.category }, b.kind, b.limit, b.spent)
                }
                m = m.plusMonths(1); n++
            }
        }
        if (txs.isEmpty() && holds.isEmpty() && plan.isEmpty()) { message = tr(R.string.ex_empty) to true; working = false; return }
        val files = DataExporter.build(ExportData(txs, holds, plan, kinds), usedFormat)
        when (delivery) {
            ExportDelivery.Download -> {
                if (DataExporter.saveToDownloads(context, files)) message = tr(R.string.ex_saved) to false
                else context.startActivity(DataExporter.shareIntent(context, files, tr(R.string.ex_title))) // older phones: share instead
            }
            else -> context.startActivity(DataExporter.shareIntent(context, files, tr(R.string.ex_title)))
        }
        working = false
    }

    AppSheet(onDismiss = onDismiss, title = tr(R.string.ex_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section(tr(R.string.ex_period))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Period.ThisMonth to R.string.ex_this_month, Period.LastMonth to R.string.ex_last_month, Period.Last3 to R.string.ex_last_3, Period.ThisYear to R.string.ex_this_year, Period.All to R.string.ex_all).forEach { (p, r) ->
                    AppChip(label(r), selected = period == p, onClick = { period = p })
                }
                Box {
                    AppChip(if (period == Period.Month) month.format(DateTimeFormatter.ofPattern("MMM yyyy")) else tr(R.string.ex_pick_month), selected = period == Period.Month, onClick = { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        (0..23).map { YearMonth.from(today).minusMonths(it.toLong()) }.forEach { ym ->
                            DropdownMenuItem(text = { Text(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy"))) }, onClick = { month = ym; period = Period.Month; menu = false })
                        }
                    }
                }
            }
            Section(tr(R.string.ex_data))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ExportKind.Transactions to R.string.ex_k_tx, ExportKind.Holds to R.string.ex_k_holds, ExportKind.Plan to R.string.ex_k_plan).forEach { (k, r) ->
                    AppChip(label(r), selected = k in kinds, onClick = { kinds = if (k in kinds && kinds.size > 1) kinds - k else kinds + k })
                }
            }
            if (ExportKind.Transactions in kinds) {
                Section(tr(R.string.ex_type))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ExportTypeFilter.All to R.string.ex_t_all, ExportTypeFilter.Income to R.string.ex_t_income, ExportTypeFilter.Expense to R.string.ex_t_expense).forEach { (t, r) ->
                        AppChip(label(r), selected = type == t, onClick = { type = t })
                    }
                }
            }
            Section(tr(R.string.ex_format))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { f -> AppChip(f.name.uppercase(), selected = format == f, onClick = { format = f }) }
            }
            Section(tr(R.string.ex_deliver))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ExportDelivery.Share to R.string.ex_d_share, ExportDelivery.Download to R.string.ex_d_download, ExportDelivery.Email to R.string.ex_d_email).forEach { (d, r) ->
                    AppChip(label(r), selected = delivery == d, onClick = { delivery = d })
                }
            }
            if (delivery == ExportDelivery.Email && format == ExportFormat.Pdf) Text(tr(R.string.ex_pdf_note), fontSize = 11.sp, color = scheme.onSurfaceVariant)
            message?.let { (text, error) -> Text(text, fontSize = 13.sp, color = if (error) scheme.error else scheme.primary) }
            Spacer(Modifier.height(4.dp))
            AppButton(if (working) tr(R.string.ex_working) else tr(R.string.ex_go), onClick = { scope.launch { run() } }, size = ButtonSize.Large, fullWidth = true, enabled = !working, loading = working)
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}
