package com.example.spendsync.ui.transaction

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.ui.components.AppSheet
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor

/** Half-screen "Income or Expense?" chooser shown before the add-transaction form. */
@Composable
fun AddTransactionTypeSheet(
    onDismiss: () -> Unit,
    onSelect: (TransactionType) -> Unit,
) {
    AppSheet(
        onDismiss = onDismiss,
        title = tr(R.string.what_are_you_adding),
        subtitle = tr(R.string.pick_one_to_continue),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TypeChoiceCard(
                label = tr(R.string.money_in),
                hint = tr(R.string.salary_gifts_refunds),
                icon = Icons.Default.ArrowUpward,
                accent = incomeColor(),
                modifier = Modifier.weight(1f),
                onClick = { onSelect(TransactionType.INCOME) },
            )
            TypeChoiceCard(
                label = tr(R.string.money_out),
                hint = tr(R.string.shopping_bills_food),
                icon = Icons.Default.ArrowDownward,
                accent = expenseColor(),
                modifier = Modifier.weight(1f),
                onClick = { onSelect(TransactionType.EXPENSE) },
            )
        }
    }
}

@Composable
private fun TypeChoiceCard(
    label: String,
    hint: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 12.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accent, textAlign = TextAlign.Center)
        Text(hint, fontSize = 12.sp, color = accent.copy(alpha = 0.8f), textAlign = TextAlign.Center)
    }
}
