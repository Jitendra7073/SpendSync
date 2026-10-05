package com.example.spendsync.ui.planify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.remote.model.BucketDto
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppChip
import com.example.spendsync.ui.components.AppDialog
import com.example.spendsync.ui.components.AppSheet
import com.example.spendsync.ui.components.AppTextField
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.DialogAction
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.categoryLabel
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.utils.formatInr
import com.example.spendsync.utils.maskAmountsInText

/** Text with rupee amounts in it, hidden the same way as on every other screen while amount hiding is on. */
internal fun safeText(vis: AmountVisibilityState, text: String): String =
    maskAmountsInText(text, vis.isMaskingEnabled, vis.isVisible).text

internal fun amountOrNull(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it >= 0 }

/** Shift part of one bucket's limit to another. The plan total does not change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoveMoneySheet(
    buckets: List<BucketDto>,
    from: String?,
    to: String?,
    suggestedAmount: Double?,
    vis: AmountVisibilityState,
    onMove: (from: String, to: String, amount: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var fromCat by remember { mutableStateOf(from ?: buckets.maxByOrNull { it.remaining }?.category) }
    var toCat by remember { mutableStateOf(to ?: buckets.firstOrNull { it.category != fromCat && it.state == "over" }?.category) }
    var amount by remember { mutableStateOf(suggestedAmount?.takeIf { it > 0 }?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    val source = buckets.firstOrNull { it.category == fromCat }
    val value = amountOrNull(amount) ?: 0.0
    val tooMuch = source != null && value > source.limit
    val valid = fromCat != null && toCat != null && fromCat != toCat && value > 0 && !tooMuch

    AppSheet(onDismiss = onDismiss, title = tr(R.string.pl_move_title), subtitle = tr(R.string.pl_move_sub)) {
        Text(tr(R.string.pl_move_from), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            buckets.forEach { b -> AppChip("${b.title()} · ${safeText(vis, formatInr(b.limit))}", selected = fromCat == b.category, onClick = { fromCat = b.category }) }
        }
        Spacer(Modifier.height(14.dp))
        Text(tr(R.string.pl_move_to), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            buckets.filter { it.category != fromCat }.forEach { b -> AppChip(b.title(), selected = toCat == b.category, onClick = { toCat = b.category }) }
        }
        Spacer(Modifier.height(14.dp))
        AppTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
            label = tr(R.string.pl_amount),
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            isError = tooMuch,
            supportingText = if (tooMuch) tr(R.string.pl_move_too_much) else null,
        )
        Spacer(Modifier.height(16.dp))
        AppButton(tr(R.string.pl_move_button), onClick = { onMove(fromCat!!, toCat!!, value) }, enabled = valid, size = ButtonSize.Large, fullWidth = true)
    }
}

/**
 * What to do when a bucket goes over. Limits are soft, so there is no blocking: move money in, raise the limit,
 * or carry on knowingly. The best way out is already highlighted.
 */
@Composable
internal fun OverLimitSheet(
    bucket: BucketDto,
    donors: List<BucketDto>,
    vis: AmountVisibilityState,
    onMove: (from: BucketDto) -> Unit,
    onRaise: () -> Unit,
    onDismiss: () -> Unit,
) {
    val overBy = -bucket.remaining
    val best = donors.filter { it.category != bucket.category && it.remaining >= overBy }.maxByOrNull { it.remaining }
    AppSheet(
        onDismiss = onDismiss,
        title = tr(R.string.pl_over_title, bucket.title(), safeText(vis, formatInr(overBy))),
        subtitle = tr(R.string.pl_over_sub, safeText(vis, formatInr(bucket.limit))),
    ) {
        if (best != null) {
            AppButton(
                tr(R.string.pl_over_move, safeText(vis, formatInr(overBy)), best.title()),
                onClick = { onMove(best) }, size = ButtonSize.Large, fullWidth = true,
            )
            Spacer(Modifier.height(10.dp))
        }
        AppButton(
            tr(R.string.pl_over_raise, safeText(vis, formatInr(bucket.spent))),
            onClick = onRaise, variant = if (best == null) ButtonVariant.Primary else ButtonVariant.Tonal, size = ButtonSize.Large, fullWidth = true,
        )
        Spacer(Modifier.height(10.dp))
        AppButton(tr(R.string.pl_over_carry_on), onClick = onDismiss, variant = ButtonVariant.Outline, size = ButtonSize.Large, fullWidth = true)
        Spacer(Modifier.height(8.dp))
        Text(tr(R.string.pl_over_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun EditLimitDialog(bucket: BucketDto, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (bucket.limit % 1.0 == 0.0) bucket.limit.toLong().toString() else bucket.limit.toString()) }
    val value = amountOrNull(text)
    AppDialog(
        onDismiss = onDismiss,
        title = tr(R.string.pl_edit_limit_title, bucket.title()),
        icon = Icons.Default.PieChart,
        primary = DialogAction(tr(R.string.save), { onSave(value ?: 0.0) }, enabled = value != null),
        secondary = DialogAction(tr(R.string.cancel), onDismiss),
    ) {
        AppTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
            label = tr(R.string.monthly_limit),
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        )
    }
}

/** Add one bucket: pick the category it tracks, what kind it is, and its limit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddBucketDialog(
    known: List<String>,
    taken: Set<String>,
    presetCategory: String? = null,
    onAdd: (category: String, kind: String, limit: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = known.filter { it !in taken }
    var category by remember { mutableStateOf(presetCategory?.takeIf { it !in taken } ?: options.firstOrNull()) }
    var custom by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("spend") }
    var limit by remember { mutableStateOf("") }
    val chosen = custom.trim().ifEmpty { category.orEmpty() }
    val value = amountOrNull(limit)
    val clash = chosen.isNotEmpty() && chosen in taken
    AppDialog(
        onDismiss = onDismiss,
        title = tr(R.string.pl_add_bucket_title),
        message = tr(R.string.pl_add_bucket_sub),
        icon = Icons.Default.PieChart,
        primary = DialogAction(tr(R.string.pl_add_bucket_button), { onAdd(chosen, kind, value ?: 0.0) }, enabled = chosen.isNotEmpty() && !clash && value != null && value > 0),
        secondary = DialogAction(tr(R.string.cancel), onDismiss),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { c -> AppChip(categoryLabel(c), selected = custom.isBlank() && category == c, onClick = { category = c; custom = "" }) }
        }
        Spacer(Modifier.height(12.dp))
        AppTextField(value = custom, onValueChange = { custom = it.take(40) }, label = tr(R.string.pl_add_bucket_custom), isError = clash, supportingText = if (clash) tr(R.string.pl_add_bucket_clash) else null)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("spend", "fixed", "savings").forEach { k -> AppChip(groupTitle(k), selected = kind == k, onClick = { kind = k }) }
        }
        Spacer(Modifier.height(12.dp))
        AppTextField(
            value = limit,
            onValueChange = { limit = it.filter { c -> c.isDigit() || c == '.' } },
            label = tr(R.string.monthly_limit),
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        )
    }
}
