package com.example.spendsync.ui.home

import com.example.spendsync.ui.i18n.categoryLabel
import androidx.annotation.StringRes
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import com.example.spendsync.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import com.example.spendsync.ui.components.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spendsync.data.remote.IconifyApiClient
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.ui.components.Skeleton
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.ui.shared.MaskableAmountText
import com.example.spendsync.ui.shared.TopBarDateSearchGroup
import com.example.spendsync.ui.theme.expenseColor
import com.example.spendsync.ui.theme.incomeColor
import com.example.spendsync.ui.transaction.builtInCategoryIcon
import java.time.LocalDate

/** Nested pages reachable from Home. */
enum class HomePage(@StringRes val titleRes: Int) {
    Balance(R.string.balance),
    Transactions(R.string.transactions);

    val title: String get() = tr(titleRes)
}

/** Which transactions the Transactions page shows. */
enum class TypeFilter(@StringRes val labelRes: Int) {
    All(R.string.all), Income(R.string.income), Expense(R.string.expenses);

    val label: String get() = tr(labelRes)
}

/** Counts smoothly from the previous value to [target] (first composition counts up from 0). */
@Composable
fun animatedAmount(target: Double): Double {
    val on = com.example.spendsync.ui.theme.LocalMotion.current.enabled(com.example.spendsync.ui.theme.MotionKind.Counts)
    var from by remember { mutableStateOf(target) } // first composition: show the value, no count-up
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target, on) {
        val current = from + (to - from) * progress.value
        // Loading placeholders (to or from 0) and tiny changes swap instantly; only a real change on screen counts over.
        if (!on || current == 0.0 || target == 0.0 || kotlin.math.abs(target - current) < 1.0) {
            from = target; to = target; progress.snapTo(1f)
        } else {
            from = current; to = target
            progress.snapTo(0f)
            progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
    }
    return from + (to - from) * progress.value
}

/** Card surface shared by every Home group — same frosted look as Settings. */
@Composable
fun Modifier.glassCard(radius: Int = 20): Modifier {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(radius.dp)
    return this
        .clip(shape)
        .background(scheme.surface)
        .border(BorderStroke(0.5.dp, scheme.outlineVariant.copy(alpha = 0.6f)), shape)
}

// ── Top bar ──────────────────────────────────────────────────────────────────

@Composable
fun HomeTopBar(
    title: String = "SpendSync",
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Savings,
    selectedDate: LocalDate,
    datePattern: String,
    onCalendarClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAssistantClick: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                com.example.spendsync.ui.components.AppIconButton(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    com.example.spendsync.ui.i18n.tr(com.example.spendsync.R.string.back),
                    onClick = onBack,
                )
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, color = scheme.onBackground, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        TopBarDateSearchGroup(
            selectedDate = selectedDate,
            onCalendarClick = onCalendarClick,
            onSearchClick = onSearchClick,
            contentColor = scheme.onSurface,
            groupBackgroundColor = scheme.surface,
            dateFormatPattern = datePattern,
            onAssistantClick = onAssistantClick,
        )
    }
}

// ── Balance hero ─────────────────────────────────────────────────────────────

@Composable
fun BalanceHero(
    balance: Double,
    holdNet: Double,
    isLoading: Boolean,
    amountVisibility: AmountVisibilityState,
    onOpenBalance: () -> Unit,
    onOpenHolds: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val onHero = scheme.onPrimary
    val animBalance = animatedAmount(if (isLoading) 0.0 else balance)
    val animHold = animatedAmount(if (isLoading) 0.0 else holdNet)
    val animTotal = animatedAmount(if (isLoading) 0.0 else balance + holdNet)
    // While loading, show wide placeholder numbers so the skeleton bones have a realistic width.
    val shownBalance = if (isLoading) 1234567.0 else animBalance
    val shownHold = if (isLoading) 12345.0 else animHold
    val shownTotal = if (isLoading) 1234567.0 else animTotal

    HeroCard(modifier) {
      Skeleton(loading = isLoading) {
       Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onOpenBalance),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr(R.string.net_balance), color = onHero.copy(alpha = 0.8f), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                MaskableAmountText(
                    amount = shownBalance,
                    visibility = amountVisibility,
                    color = onHero,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = tr(R.string.balance_details),
                tint = onHero.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroChip(
                label = tr(R.string.incl_holds),
                amount = shownTotal,
                visibility = amountVisibility,
                modifier = Modifier.weight(1f),
            )
            HeroChip(
                label = tr(R.string.on_hold),
                amount = shownHold,
                visibility = amountVisibility,
                modifier = Modifier.weight(1f),
                onClick = onOpenHolds,
            )
        }
       }
      }
    }
}

@Composable
fun HeroChip(
    label: String,
    amount: Double,
    visibility: AmountVisibilityState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val onHero = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(onHero.copy(alpha = 0.16f))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (onClick != null) "$label  →" else label, color = onHero.copy(alpha = 0.78f), fontSize = 11.5.sp)
        MaskableAmountText(amount, visibility, color = onHero, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Month summary ────────────────────────────────────────────────────────────

@Composable
fun MonthSummaryCard(
    monthLabel: String,
    income: Double,
    expenses: Double,
    isLoading: Boolean,
    amountVisibility: AmountVisibilityState,
    onIncome: () -> Unit,
    onExpenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val total = income + expenses
    val incomeShare by animateFloatAsState(
        targetValue = if (isLoading || total <= 0.0) 0f else (income / total).toFloat(),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "income_share",
    )

    Column(
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(tr(R.string.this_month), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = scheme.onSurface)
            Text(monthLabel, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }

        Skeleton(loading = isLoading) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowTile(tr(R.string.income), if (isLoading) 123456.0 else income, Icons.Default.ArrowUpward, incomeColor(), amountVisibility, onIncome, Modifier.weight(1f))
                FlowTile(tr(R.string.expenses), if (isLoading) 123456.0 else expenses, Icons.Default.ArrowDownward, expenseColor(), amountVisibility, onExpenses, Modifier.weight(1f))
            }
        }

        // Income vs expenses split for the month.
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(if (total > 0.0 && !isLoading) expenseColor().copy(alpha = 0.55f) else scheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(incomeShare)
                    .clip(CircleShape)
                    .background(incomeColor()),
            )
        }
    }
}

@Composable
private fun FlowTile(
    label: String,
    amount: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    visibility: AmountVisibilityState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = animatedAmount(amount)
    Column(
        modifier = modifier
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        MaskableAmountText(shown, visibility, color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Section header ───────────────────────────────────────────────────────────

@Composable
fun HomeSectionHeader(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground)
        if (actionLabel != null) {
            com.example.spendsync.ui.components.AppButton(
                text = actionLabel,
                onClick = onAction,
                variant = com.example.spendsync.ui.components.ButtonVariant.Text,
                size = com.example.spendsync.ui.components.ButtonSize.Small,
            )
        }
    }
}

// ── Day grouping ─────────────────────────────────────────────────────────────

@Composable
fun DayHeader(label: String, creditTotal: Double, debitTotal: Double, amountVisibility: AmountVisibilityState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (creditTotal > 0.0) {
                MaskableAmountText(creditTotal, amountVisibility, prefix = "+", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = incomeColor())
            }
            if (debitTotal > 0.0) {
                MaskableAmountText(debitTotal, amountVisibility, prefix = "-", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = expenseColor())
            }
        }
    }
}

/** One card per day; rows separated by hairlines. */
@Composable
fun DayCard(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glassCard()) { content() }
}

@Composable
fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        thickness = 0.6.dp,
    )
}

// ── Transaction row ──────────────────────────────────────────────────────────
// Tap = edit, swipe left = delete, swipe right = details. Swipes always snap
// back; delete still confirms in a dialog and the host removes the row.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionRow(
    transaction: TransactionDto,
    customCategoryIcons: Map<String, String>,
    amountVisibility: AmountVisibilityState,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val amountVal = remember(transaction.amount) { transaction.amount.toDoubleOrNull() ?: 0.0 }
    val isCredit = transaction.type == "credit"
    val tint = if (isCredit) incomeColor() else expenseColor()

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.StartToEnd -> onEdit() // swipe right = edit, tap = details
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.fillMaxWidth(),
        backgroundContent = {
            val (bg, icon, align) = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> Triple(scheme.error, Icons.Default.Delete, Alignment.CenterEnd)
                SwipeToDismissBoxValue.StartToEnd -> Triple(scheme.primary, Icons.Default.Edit, Alignment.CenterStart)
                SwipeToDismissBoxValue.Settled -> Triple(Color.Transparent, null, Alignment.Center)
            }
            Box(Modifier.fillMaxSize().background(bg), contentAlignment = align) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (bg == scheme.error) scheme.onError else scheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 24.dp).size(22.dp),
                    )
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .background(scheme.surface)
                .clickable(onClick = onInfo)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val customIconId = customCategoryIcons[transaction.category]
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (customIconId != null) {
                    AsyncImage(
                        model = IconifyApiClient.iconUrl(customIconId, colorHex = "#%06X".format(0xFFFFFF and tint.toArgb())),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(19.dp),
                    )
                } else {
                    Icon(builtInCategoryIcon(transaction.category) ?: Icons.Default.Star, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(categoryLabel(transaction.category), color = scheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, maxLines = 1)
                if (transaction.merchant.isNotBlank()) {
                    Text(transaction.merchant, color = scheme.onSurfaceVariant, fontSize = 12.5.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.width(12.dp))
            MaskableAmountText(
                amount = amountVal,
                visibility = amountVisibility,
                prefix = if (isCredit) "+ " else "- ",
                color = tint,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun HomeEmptyState(title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Savings, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scheme.onBackground)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 13.sp, color = scheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// ── Balance page rows ────────────────────────────────────────────────────────

@Composable
fun AmountRow(
    label: String,
    amount: Double,
    amountVisibility: AmountVisibilityState,
    color: Color = MaterialTheme.colorScheme.onSurface,
    hint: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.5.sp, color = scheme.onSurface)
            if (hint != null) Text(hint, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        MaskableAmountText(animatedAmount(amount), amountVisibility, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Staggered fade + rise driven by a hoisted [visible] flag, so lazy-list scrolling never replays it. */
@Composable
fun Modifier.introIn(visible: Boolean, index: Int): Modifier {
    val on = com.example.spendsync.ui.theme.LocalMotion.current.enabled(com.example.spendsync.ui.theme.MotionKind.Entrance)
    val p by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (on) tween(450, delayMillis = 70 * index, easing = FastOutSlowInEasing) else snap(),
        label = "intro_$index",
    )
    return this.graphicsLayer {
        alpha = p
        translationY = (1f - p) * 24.dp.toPx()
    }
}

/** Accent-gradient card with soft decorative rings — the headline surface on Home, Analytics and Budget. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(scheme.primary, lerp(scheme.primary, Color.Black, 0.38f))))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.width * 0.42f, center = Offset(size.width * 0.95f, -size.height * 0.05f))
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.width * 0.30f, center = Offset(size.width * 0.05f, size.height * 1.05f))
            }
            .padding(20.dp),
        content = content,
    )
}

/** Fades a bar/fill in from 0 to [target] once, then follows changes. */
@Composable
fun animatedFraction(target: Float): Float {
    val on = com.example.spendsync.ui.theme.LocalMotion.current.enabled(com.example.spendsync.ui.theme.MotionKind.Charts)
    var go by remember { mutableStateOf(!on) }
    LaunchedEffect(Unit) { go = true }
    val v by animateFloatAsState(if (go) target else 0f, if (on) tween(650, easing = FastOutSlowInEasing) else snap(), label = "fraction")
    return v
}

/**
 * Frosted card whose header opens a nested page. Only the header is the tap target, so
 * charts inside the card keep their own touch handling.
 */
@Composable
fun PreviewCard(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glassCard()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
            Text(tr(R.string.details), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = tr(R.string.open_1, title),
                tint = scheme.primary,
                modifier = Modifier.size(12.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}
