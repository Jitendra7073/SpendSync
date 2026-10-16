package com.example.spendsync.ui.assistant

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.theme.LocalMotion
import com.example.spendsync.ui.theme.MotionKind
import com.example.spendsync.voice.VoicePhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ChatGPT-style real-time voice visualizer.
 *
 * Renders an organic, fluid multi-layered animated orb that smoothly morphs across states:
 * - [VoicePhase.Listening]: Dynamically reacts to microphone audio level with spring physics and outward radiant rings.
 * - [VoicePhase.Thinking]: Rotates with a fluid dual-gradient swirling aura to signify neural cognition.
 * - [VoicePhase.Speaking]: Undulates harmonically with rhythmic sine wave pulses simulating live assistant speech.
 * - [VoicePhase.Paused]: Subdued resting breath state inviting the user to tap.
 *
 * Respects [LocalMotion] to disable motion when user or accessibility preferences require it.
 */
@Composable
fun VoiceVisualizer(
    phase: VoicePhase,
    audioLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualizerSize: Dp = 220.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    val animate = motion.enabled(MotionKind.Transitions)

    // Smooth audio reactivity with spring physics for natural fluid expansion
    val targetAudioScale = if (phase == VoicePhase.Listening) {
        1f + (audioLevel.coerceIn(0f, 1f) * 0.75f)
    } else {
        1f
    }
    val reactiveAudioScale by animateFloatAsState(
        targetValue = if (animate) targetAudioScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "voice_audio_scale",
    )

    // Infinite transitions for ambient breathing, thinking spin, and speaking wave harmonics
    val infiniteTransition = rememberInfiniteTransition(label = "voice_infinite_transition")

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice_breathing_scale",
    )

    val thinkingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "voice_thinking_rotation",
    )

    val speakingWaveHarmonic by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "voice_speaking_wave",
    )

    val ripplePulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "voice_ripple_pulse",
    )

    val primaryColor = scheme.primary
    val primaryContainer = scheme.primaryContainer
    val tertiaryColor = scheme.tertiary
    val secondaryColor = scheme.secondary
    val surfaceVariant = scheme.surfaceVariant

    Box(
        modifier = modifier
            .size(visualizerSize)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = visualizerSize / 2),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (animate && phase == VoicePhase.Thinking) {
                        rotationZ = thinkingRotation
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.28f

            when (phase) {
                VoicePhase.Listening -> {
                    val currentScale = if (animate) reactiveAudioScale * breathingScale else 1f
                    drawListeningOrb(
                        center = center,
                        baseRadius = baseRadius,
                        scale = currentScale,
                        audioLevel = audioLevel,
                        rippleProgress = if (animate) ripplePulse else 0.5f,
                        primaryColor = primaryColor,
                        accentColor = tertiaryColor,
                    )
                }
                VoicePhase.Thinking -> {
                    drawThinkingOrb(
                        center = center,
                        baseRadius = baseRadius,
                        rotationRad = if (animate) (thinkingRotation * PI / 180f).toFloat() else 0f,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        tertiaryColor = tertiaryColor,
                    )
                }
                VoicePhase.Speaking -> {
                    drawSpeakingOrb(
                        center = center,
                        baseRadius = baseRadius,
                        wavePhase = if (animate) speakingWaveHarmonic else 0f,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                    )
                }
                VoicePhase.Paused, VoicePhase.Off -> {
                    drawPausedOrb(
                        center = center,
                        baseRadius = baseRadius,
                        breathing = if (animate) breathingScale else 1f,
                        surfaceVariant = surfaceVariant,
                        primaryColor = primaryColor,
                    )
                }
            }
        }

        // Center state icon badge with subtle elevation
        val iconTint = when (phase) {
            VoicePhase.Paused, VoicePhase.Off -> scheme.onSurfaceVariant
            else -> scheme.onPrimary
        }
        val icon = when (phase) {
            VoicePhase.Listening -> Icons.Default.Mic
            VoicePhase.Thinking -> Icons.Default.Psychology
            VoicePhase.Speaking -> Icons.Default.GraphicEq
            VoicePhase.Paused -> Icons.Default.Pause
            VoicePhase.Off -> Icons.Default.Mic
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(36.dp),
        )
    }
}

/** Draws the reactive listening orb with expanding audio-reactive ripple rings. */
private fun DrawScope.drawListeningOrb(
    center: Offset,
    baseRadius: Float,
    scale: Float,
    audioLevel: Float,
    rippleProgress: Float,
    primaryColor: Color,
    accentColor: Color,
) {
    val orbRadius = (baseRadius * scale).coerceAtMost(size.minDimension * 0.44f)

    // Outer subtle sound wave rings
    val maxRippleRadius = size.minDimension * 0.48f
    val rippleRadius = orbRadius + (maxRippleRadius - orbRadius) * rippleProgress
    val rippleAlpha = ((1f - rippleProgress) * (0.28f + audioLevel * 0.4f)).coerceIn(0f, 0.6f)
    drawCircle(
        color = primaryColor.copy(alpha = rippleAlpha),
        radius = rippleRadius,
        center = center,
    )

    // Soft radiant aura halo
    val haloRadius = orbRadius * 1.35f
    val auraBrush = Brush.radialGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.35f + audioLevel * 0.3f),
            primaryColor.copy(alpha = 0.12f),
            Color.Transparent,
        ),
        center = center,
        radius = haloRadius,
    )
    drawCircle(brush = auraBrush, radius = haloRadius, center = center)

    // Main fluid core orb
    val coreBrush = Brush.radialGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.95f),
            primaryColor,
            primaryColor.copy(alpha = 0.85f),
        ),
        center = center - Offset(orbRadius * 0.25f, orbRadius * 0.25f),
        radius = orbRadius * 1.1f,
    )
    drawCircle(brush = coreBrush, radius = orbRadius, center = center)
}

/** Draws the hypnotic thinking orb with dual revolving gradient nodes. */
private fun DrawScope.drawThinkingOrb(
    center: Offset,
    baseRadius: Float,
    rotationRad: Float,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
) {
    val orbRadius = baseRadius * 1.05f

    // Outer revolving nebula glow
    val nebulaRadius = orbRadius * 1.4f
    val nebulaBrush = Brush.radialGradient(
        colors = listOf(
            secondaryColor.copy(alpha = 0.38f),
            tertiaryColor.copy(alpha = 0.2f),
            Color.Transparent,
        ),
        center = center,
        radius = nebulaRadius,
    )
    drawCircle(brush = nebulaBrush, radius = nebulaRadius, center = center)

    // Swirling multi-color gradient core
    val offsetDistance = orbRadius * 0.35f
    val offsetX = (cos(rotationRad) * offsetDistance)
    val offsetY = (sin(rotationRad) * offsetDistance)
    val swirlBrush = Brush.radialGradient(
        colors = listOf(
            tertiaryColor,
            secondaryColor,
            primaryColor,
        ),
        center = center + Offset(offsetX, offsetY),
        radius = orbRadius * 1.15f,
    )
    drawCircle(brush = swirlBrush, radius = orbRadius, center = center)
}

/** Draws the undulating harmonic speaking orb with rhythmic sound ripples. */
private fun DrawScope.drawSpeakingOrb(
    center: Offset,
    baseRadius: Float,
    wavePhase: Float,
    primaryColor: Color,
    secondaryColor: Color,
) {
    // Dynamic harmonic wave variation
    val harmonicModulation = (sin(wavePhase) * 0.08f) + (cos(wavePhase * 2.3f) * 0.05f)
    val orbRadius = baseRadius * (1.12f + harmonicModulation)

    // Two rhythmic wave rings expanding outward
    val ring1Expansion = (sin(wavePhase) + 1f) * 0.5f
    val ring1Radius = orbRadius + (size.minDimension * 0.18f * ring1Expansion)
    drawCircle(
        color = secondaryColor.copy(alpha = (1f - ring1Expansion) * 0.3f),
        radius = ring1Radius,
        center = center,
    )

    val ring2Expansion = (cos(wavePhase + PI.toFloat() / 2f) + 1f) * 0.5f
    val ring2Radius = orbRadius + (size.minDimension * 0.26f * ring2Expansion)
    drawCircle(
        color = primaryColor.copy(alpha = (1f - ring2Expansion) * 0.2f),
        radius = ring2Radius,
        center = center,
    )

    // Vivid vocal core
    val vocalBrush = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.9f),
            secondaryColor,
            primaryColor,
        ),
        center = center - Offset(orbRadius * 0.2f, orbRadius * 0.2f),
        radius = orbRadius * 1.2f,
    )
    drawCircle(brush = vocalBrush, radius = orbRadius, center = center)
}

/** Draws the calm resting paused orb. */
private fun DrawScope.drawPausedOrb(
    center: Offset,
    baseRadius: Float,
    breathing: Float,
    surfaceVariant: Color,
    primaryColor: Color,
) {
    val orbRadius = baseRadius * 0.95f * breathing
    val haloRadius = orbRadius * 1.25f

    // Soft quiet halo
    drawCircle(
        color = primaryColor.copy(alpha = 0.08f),
        radius = haloRadius,
        center = center,
    )

    // Subtle brushed core
    val pausedBrush = Brush.radialGradient(
        colors = listOf(
            surfaceVariant.copy(alpha = 0.9f),
            surfaceVariant,
            primaryColor.copy(alpha = 0.25f),
        ),
        center = center,
        radius = orbRadius,
    )
    drawCircle(brush = pausedBrush, radius = orbRadius, center = center)
}
