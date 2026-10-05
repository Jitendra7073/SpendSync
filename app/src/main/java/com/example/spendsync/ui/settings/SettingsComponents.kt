package com.example.spendsync.ui.settings

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import com.example.spendsync.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.example.spendsync.ui.components.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppIconButton
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.theme.SemanticError

/** Max content width — keeps lists readable on tablets / foldables instead of stretching edge to edge. */
private val ContentMaxWidth = 600.dp

// ── Backdrop ─────────────────────────────────────────────────────────────────

/**
 * Slowly drifting colour blobs behind every settings surface. Colours come only
 * from [MaterialTheme.colorScheme] (so it follows light/dark and the accent
 * choice) and the blobs are drawn in one Canvas — no per-frame recomposition.
 */
@Composable
fun SettingsBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val drift = rememberInfiniteTransition(label = "settings_backdrop")
    val a by drift.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a",
    )
    val b by drift.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse),
        label = "b",
    )
    val isDark = scheme.background.luminance() < 0.5f
    val strength = if (isDark) 0.28f else 0.20f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .drawBehind {
                val w = size.width
                val h = size.height
                fun blob(color: Color, cx: Float, cy: Float, r: Float) = drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = strength), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        radius = r,
                    ),
                    radius = r,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                )
                blob(scheme.primary, w * (0.15f + 0.45f * a), h * (0.08f + 0.10f * b), w * 0.85f)
                blob(scheme.tertiary, w * (0.95f - 0.40f * b), h * (0.40f + 0.12f * a), w * 0.75f)
                blob(scheme.secondary, w * (0.10f + 0.30f * b), h * (0.85f - 0.10f * a), w * 0.70f)
            },
        content = content,
    )
}

/** Centers content and caps its width on large screens. */
@Composable
fun SettingsContentWidth(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), content = content)
    }
}

// ── Motion ───────────────────────────────────────────────────────────────────

/** Fade + rise entrance, delayed per [index] so a page's groups cascade in. Runs once per composition. */
fun Modifier.cascadeIn(index: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(420, delayMillis = 45 * index.coerceAtMost(8), easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

// ── Structure ────────────────────────────────────────────────────────────────

@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back), onClick = onBack)
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small caption above a [SettingsGroup]. */
@Composable
fun SettingsGroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp, top = 4.dp),
    )
}

/** Frosted card that holds a run of rows separated by hairlines. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surface.copy(alpha = 0.88f))
                .border(BorderStroke(0.5.dp, scheme.outlineVariant.copy(alpha = 0.6f)), RoundedCornerShape(20.dp)),
            content = content,
        )
        if (footer != null) {
            Text(
                text = footer,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        thickness = 0.6.dp,
    )
}

// ── Rows ─────────────────────────────────────────────────────────────────────

@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative: the row's own label already names the action.
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun RowText(
    title: String,
    subtitle: String?,
    titleColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = titleColor)
        if (subtitle != null) {
            Text(
                subtitle,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tappable row that opens a page, dialog or sheet. [value] is the current selection shown on the right. */
@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    destructive: Boolean = false,
    chevron: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (destructive) SemanticError else scheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(14.dp))
        RowText(title, subtitle, if (destructive) SemanticError else scheme.onSurface, Modifier.weight(1f))
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                value,
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/** Whole-row switch: the row (not just the thumb) is the touch target and is announced as a Switch. */
@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, scheme.primary)
        Spacer(Modifier.width(14.dp))
        RowText(title, subtitle, scheme.onSurface, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        // Row owns the click + semantics; the Switch is purely visual.
        Box(Modifier.clearAndSetSemantics { }) {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = scheme.onPrimary,
                    checkedTrackColor = scheme.primary,
                    uncheckedThumbColor = scheme.outline,
                    uncheckedTrackColor = scheme.surfaceVariant,
                    uncheckedBorderColor = scheme.outline,
                ),
            )
        }
    }
}

/** Read-only label/value line (account details). */
@Composable
fun SettingsInfoRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = scheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

// ── Selection controls ───────────────────────────────────────────────────────

/** One choice in a [SettingsSegmented]: [key] is what is stored, [label] is what the user reads. */
data class SegmentOption(val key: String, val label: String, val icon: ImageVector)

/** Segmented pill with a sliding thumb. [labels] are shown and also returned as the selected key. */
@Composable
fun SettingsSegmented(
    options: List<SegmentOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val index = options.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    BoxWithWidth(modifier.padding(16.dp).fillMaxWidth().heightIn(min = 52.dp)) { width ->
        val cell = width / options.size
        val offset by animateDpAsState(cell * index, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium), label = "seg")
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.7f))
        ) {
            Box(
                Modifier
                    .padding(4.dp)
                    // offset, not padding: the bouncy spring overshoots below zero and padding() throws on negatives
                    .offset(x = offset)
                    .width((cell - 8.dp).coerceAtLeast(0.dp))
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.primary)
                    .align(Alignment.CenterStart)
            )
            Row(Modifier.fillMaxWidth().padding(4.dp)) {
                options.forEach { (key, label, icon) ->
                    val isSel = key == selected
                    val tint by animateColorAsState(
                        if (isSel) scheme.onPrimary else scheme.onSurfaceVariant, tween(200), label = "segTint",
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = isSel, role = Role.RadioButton, onClick = { onSelect(key) })
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

/** Measures its own width in dp and hands it to [content]. */
@Composable
private fun BoxWithWidth(modifier: Modifier, content: @Composable (androidx.compose.ui.unit.Dp) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) { content(maxWidth) }
}

/** Row of colour dots; the selected one grows and shows a check. Wraps on narrow screens / large font. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsSwatches(
    options: List<Pair<String, Color>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        options.forEach { (name, color) ->
            val isSel = name == selected
            val size by animateDpAsState(if (isSel) 52.dp else 44.dp, spring(Spring.DampingRatioMediumBouncy), label = "swatch")
            Box(
                modifier = Modifier.size(52.dp)
                    .selectable(selected = isSel, role = Role.RadioButton, onClick = { onSelect(name) }),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSel) 3.dp else 0.dp,
                            color = scheme.onSurface.copy(alpha = 0.85f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSel) Icon(Icons.Default.Check, contentDescription = com.example.spendsync.ui.i18n.accentLabel(name), tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/** "This device" / "Synced" origin tag so users know whether a setting follows them to other devices. */
@Composable
fun ScopeTag(synced: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = if (synced) tr(R.string.synced_to_your_account) else tr(R.string.stored_on_this_device_only),
        fontSize = 11.sp,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

/** Inline status banner, e.g. a missing permission. */
@Composable
fun SettingsBanner(
    icon: ImageVector,
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.errorContainer.copy(alpha = 0.55f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.error, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = scheme.onErrorContainer, modifier = Modifier.weight(1f))
        if (actionLabel != null) {
            Spacer(Modifier.width(8.dp))
            AppButton(actionLabel, onAction, variant = ButtonVariant.Danger, size = ButtonSize.Small)
        }
    }
}
