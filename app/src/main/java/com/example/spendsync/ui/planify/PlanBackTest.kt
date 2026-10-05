package com.example.spendsync.ui.planify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.planify.BackTest
import com.example.spendsync.data.planify.PlanGuide
import com.example.spendsync.data.remote.model.SuggestionDto
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.i18n.categoryLabel
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.theme.SemanticWarning
import com.example.spendsync.ui.theme.incomeColor
import com.example.spendsync.utils.formatInr
import java.time.YearMonth

/** Text lookup so the same card works in the guide (its own language menu) and in the builder (the app language). */
internal typealias Say = (Int, List<Any>) -> String

/**
 * Tests the draft plan against the user's own past months and shows the honest result, with one-tap fixes for the
 * buckets that would have broken. With under two months of history it says so instead of pretending.
 */
@Composable
internal fun BackTestCard(
    rows: List<BackTest.Row>,
    suggestion: SuggestionDto?,
    month: String,
    vis: AmountVisibilityState,
    say: Say,
    onRaise: (category: String, newLimit: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestion == null || rows.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val days = remember(month) { runCatching { YearMonth.parse(month).lengthOfMonth() }.getOrDefault(30) }
    val outcome = remember(rows, suggestion, days) { BackTest.run(rows, suggestion.monthlySpend, suggestion.monthLabels.size, days) }
    fun money(v: Double) = safeText(vis, formatInr(v))

    Column(modifier.fillMaxWidth().glassCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(say(R.string.pg_bt_title, emptyList()), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        if (outcome.tooLittle) {
            Text(say(R.string.pg_bt_low, emptyList()), fontSize = 13.sp, color = scheme.onSurfaceVariant)
        } else {
            val allHeld = outcome.monthsHeld == outcome.monthsTested
            Text(
                if (allHeld) say(R.string.pg_bt_all, listOf(outcome.monthsTested)) else say(R.string.pg_bt_some, listOf(outcome.monthsHeld, outcome.monthsTested)),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (allHeld) incomeColor() else SemanticWarning,
            )
            outcome.breaches.take(3).forEach { b ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        say(R.string.pg_bt_over, listOf(categoryLabel(b.category), b.monthsOver, outcome.monthsTested, money(b.peak))),
                        fontSize = 13.sp, color = scheme.onSurfaceVariant,
                    )
                    AppButton(say(R.string.pg_bt_raise, listOf(money(BackTest.fixLimit(b)))), onClick = { onRaise(b.category, BackTest.fixLimit(b)) }, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
                }
            }
        }
        if (outcome.perDay > 0) Text(say(R.string.pg_bt_day, listOf(money(outcome.perDay))), fontSize = 12.sp, color = scheme.onSurfaceVariant)
    }
}

/** Where a bucket's number came from, in words ("Based on 3 months of your spending"). */
internal fun basisText(item: PlanGuide.Item, say: Say): String = when (item.basis) {
    "history" -> say(R.string.pg_basis_history, listOf(item.months.coerceAtLeast(1)))
    "estimate" -> say(R.string.pg_basis_estimate, emptyList())
    "commitment" -> say(R.string.pg_basis_commitment, emptyList())
    "goal" -> say(R.string.pg_basis_goal, emptyList())
    "cushion" -> say(R.string.pg_basis_cushion, emptyList())
    "leftover" -> say(R.string.pg_basis_leftover, emptyList())
    else -> say(R.string.pg_basis_choice, emptyList())
}
