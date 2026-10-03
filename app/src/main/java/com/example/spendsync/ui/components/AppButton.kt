package com.example.spendsync.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How loud a button is. Pick by importance, not by colour. */
enum class ButtonVariant {
    /** The one main action on a screen or dialog. */
    Primary,
    /** A supporting action next to a primary one. */
    Tonal,
    /** A neutral alternative with an outline. */
    Outline,
    /** The quietest: Cancel, Skip, inline links. */
    Text,
    /** A destructive main action (Delete, Clear). */
    Danger,
    /** A quiet destructive action. */
    DangerText,
}

enum class ButtonSize(val minHeight: Int, val horizontal: Int, val font: Int) {
    Small(40, 14, 13),
    Medium(48, 20, 15),
    Large(56, 24, 16),
}

/**
 * The only button used across SpendSync. Every screen, dialog and sheet builds on this so
 * colour, shape, size, motion and accessibility stay identical everywhere.
 *
 * Feel: springy press-in, soft hover/focus highlight for mouse, keyboard and stylus, a
 * ripple, and a light haptic tick. [loading] swaps the label for a spinner without changing
 * the button's size, and blocks repeat taps. Colours come only from the theme, so every
 * accent and both light/dark modes are covered. Long translations wrap instead of clipping.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    size: ButtonSize = ButtonSize.Medium,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val haptic = LocalHapticFeedback.current
    val active = enabled && !loading

    val (container, content) = variant.colors()
    val border = variant.border()

    val scale by animateFloatAsState(
        targetValue = if (pressed && active) 0.96f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "button_scale",
    )
    // Hover/press tint is a overlay of the content colour, so it works on any container.
    val overlay by animateColorAsState(
        targetValue = when {
            !active -> Color.Transparent
            pressed -> content.copy(alpha = 0.16f)
            hovered || focused -> content.copy(alpha = 0.09f)
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "button_overlay",
    )
    val shape = RoundedCornerShape(if (size == ButtonSize.Small) 12.dp else 16.dp)
    val disabledAlpha = if (enabled) 1f else 0.45f

    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = size.minHeight.dp)
            .defaultMinSize(minWidth = 64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = disabledAlpha
            }
            .clip(shape)
            .background(container)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .then(if (focused) Modifier.border(2.dp, scheme.primary.copy(alpha = 0.7f), shape) else Modifier)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = content),
                enabled = active,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .background(overlay)
            .padding(horizontal = size.horizontal.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Label stays in layout (invisible) while loading so the button never changes size.
        Row(
            modifier = Modifier
                .graphicsLayer { alpha = if (loading) 0f else 1f }
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = content,
                fontSize = size.font.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(trailingIcon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            }
        }
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp).semantics { contentDescription = text },
                color = content,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun ButtonVariant.colors(): Pair<Color, Color> {
    val s = MaterialTheme.colorScheme
    return when (this) {
        ButtonVariant.Primary -> s.primary to s.onPrimary
        ButtonVariant.Tonal -> s.primary.copy(alpha = 0.14f) to s.primary
        ButtonVariant.Outline -> Color.Transparent to s.primary
        ButtonVariant.Text -> Color.Transparent to s.primary
        ButtonVariant.Danger -> s.error to s.onError
        ButtonVariant.DangerText -> Color.Transparent to s.error
    }
}

@Composable
private fun ButtonVariant.border(): BorderStroke? =
    if (this == ButtonVariant.Outline) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline) else null

/**
 * Round icon-only button with the same motion and feedback as [AppButton]. [contentDescription]
 * is required — screen readers have nothing else to announce.
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Text,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val (container, content) = variant.colors()
    val iconColor = tint ?: if (variant == ButtonVariant.Text) MaterialTheme.colorScheme.onSurface else content
    val scale by animateFloatAsState(if (pressed && enabled) 0.88f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "icon_btn_scale")
    val overlay by animateColorAsState(
        when {
            !enabled -> Color.Transparent
            pressed -> iconColor.copy(alpha = 0.18f)
            hovered || focused -> iconColor.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        tween(120), label = "icon_btn_overlay",
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .clip(CircleShape)
            .background(container)
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape) else Modifier)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = ripple(color = iconColor), enabled = enabled, role = Role.Button, onClick = onClick)
            .background(overlay),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = iconColor, modifier = Modifier.size(22.dp))
    }
}
