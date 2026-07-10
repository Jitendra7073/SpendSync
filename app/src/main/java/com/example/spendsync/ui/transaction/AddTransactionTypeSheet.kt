package com.example.spendsync.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.ui.theme.SemanticError
import com.example.spendsync.ui.theme.SemanticSuccess

/**
 * The half-screen sheet shown when the FAB is tapped — asks whether the user
 * is adding income or an expense before opening the full form, instead of
 * defaulting straight into one type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionTypeSheet(
    onDismiss: () -> Unit,
    onSelect: (TransactionType) -> Unit,
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NeutralWhite,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = "What are you adding?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = NeutralBlack,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Choose a type to continue",
                fontSize = 13.sp,
                color = NeutralMid,
            )
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TypeChoiceCard(
                    label = "Add Income",
                    icon = Icons.Default.ArrowUpward,
                    accentColor = SemanticSuccess,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(TransactionType.INCOME) },
                )
                TypeChoiceCard(
                    label = "Add Expense",
                    icon = Icons.Default.ArrowDownward,
                    accentColor = SemanticError,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(TransactionType.EXPENSE) },
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun TypeChoiceCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accentColor.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = 24.dp, horizontal = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = accentColor)
    }
}
