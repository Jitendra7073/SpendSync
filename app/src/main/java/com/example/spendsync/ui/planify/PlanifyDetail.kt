package com.example.spendsync.ui.planify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.remote.model.BucketDto
import com.example.spendsync.data.remote.model.PlanViewDto
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppConfirmDialog
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.home.AmountRow
import com.example.spendsync.ui.home.animatedFraction
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.settings.SettingsBackdrop
import com.example.spendsync.ui.settings.SettingsContentWidth
import com.example.spendsync.ui.settings.SettingsDivider
import com.example.spendsync.ui.settings.SettingsGroup
import com.example.spendsync.ui.settings.SettingsInfoRow
import com.example.spendsync.ui.settings.SettingsTopBar
import com.example.spendsync.ui.settings.cascadeIn
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.MaskableAmountText
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
import com.example.spendsync.utils.formatInr
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.YearMonth

/** One bucket in detail: how full it is, the pace chart, the numbers, and what you can do about it. */
@Composable
internal fun BucketDetailPage(
    bucket: BucketDto,
    plan: PlanViewDto,
    month: LocalDate,
    monthTxs: List<TransactionDto>,
    vis: AmountVisibilityState,
    canMove: Boolean,
    onBack: () -> Unit,
    onEditLimit: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit,
    onViewTransaction: (TransactionDto) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val color = stateColor(bucket.state)
    val progress = animatedFraction((bucket.percent / 100.0).toFloat().coerceIn(0f, 1f))
    var confirmRemove by remember { mutableStateOf(false) }
    val txs = remember(monthTxs, bucket.category) { monthTxs.filter { it.category == bucket.category }.take(12) }
    val isCurrent = YearMonth.from(month) == YearMonth.now()
    val status = plan.status

    SettingsBackdrop {
        Column(Modifier.fillMaxSize()) {
            SettingsTopBar(bucket.title(), onBack)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SettingsContentWidth {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.cascadeIn(0).padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(20.dp)) {
                            Text(verdictLabel(bucket), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
                            Text(tr(R.string.pl_detail_used, bucket.percent.coerceAtMost(999.0)), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                            Spacer(Modifier.height(14.dp))
                            Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(scheme.outlineVariant.copy(alpha = 0.5f))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(color))
                            }
                        }
                        if (bucket.kind == "spend" && bucket.limit > 0.0) {
                            PaceCard(
                                debits = monthTxs.filter { it.type == "debit" && it.category == bucket.category },
                                month = month, budget = bucket.limit, vis = vis, modifier = Modifier.cascadeIn(1),
                            )
                        }
                        SettingsGroup(Modifier.cascadeIn(1)) {
                            AmountRow(tr(R.string.spent), bucket.spent, vis, color = expenseColor())
                            SettingsDivider()
                            AmountRow(tr(R.string.limit), bucket.limit, vis)
                            SettingsDivider()
                            AmountRow(
                                if (bucket.remaining >= 0) tr(R.string.remaining) else tr(R.string.over_by),
                                kotlin.math.abs(bucket.remaining), vis,
                                color = if (bucket.remaining >= 0) incomeColor() else expenseColor(),
                            )
                        }
                        SettingsGroup(Modifier.cascadeIn(2)) {
                            SettingsInfoRow(tr(R.string.pl_detail_kind), groupTitle(bucket.kind))
                            if (bucket.kind == "spend" && isCurrent && status.daysLeft > 0) {
                                SettingsDivider()
                                AmountRow(
                                    tr(R.string.safe_to_spend_per_day),
                                    if (bucket.remaining > 0) bucket.remaining / status.daysLeft else 0.0, vis,
                                    hint = tr(R.string.for_the_remaining_1_day_s, status.daysLeft),
                                )
                                if (bucket.projected != null && bucket.projected > 0) {
                                    SettingsDivider()
                                    AmountRow(tr(R.string.pl_detail_projected), bucket.projected, vis, color = if (bucket.projected > bucket.limit) expenseColor() else scheme.onSurface)
                                }
                            }
                        }
                        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppButton(tr(R.string.pl_edit_limit), onClick = onEditLimit, variant = ButtonVariant.Tonal, modifier = Modifier.weight(1f))
                            if (canMove) AppButton(tr(R.string.pl_move_title), onClick = onMove, variant = ButtonVariant.Outline, modifier = Modifier.weight(1f))
                        }
                        if (txs.isNotEmpty()) {
                            Text(tr(R.string.transactions), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground, modifier = Modifier.padding(horizontal = 24.dp))
                            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard()) {
                                txs.forEachIndexed { i, t ->
                                    if (i > 0) SettingsDivider()
                                    TxRow(t, vis) { onViewTransaction(t) }
                                }
                            }
                        }
                        AppButton(
                            tr(R.string.pl_remove_bucket), onClick = { confirmRemove = true }, variant = ButtonVariant.Text, size = ButtonSize.Small,
                            leadingIcon = Icons.Default.DeleteOutline, modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(110.dp))
                    }
                }
            }
        }
    }
    if (confirmRemove) {
        AppConfirmDialog(
            title = tr(R.string.pl_remove_title, bucket.title()),
            message = tr(R.string.pl_remove_body),
            confirmLabel = tr(R.string.pl_remove),
            cancelLabel = tr(R.string.cancel),
            destructive = true,
            onConfirm = { confirmRemove = false; onRemove() },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun TxRow(t: TransactionDto, vis: AmountVisibilityState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val date = remember(t.createdAt) {
        try { ZonedDateTime.parse(t.createdAt).format(DateTimeFormatter.ofPattern("d MMM")) } catch (e: Exception) { "" }
    }
    val amount = t.amount.toDoubleOrNull() ?: 0.0
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.merchant, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface, maxLines = 1)
            Text(date, fontSize = 11.sp, color = scheme.onSurfaceVariant)
        }
        MaskableAmountText(
            amount, vis, prefix = if (t.type == "credit") "+" else "-",
            color = if (t.type == "credit") incomeColor() else expenseColor(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}
