package com.example.spendsync.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Tactile press-down feedback for hand-rolled `Box/Card + .clickable` action
 * surfaces — the same scale-on-press feel [PrimaryButton] already has via
 * Material [androidx.compose.material3.Button], without requiring every
 * custom-styled action surface (a dialog's Confirm, a screen's Save button)
 * to restructure into one.
 *
 * The returned [MutableInteractionSource] must be passed to that same
 * surface's own `.clickable(interactionSource = ...)` — the press state is
 * read from it, so both need to reference the same instance.
 */
@Composable
fun rememberPressScale(pressedScale: Float = 0.96f): Pair<Modifier, MutableInteractionSource> {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = tween(100),
        label = "press_scale",
    )
    val modifier = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    return modifier to interactionSource
}
