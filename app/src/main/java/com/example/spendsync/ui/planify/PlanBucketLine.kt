package com.example.spendsync.ui.planify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.planify.PlanMath
import com.example.spendsync.data.remote.model.PlanViewDto
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.theme.SemanticWarning
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
import com.example.spendsync.utils.formatInr

/**
 * One line on the Add expense screen about the plan: how much is left in this category's bucket, and what this
 * amount would do to it. It shows up at the moment of the decision and never blocks anything.
 *
 * [alreadyCounted] is the amount of the transaction being edited, so editing a ₹400 spend to ₹450 is judged as +50.
 */
@Composable
fun PlanBucketLine(
    plan: PlanViewDto?,
    category: String?,
    amount: Double,
    alreadyCounted: Double,
    vis: AmountVisibilityState,
    modifier: Modifier = Modifier,
) {
    val bucket = category?.let { PlanMath.bucketFor(plan, it) } ?: return
    if (bucket.kind == "savings") return
    val base = bucket.copy(spent = (bucket.spent - alreadyCounted).coerceAtLeast(0.0))
    val after = remember(base, amount) { PlanMath.afterSpend(base, amount) }
    val scheme = MaterialTheme.colorScheme
    fun money(v: Double) = safeText(vis, formatInr(v))

    val (color, text) = when {
        amount <= 0.0 -> scheme.onSurfaceVariant to tr(R.string.pl_line_left, bucket.title(), money(base.remaining.coerceAtLeast(0.0)), money(base.limit))
        after.level == PlanMath.Level.Over -> expenseColor() to tr(R.string.pl_line_over, bucket.title(), money(base.remaining.coerceAtLeast(0.0)), money(after.overBy))
        after.level == PlanMath.Level.Reached -> SemanticWarning to tr(R.string.pl_line_reached, bucket.title())
        after.level == PlanMath.Level.Close -> SemanticWarning to tr(R.string.pl_line_after, bucket.title(), money(after.remaining))
        else -> incomeColor() to tr(R.string.pl_line_after, bucket.title(), money(after.remaining))
    }
    Column(
        modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(start = 8.dp))
        }
        if (amount > 0.0 && after.level == PlanMath.Level.Over) {
            Text(tr(R.string.pl_line_soft), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
        }
    }
}
