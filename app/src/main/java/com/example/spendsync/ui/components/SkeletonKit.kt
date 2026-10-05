package com.example.spendsync.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Automatic skeleton screens.
 *
 * Instead of hand-drawing a second "loading layout" per screen, a screen renders its REAL
 * layout with placeholder data inside [Skeleton]. While `loading` is true, every [Text] and
 * [Icon] from this package turns into a shimmering bone of exactly its own size, and
 * containers, cards, spacing and dividers stay as they are — so the skeleton can never drift
 * out of sync with the real design, in any language or font size.
 *
 * Usage:
 * ```
 * Skeleton(loading = isLoading) { BalanceHero(balance = data?.balance ?: 12345.0, ...) }
 * ```
 */
val LocalSkeleton = compositionLocalOf { false }

/** 0..1, looping — one clock shared by every bone so a whole screen shimmers in step. */
private val LocalShimmerProgress = compositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

@Composable
fun Skeleton(
    loading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shimmer = com.example.spendsync.ui.theme.LocalMotion.current.enabled(com.example.spendsync.ui.theme.MotionKind.Skeleton)
    val progress: State<Float> = if (loading && shimmer) {
        rememberInfiniteTransition(label = "skeleton_clock").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
            label = "skeleton_progress",
        )
    } else remember { mutableFloatStateOf(0f) }

    CompositionLocalProvider(LocalSkeleton provides loading, LocalShimmerProgress provides progress) {
        Box(
            modifier = modifier.then(
                if (loading) {
                    // Placeholder data must not be tappable or announced.
                    Modifier
                        .clearAndSetSemantics { }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                } else Modifier
            ),
        ) { content() }
    }
}

/** Shimmering rounded-rectangle fill. Reads the shared clock in the draw phase (no recomposition). */
@Composable
fun Modifier.skeletonBone(radius: Dp = 6.dp): Modifier {
    val progress = LocalShimmerProgress.current
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    // Each bone is drawn a little shorter than its box, so lines that sit right above each other (a label over an
    // amount, two rows of a list) keep a visible gap instead of fusing into one block.
    return this.drawBehind {
        val inset = (size.height * 0.14f).coerceIn(1.dp.toPx(), 4.dp.toPx())
        val r = radius.toPx()
        val shape = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, inset, size.width, size.height - inset, r, r))
        }
        clipPath(shape) {
            drawRect(base)
            val band = size.width * 0.6f
            val x = progress.value * (size.width + band) - band
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, highlight, Color.Transparent), startX = x, endX = x + band))
        }
    }
}

/** A free-standing placeholder block (charts, images) used while [LocalSkeleton] is on. */
@Composable
fun SkeletonBlock(height: Dp, modifier: Modifier = Modifier, radius: Dp = 12.dp) {
    Box(modifier.fillMaxWidth().height(height).skeletonBone(radius))
}

// ── Skeleton-aware Text ───────────────────────────────────────────────────────
// Same signature as androidx.compose.material3.Text, so call sites only change their import.

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val skeleton = LocalSkeleton.current
    androidx.compose.material3.Text(
        text = text,
        modifier = if (skeleton) modifier.skeletonBone(radius = 5.dp) else modifier,
        color = if (skeleton) Color.Transparent else color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    val skeleton = LocalSkeleton.current
    androidx.compose.material3.Text(
        text = text,
        modifier = if (skeleton) modifier.skeletonBone(radius = 5.dp) else modifier,
        color = if (skeleton) Color.Transparent else color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}

// ── Skeleton-aware Icon ───────────────────────────────────────────────────────

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val skeleton = LocalSkeleton.current
    androidx.compose.material3.Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = if (skeleton) modifier.skeletonBone(radius = 6.dp) else modifier,
        tint = if (skeleton) Color.Transparent else tint,
    )
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val skeleton = LocalSkeleton.current
    androidx.compose.material3.Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = if (skeleton) modifier.skeletonBone(radius = 6.dp) else modifier,
        tint = if (skeleton) Color.Transparent else tint,
    )
}
