package com.example.spendsync.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shimmering placeholder primitive every skeleton in the app is built
 * from — a pulsing-alpha rounded box. Used wherever a screen is waiting on a
 * network call so the user sees "loading" at the exact spot the real value
 * will land, instead of a blank screen or a single spinner.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_alpha",
    )
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(shape)
            .background(base.copy(alpha = alpha * 0.22f)),
    )
}

/** A single line of placeholder text. */
@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    SkeletonBox(modifier = modifier.height(height), shape = RoundedCornerShape(4.dp))
}

/** A placeholder circle — avatar / icon badge. */
@Composable
fun SkeletonCircle(size: Dp) {
    SkeletonBox(modifier = Modifier.size(size), shape = CircleShape)
}
