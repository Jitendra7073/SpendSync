package com.example.spendsync.ui.home

import com.example.spendsync.ui.i18n.categoryLabel
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.ui.components.AppDialog
import com.example.spendsync.ui.components.DialogAction
import com.example.spendsync.ui.components.DialogTone
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.MaskableAmountText
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DeleteTransactionDialog(
    transaction: TransactionDto,
    amountVisibility: AmountVisibilityState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    loading: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val amount = transaction.amount.toDoubleOrNull() ?: 0.0
    AppDialog(
        onDismiss = onDismiss,
        title = tr(R.string.delete_this_transaction),
        message = tr(R.string.it_will_be_removed_from_your),
        icon = Icons.Default.DeleteForever,
        tone = DialogTone.Danger,
        primary = DialogAction(tr(R.string.delete), onConfirm, loading = loading),
        secondary = DialogAction(tr(R.string.keep_it), onDismiss),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                transaction.merchant.ifBlank { categoryLabel(transaction.category) },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            Spacer(Modifier.width(12.dp))
            MaskableAmountText(amount, amountVisibility, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        }
    }
}

@Composable
internal fun TransactionInfoDialog(
    transaction: TransactionDto,
    amountVisibility: AmountVisibilityState,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val amount = transaction.amount.toDoubleOrNull() ?: 0.0
    val isCredit = transaction.type == "credit"
    val locale = Locale.getDefault()
    val created = remember(transaction.createdAt, locale) {
        try {
            java.time.ZonedDateTime.parse(transaction.createdAt)
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", locale))
        } catch (e: Exception) {
            transaction.createdAt
        }
    }
    val tint = if (isCredit) incomeColor() else expenseColor()

    AppDialog(
        onDismiss = onDismiss,
        title = if (isCredit) tr(R.string.money_in) else tr(R.string.money_out),
        icon = Icons.Default.ReceiptLong,
        tone = if (isCredit) DialogTone.Success else DialogTone.Danger,
        primary = DialogAction(tr(R.string.close), onDismiss),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MaskableAmountText(
                amount = amount,
                visibility = amountVisibility,
                prefix = if (isCredit) "+ " else "- ",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            Spacer(Modifier.heightIn(min = 12.dp))
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
            InfoRow(tr(R.string.category), categoryLabel(transaction.category))
            InfoRow(tr(R.string.paid_to_from), transaction.merchant.ifBlank { "—" })
            if (!transaction.note.isNullOrBlank()) InfoRow(tr(R.string.note), transaction.note)
            if (!transaction.sourceApp.isNullOrBlank()) InfoRow(tr(R.string.added_from), transaction.sourceApp)
            InfoRow(tr(R.string.date), created)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = scheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
