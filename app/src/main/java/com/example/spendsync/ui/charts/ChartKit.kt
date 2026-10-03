package com.example.spendsync.ui.charts

import com.example.spendsync.ui.i18n.LanguageManager
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DonutLarge
import com.example.spendsync.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import com.example.spendsync.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.ui.components.LocalSkeleton
import com.example.spendsync.ui.components.SkeletonBlock
import com.example.spendsync.ui.home.animatedAmount
import com.example.spendsync.ui.home.animatedFraction
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.MaskableAmountText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.roundToInt

// ── Data ─────────────────────────────────────────────────────────────────────

data class ChartPoint(val label: String, val value: Double)
data class BarSeries(val name: String, val color: Color)
data class BarGroup(val label: String, val values: List<Double>)
data class DonutSlice(val label: String, val value: Double, val color: Color)
data class RankedItem(val label: String, val value: Double, val color: Color, val count: Int = 0)

/** Compact rupee label for axes: ₹950, ₹1.2K, ₹3.4L, ₹1.1Cr. */
fun compactInr(v: Double): String = when {
    v >= 1e7 -> tr(R.string.s_1fcr).format(v / 1e7)
    v >= 1e5 -> tr(R.string.s_1fl).format(v / 1e5)
    v >= 1e3 -> tr(R.string.s_1fk).format(v / 1e3)
    else -> "₹%.0f".format(v)
}

/** Axis numbers would leak masked amounts, so they hide while masking is on and locked. */
private fun axisVisible(vis: AmountVisibilityState) = !(vis.isMaskingEnabled && !vis.isVisible)

// ── Popover ──────────────────────────────────────────────────────────────────

/**
 * Reserved strip above a chart. Shows [hint] until something is selected, then a
 * bubble (with [content]) that slides under the selected point at [anchor] (0..1 of the width).
 */
@Composable
fun PopoverLane(anchor: Float?, hint: String, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp)) {
        val laneW = constraints.maxWidth
        var w by remember { mutableIntStateOf(0) }
        AnimatedVisibility(anchor == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterStart)) {
            Text(hint, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        AnimatedVisibility(
            visible = anchor != null,
            enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.9f),
            exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.9f),
        ) {
            // Keep the last anchor while animating out.
            val a = remember(anchor) { anchor ?: 0.5f }
            Column(
                modifier = Modifier
                    .offset(a, laneW, w)
                    .onSizeChanged { w = it.width }
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.inverseSurface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                content = content,
            )
        }
    }
}

private fun Modifier.offset(anchor: Float, laneW: Int, w: Int): Modifier =
    this.then(Modifier.offset { IntOffset((anchor * laneW - w / 2f).coerceIn(0f, (laneW - w).coerceAtLeast(0).toFloat()).roundToInt(), 0) })

@Composable
private fun PopTitle(text: String) =
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f), maxLines = 1)

@Composable
private fun PopAmount(label: String?, amount: Double, vis: AmountVisibilityState) {
    val c = MaterialTheme.colorScheme.inverseOnSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label != null) Text("$label  ", fontSize = 12.sp, color = c.copy(alpha = 0.8f), maxLines = 1)
        MaskableAmountText(amount, vis, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c, maxLines = 1)
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
fun ChartEmpty(icon: ImageVector, text: String, height: Dp = 120.dp) {
    val mid = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxWidth().height(height),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = mid.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, fontSize = 12.sp, color = mid)
    }
}

// ── Line chart ───────────────────────────────────────────────────────────────

/**
 * Smoothed area line. Tap or drag anywhere to read a value; the bubble above follows your finger.
 * [guide] is an optional second dashed line on the same scale (e.g. even budget pace).
 */
@Composable
fun InteractiveLineChart(
    points: List<ChartPoint>,
    color: Color,
    vis: AmountVisibilityState,
    emptyIcon: ImageVector,
    emptyText: String,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 150.dp,
    valueLabel: String = tr(R.string.spent),
    guide: List<Double>? = null,
    guideLabel: String = tr(R.string.pace),
) {
    val scheme = MaterialTheme.colorScheme
    if (LocalSkeleton.current) { SkeletonBlock(plotHeight + 48.dp, modifier); return }
    if (points.isEmpty() || points.sumOf { it.value } <= 0.0) {
        ChartEmpty(emptyIcon, emptyText, plotHeight + 48.dp)
        return
    }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val reveal = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    val maxV = maxOf(points.maxOf { it.value }, guide?.maxOrNull() ?: 0.0).let { if (it <= 0.0) 1.0 else it }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = scheme.onSurfaceVariant)
    val showAxis = axisVisible(vis)
    val grid = scheme.outlineVariant
    val dotCenter = scheme.surface
    val n = points.size

    Column(modifier) {
        PopoverLane(
            anchor = selected?.let { if (n > 1) it / (n - 1f) else 0.5f },
            hint = tr(R.string.tap_or_drag_the_chart_to),
        ) {
            selected?.let { i ->
                PopTitle(points[i].label)
                PopAmount(valueLabel, points[i].value, vis)
                if (guide != null && i < guide.size) PopAmount(guideLabel, guide[i], vis)
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(plotHeight)
                .pointerInput(points) {
                    detectTapGestures { off ->
                        val step = if (n > 1) size.width / (n - 1f) else 1f
                        val i = (off.x / step).roundToInt().coerceIn(0, n - 1)
                        selected = if (selected == i) null else i
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures(onDragEnd = {}, onDragCancel = {}) { change, _ ->
                        val step = if (n > 1) size.width / (n - 1f) else 1f
                        selected = (change.position.x / step).roundToInt().coerceIn(0, n - 1)
                    }
                },
        ) {
            val h = size.height
            val stepX = if (n > 1) size.width / (n - 1) else 0f
            for (g in 0..3) {
                val y = h - (h * g / 3)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                if (showAxis && g > 0) {
                    drawText(measurer, compactInr(maxV * g / 3), Offset(2.dp.toPx(), y - 14.dp.toPx()), labelStyle)
                }
            }
            fun yOf(v: Double) = h - (v / maxV).toFloat() * h

            guide?.let { g ->
                val gp = Path()
                g.forEachIndexed { i, v -> if (i == 0) gp.moveTo(0f, yOf(v)) else gp.lineTo(i * stepX, yOf(v)) }
                clipRect(right = size.width * reveal.value) {
                    drawPath(gp, scheme.onSurfaceVariant.copy(alpha = 0.7f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                }
            }

            val coords = points.mapIndexed { i, p -> Offset(i * stepX, yOf(p.value)) }
            val line = Path()
            val fill = Path()
            line.moveTo(coords.first().x, coords.first().y)
            fill.moveTo(coords.first().x, h)
            fill.lineTo(coords.first().x, coords.first().y)
            for (i in 1 until coords.size) {
                val p = coords[i - 1]
                val c = coords[i]
                val mx = (p.x + c.x) / 2f
                val my = (p.y + c.y) / 2f
                if (i == 1) { line.lineTo(mx, my); fill.lineTo(mx, my) } else { line.quadraticTo(p.x, p.y, mx, my); fill.quadraticTo(p.x, p.y, mx, my) }
                if (i == coords.lastIndex) { line.quadraticTo(c.x, c.y, c.x, c.y); fill.quadraticTo(c.x, c.y, c.x, c.y) }
            }
            fill.lineTo(coords.last().x, h)
            fill.close()

            clipRect(right = size.width * reveal.value) {
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.24f), color.copy(alpha = 0f))))
                drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }

            selected?.let { i ->
                val c = coords[i]
                drawLine(color.copy(alpha = 0.5f), Offset(c.x, 0f), Offset(c.x, h), strokeWidth = 1.5.dp.toPx())
                drawCircle(color, 6.dp.toPx(), c)
                drawCircle(dotCenter, 3.dp.toPx(), c)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().label, fontSize = 11.sp, color = scheme.onSurfaceVariant)
            if (n > 1) Text(points.last().label, fontSize = 11.sp, color = scheme.onSurfaceVariant)
        }
    }
}

// ── Bar chart (single or grouped) ────────────────────────────────────────────

/** Vertical bars, one group per label. Tap a group to read every series in its bubble. */
@Composable
fun InteractiveBarChart(
    groups: List<BarGroup>,
    series: List<BarSeries>,
    vis: AmountVisibilityState,
    emptyIcon: ImageVector,
    emptyText: String,
    modifier: Modifier = Modifier,
    plotHeight: Dp = 150.dp,
    hint: String = tr(R.string.tap_a_bar_to_see_details),
) {
    val scheme = MaterialTheme.colorScheme
    val maxV = groups.maxOfOrNull { g -> g.values.maxOrNull() ?: 0.0 } ?: 0.0
    if (LocalSkeleton.current) { SkeletonBlock(plotHeight + 48.dp, modifier); return }
    if (groups.isEmpty() || maxV <= 0.0) {
        ChartEmpty(emptyIcon, emptyText, plotHeight + 48.dp)
        return
    }
    var selected by remember(groups) { mutableStateOf<Int?>(null) }
    val n = groups.size
    val labelEvery = maxOf(1, (n + 5) / 6)

    Column(modifier) {
        PopoverLane((selected?.let { (it + 0.5f) / n }), hint) {
            selected?.let { i ->
                PopTitle(groups[i].label)
                series.forEachIndexed { s, ser -> PopAmount(if (series.size > 1) ser.name else null, groups[i].values.getOrElse(s) { 0.0 }, vis) }
            }
        }
        Row(Modifier.fillMaxWidth().height(plotHeight), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEachIndexed { i, g ->
                val dim = selected != null && selected != i
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected == i) scheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable(role = Role.Button) { selected = if (selected == i) null else i },
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    series.forEachIndexed { s, ser ->
                        val frac = animatedFraction(((g.values.getOrElse(s) { 0.0 }) / maxV).toFloat().coerceIn(0f, 1f))
                        Box(
                            Modifier
                                .weight(1f, fill = false)
                                .width(if (series.size > 1) 12.dp else 22.dp)
                                .fillMaxHeight(maxOf(frac, 0.02f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(ser.color.copy(alpha = if (dim) 0.4f else 1f)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.forEachIndexed { i, g ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % labelEvery == 0) Text(g.label, fontSize = 10.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        if (series.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                series.forEach { ser ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(ser.color))
                        Spacer(Modifier.width(6.dp))
                        Text(ser.name, fontSize = 11.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ── Donut ────────────────────────────────────────────────────────────────────

/** Part-to-whole ring. Tap a slice or its legend row; the centre shows that slice, tap again to clear. */
@Composable
fun InteractiveDonut(
    slices: List<DonutSlice>,
    vis: AmountVisibilityState,
    totalLabel: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val total = slices.sumOf { it.value }
    if (LocalSkeleton.current) { SkeletonBlock(300.dp, modifier); return }
    if (slices.isEmpty() || total <= 0.0) {
        ChartEmpty(Icons.Default.DonutLarge, tr(R.string.nothing_to_break_down_yet))
        return
    }
    var selected by remember(slices) { mutableStateOf<Int?>(null) }
    val reveal = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { reveal.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val gap = 2f

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(slices) {
                    detectTapGestures { off ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val dx = off.x - c.x
                        val dy = off.y - c.y
                        val r = kotlin.math.sqrt(dx * dx + dy * dy)
                        val outer = size.width / 2f
                        if (r < outer * 0.5f || r > outer) { selected = null; return@detectTapGestures }
                        var ang = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
                        if (ang < 0) ang += 360f
                        var start = 0f
                        slices.forEachIndexed { i, s ->
                            val sweep = (s.value / total * 360f).toFloat()
                            if (ang >= start && ang < start + sweep) selected = if (selected == i) null else i
                            start += sweep
                        }
                    }
                },
            ) {
                val stroke = 30.dp.toPx()
                val inset = stroke / 2f + 6.dp.toPx()
                var start = -90f
                slices.forEachIndexed { i, s ->
                    val sweep = (s.value / total * 360f).toFloat() * reveal.value
                    val isSel = selected == i
                    val w = if (isSel) stroke + 8.dp.toPx() else stroke
                    val pad = inset - (w - stroke) / 2f
                    drawArc(
                        color = s.color.copy(alpha = if (selected != null && !isSel) 0.35f else 1f),
                        startAngle = start + gap / 2f,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = Offset(pad, pad),
                        size = Size(size.width - pad * 2, size.height - pad * 2),
                        style = Stroke(w, cap = StrokeCap.Butt),
                    )
                    start += (s.value / total * 360f).toFloat()
                }
            }
            val sel = selected?.let { slices[it] }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(36.dp)) {
                Text(sel?.label ?: totalLabel, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
                MaskableAmountText(sel?.value ?: total, vis, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1)
                if (sel != null) Text("%.1f%%".format(sel.value / total * 100), fontSize = 12.sp, color = sel.color, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            slices.forEachIndexed { i, s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected == i) s.color.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable(role = Role.Button) { selected = if (selected == i) null else i }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                    Spacer(Modifier.width(10.dp))
                    Text(s.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("%.0f%%".format(s.value / total * 100), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Ranked bars ──────────────────────────────────────────────────────────────

/** Horizontal ranked bars. Tap a row to expand its share, transaction count and average. */
@Composable
fun RankedBars(
    items: List<RankedItem>,
    total: Double,
    vis: AmountVisibilityState,
    emptyIcon: ImageVector,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    if (LocalSkeleton.current) { SkeletonBlock((items.size.coerceAtLeast(2) * 52).dp, modifier); return }
    if (items.isEmpty()) {
        ChartEmpty(emptyIcon, emptyText, 100.dp)
        return
    }
    val maxAmount = items.maxOf { it.value }.let { if (it <= 0.0) 1.0 else it }
    var selected by remember(items) { mutableStateOf<String?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            val pct = if (total > 0) item.value / total * 100 else 0.0
            val frac = animatedFraction((item.value / maxAmount).toFloat().coerceIn(0f, 1f))
            val open = selected == item.label
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (open) item.color.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable(role = Role.Button) { selected = if (open) null else item.label }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(item.color))
                        Spacer(Modifier.width(8.dp))
                        Text(item.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, maxLines = 1)
                    }
                    MaskableAmountText(item.value, vis, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    Text("  ·  %.1f%%".format(pct), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(scheme.outlineVariant.copy(alpha = 0.5f))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(item.color))
                }
                AnimatedVisibility(open) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (item.count > 0) {
                            Detail(tr(R.string.transactions), item.count.toString())
                            Column {
                                Text(tr(R.string.average), fontSize = 11.sp, color = scheme.onSurfaceVariant)
                                MaskableAmountText(item.value / item.count, vis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                            }
                        }
                        Detail(tr(R.string.share), "%.1f%%".format(pct))
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Text(label, fontSize = 11.sp, color = scheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
    }
}

// ── Heatmap calendar ─────────────────────────────────────────────────────────

/** Calendar where darker days are bigger spends. Tap a day for its total. */
@Composable
fun SpendHeatmap(
    daily: Map<LocalDate, Double>,
    start: LocalDate,
    end: LocalDate,
    color: Color,
    vis: AmountVisibilityState,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val max = daily.values.maxOrNull() ?: 0.0
    if (LocalSkeleton.current) { SkeletonBlock(260.dp, modifier); return }
    if (max <= 0.0) {
        ChartEmpty(Icons.Default.CalendarMonth, tr(R.string.no_spending_to_map_in_this))
        return
    }
    val gridStart = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = ((ChronoUnit.DAYS.between(gridStart, end) / 7) + 1).toInt()
    var selected by remember(daily) { mutableStateOf<LocalDate?>(null) }
    val fmt = remember(LanguageManager.current) { DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()) }

    Column(modifier) {
        PopoverLane(
            anchor = selected?.let { ((it.dayOfWeek.value - 1) + 0.5f) / 7f },
            hint = tr(R.string.tap_a_day_to_see_what),
        ) {
            selected?.let { d ->
                PopTitle(d.format(fmt))
                PopAmount(null, daily[d] ?: 0.0, vis)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), fontSize = 10.sp, color = scheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(weeks) { w ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { d ->
                        val date = gridStart.plusDays((w * 7 + d).toLong())
                        val inRange = !date.isBefore(start) && !date.isAfter(end)
                        val v = if (inRange) daily[date] ?: 0.0 else 0.0
                        val bg = when {
                            !inRange -> Color.Transparent
                            v <= 0.0 -> scheme.surfaceVariant.copy(alpha = 0.6f)
                            else -> color.copy(alpha = 0.18f + 0.82f * (v / max).toFloat())
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .then(if (selected == date) Modifier.border(2.dp, scheme.onSurface, RoundedCornerShape(8.dp)) else Modifier)
                                .then(if (inRange) Modifier.clickable(role = Role.Button) { selected = if (selected == date) null else date } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (inRange) Text(
                                date.dayOfMonth.toString(),
                                fontSize = 10.sp,
                                color = if (v / max > 0.55) Color.White else scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
