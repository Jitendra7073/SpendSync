package com.example.spendsync.ui.planify

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.planify.PlanMath
import com.example.spendsync.data.remote.model.BucketDto
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.ui.charts.BarGroup
import com.example.spendsync.ui.charts.BarSeries
import com.example.spendsync.ui.charts.ChartPoint
import com.example.spendsync.ui.charts.InteractiveBarChart
import com.example.spendsync.ui.charts.InteractiveLineChart
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.home.animatedFraction
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.i18n.categoryLabel
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.MaskableAmountText
import com.example.spendsync.ui.theme.SemanticWarning
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
import java.time.LocalDate
import java.time.YearMonth

internal fun kindOrder(kind: String) = when (kind) { "fixed" -> 0; "spend" -> 1; else -> 2 }

/** The bucket's own name, or the translated category when it was never renamed. */
internal fun BucketDto.title(): String = if (name.isBlank() || name == category) categoryLabel(category) else name

@Composable
internal fun stateColor(state: String): Color = when (state) {
    "over" -> expenseColor()
    "close" -> SemanticWarning
    "paid", "saved" -> incomeColor()
    else -> MaterialTheme.colorScheme.primary
}

internal fun verdictLabel(b: BucketDto): String = tr(
    when (PlanMath.verdict(b)) {
        PlanMath.Verdict.OnTrack -> R.string.on_track
        PlanMath.Verdict.Close -> R.string.near_limit
        PlanMath.Verdict.Reached -> R.string.pl_v_reached
        PlanMath.Verdict.Over -> R.string.over
        PlanMath.Verdict.Paid -> R.string.pl_v_paid
        PlanMath.Verdict.Saved -> R.string.pl_v_saved
    },
)

internal fun groupTitle(kind: String): String = tr(
    when (kind) { "fixed" -> R.string.pl_group_fixed; "savings" -> R.string.pl_group_savings; else -> R.string.pl_group_spend },
)

/** One bucket on the plan home: how full it is, what is left, and (when over) a way to deal with it. */
@Composable
internal fun BucketCard(
    b: BucketDto,
    vis: AmountVisibilityState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFix: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val color = stateColor(b.state)
    val progress = animatedFraction((b.percent / 100.0).toFloat().coerceIn(0f, 1f))
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glassCard()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(b.title(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = scheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1)
            MaskableAmountText(b.spent, vis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = scheme.onSurface)
            Text(" / ", fontSize = 13.sp, color = scheme.onSurfaceVariant)
            MaskableAmountText(b.limit, vis, fontSize = 13.sp, color = scheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(scheme.outlineVariant.copy(alpha = 0.5f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(color))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(verdictLabel(b), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (b.remaining >= 0) {
                    MaskableAmountText(b.remaining, vis, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    Text(" " + tr(if (b.kind == "savings") R.string.pl_to_go else R.string.pl_left), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                } else {
                    Text(tr(R.string.over_by) + " ", fontSize = 12.sp, color = color)
                    MaskableAmountText(-b.remaining, vis, fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (b.paceWarning && b.runsOutOnDay != null) {
            Spacer(Modifier.height(6.dp))
            Text(tr(R.string.pl_pace_line, b.runsOutOnDay), fontSize = 12.sp, color = SemanticWarning)
        }
        if (onFix != null) {
            Spacer(Modifier.height(10.dp))
            AppButton(tr(R.string.pl_what_now), onClick = onFix, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
        }
    }
}

/** Cumulative spend by day for [month] (up to today if it is the current month) plus the even-pace line. */
private fun paceSeries(debits: List<TransactionDto>, month: LocalDate, budget: Double): Pair<List<ChartPoint>, List<Double>> {
    val len = month.lengthOfMonth()
    val last = if (YearMonth.from(month) == YearMonth.now()) LocalDate.now().dayOfMonth else len
    val byDay = HashMap<Int, Double>()
    debits.forEach { t ->
        val d = try { java.time.ZonedDateTime.parse(t.createdAt).dayOfMonth } catch (e: Exception) { null }
        if (d != null) byDay[d] = (byDay[d] ?: 0.0) + (t.amount.toDoubleOrNull() ?: 0.0)
    }
    var run = 0.0
    val points = (1..last).map { d ->
        run += byDay[d] ?: 0.0
        ChartPoint(tr(R.string.day_1, d), run)
    }
    val guide = (1..last).map { d -> budget * d / len }
    return points to guide
}

/** Spend so far against an even spread of the limit across the month. Tap or drag to compare any day. */
@Composable
internal fun PaceCard(
    debits: List<TransactionDto>,
    month: LocalDate,
    budget: Double,
    vis: AmountVisibilityState,
    modifier: Modifier = Modifier,
    title: String = tr(R.string.spending_pace),
) {
    val scheme = MaterialTheme.colorScheme
    val (points, guide) = remember(debits, month, budget) { paceSeries(debits, month, budget) }
    val gap = (points.lastOrNull()?.value ?: 0.0) - (guide.lastOrNull() ?: 0.0)
    val gapColor = if (gap > 0) expenseColor() else incomeColor()
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(16.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (gap > 0) tr(R.string.ahead_of_pace_by) else tr(R.string.under_pace_by), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = gapColor)
            MaskableAmountText(kotlin.math.abs(gap), vis, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = gapColor)
            Text(tr(R.string.dashed_even_spread), fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        InteractiveLineChart(
            points = points,
            color = if (gap > 0) expenseColor() else scheme.primary,
            vis = vis,
            emptyIcon = Icons.Default.PieChart,
            emptyText = tr(R.string.no_spending_yet_this_month),
            plotHeight = 140.dp,
            valueLabel = tr(R.string.spent_so_far),
            guide = guide,
            guideLabel = tr(R.string.even_pace),
        )
    }
}

/** Limit against spent for each bucket, side by side. Tap a pair for the numbers. */
@Composable
internal fun PlanVsSpentCard(buckets: List<BucketDto>, vis: AmountVisibilityState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(16.dp)) {
        Text(tr(R.string.budget_vs_spent), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        Spacer(Modifier.height(4.dp))
        InteractiveBarChart(
            groups = buckets.take(6).map { BarGroup(it.title(), listOf(it.limit, it.spent)) },
            series = listOf(BarSeries(tr(R.string.limit), scheme.outline), BarSeries(tr(R.string.spent), scheme.primary)),
            vis = vis,
            emptyIcon = Icons.Default.PieChart,
            emptyText = tr(R.string.no_category_budgets_yet_2),
            plotHeight = 140.dp,
            hint = tr(R.string.tap_a_category_to_compare),
        )
    }
}
