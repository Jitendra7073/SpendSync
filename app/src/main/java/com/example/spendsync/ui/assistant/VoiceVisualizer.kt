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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.spendsync.ui.theme.LocalMotion
import com.example.spendsync.ui.theme.MotionKind
import com.example.spendsync.voice.VoicePhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Iconic Gemini rainbow / chromatic spectrum gradient palette.
 * Smoothly transitions from Electric Blue -> Indigo -> Magenta -> Hot Pink -> Coral -> Sun Yellow.
 */
val GeminiSpectrum = listOf(
    Color(0xFF1A73E8), // Electric Royal Blue
    Color(0xFF2563EB), // Azure Blue
    Color(0xFF6366F1), // Indigo
    Color(0xFF8B5CF6), // Purple
    Color(0xFFC026D3), // Magenta / Fuchsia
    Color(0xFFE11D48), // Rose Red
    Color(0xFFF43F5E), // Hot Pink
    Color(0xFFF97316), // Coral Sunset Orange
    Color(0xFFFB923C), // Warm Amber Orange
    Color(0xFFFBBF24), // Golden Sun Yellow
)

/**
 * Dynamic Gemini-style real-time chromatic soundwave equalizer visualizer.
 *
 * Renders an expansive multi-color vertical waveform bar array spanning across the canvas:
 * - [VoicePhase.Listening]: Bars dance and undulate dynamically to live microphone input level ([audioLevel])
 *   with multi-harmonic acoustic Fourier components.
 * - [VoicePhase.Speaking]: Rhythmic vocal synthesis waves sweep across the frequency spectrum.
 * - [VoicePhase.Thinking]: Fluid harmonic traveling ripples traverse smoothly across the gradient.
 * - [VoicePhase.Paused]: Calm resting ambient breath.
 *
 * Supports [LocalMotion] to gracefully fall back to a serene static harmonic baseline when motion is disabled.
 */
@Composable
fun VoiceVisualizer(
    phase: VoicePhase,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    visualizerHeight: Dp = 190.dp,
    onClick: (() -> Unit)? = null,
) {
    val motion = LocalMotion.current
    val animate = motion.enabled(MotionKind.Transitions)

    // Smooth real-time microphone audio reactivity using spring physics
    val targetAudioEnergy = if (phase == VoicePhase.Listening) audioLevel.coerceIn(0f, 1f) else 0f
    val reactiveAudioLevel by animateFloatAsState(
        targetValue = if (animate) targetAudioEnergy else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "gemini_audio_energy",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "gemini_wave_motion")

    // Slow ambient wave phase progression
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gemini_wave_phase",
    )

    // Speaking vocal cadence harmonic pulse
    val speechHarmonic by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gemini_speech_harmonic",
    )

    // Thinking ripple phase
    val thinkingWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gemini_thinking_wave",
    )

    // Ambient breathing scale
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gemini_breathing",
    )

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            role = Role.Button,
            onClick = onClick,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false),
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(visualizerHeight)
            .then(clickableModifier),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerY = canvasHeight / 2f

            if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

            // Continuous horizontal chromatic gradient across the entire soundwave
            val geminiBrush = Brush.linearGradient(
                colors = GeminiSpectrum,
                start = Offset(0f, centerY),
                end = Offset(canvasWidth, centerY),
            )

            val barWidthPx = 3.8.dp.toPx()
            val barSpacingPx = 4.2.dp.toPx()
            val totalUnit = barWidthPx + barSpacingPx

            // Compute responsive bar count fitting symmetrically inside canvas with padding
            val availableWidth = (canvasWidth - 28.dp.toPx()).coerceAtLeast(totalUnit * 10f)
            val barCount = ((availableWidth + barSpacingPx) / totalUnit).toInt().coerceIn(24, 52)
            val totalWaveWidth = barCount * totalUnit - barSpacingPx
            val startX = (canvasWidth - totalWaveWidth) / 2f + barWidthPx / 2f

            val minHalfHeight = 4.dp.toPx()
            val maxHalfHeight = (canvasHeight * 0.44f).coerceAtLeast(minHalfHeight + 12f)

            for (i in 0 until barCount) {
                val x = startX + i * totalUnit
                val u = if (barCount > 1) i / (barCount - 1f) else 0.5f

                // Smooth bell/Hann envelope tapering at extremities
                val rawSine = sin(PI.toFloat() * u)
                val envelope = if (rawSine > 0f) rawSine.pow(0.60f) else 0f

                val heightFraction: Float = when (phase) {
                    VoicePhase.Listening -> {
                        if (animate) {
                            val h1 = sin(u * 11f + wavePhase * 1.6f) * 0.35f
                            val h2 = cos(u * 21f - wavePhase * 2.1f) * 0.25f
                            val h3 = sin(u * 33f + wavePhase * 3.3f) * 0.15f
                            val rawModulation = (h1 + h2 + h3 + 0.85f).coerceIn(0.2f, 1.35f)
                            val energy = 0.20f + (reactiveAudioLevel * 0.80f)
                            (energy * rawModulation * envelope).coerceIn(0.06f, 1.0f)
                        } else {
                            (0.35f * envelope).coerceIn(0.06f, 0.5f)
                        }
                    }
                    VoicePhase.Speaking -> {
                        if (animate) {
                            val speakWave = sin(u * 12f - speechHarmonic * 4.5f) * 0.40f +
                                cos(u * 20f + speechHarmonic * 2.5f) * 0.30f + 0.75f
                            (speakWave * 0.75f * envelope).coerceIn(0.08f, 1.0f)
                        } else {
                            (0.55f * envelope).coerceIn(0.08f, 0.7f)
                        }
                    }
                    VoicePhase.Thinking -> {
                        if (animate) {
                            val thinkPulse = sin(u * 8f - thinkingWave * 3.2f) * 0.5f + 0.5f
                            ((0.25f + thinkPulse * 0.70f) * envelope).coerceIn(0.06f, 1.0f)
                        } else {
                            (0.35f * envelope).coerceIn(0.06f, 0.5f)
                        }
                    }
                    VoicePhase.Paused, VoicePhase.Off -> {
                        if (animate) {
                            val breath = sin(u * 6f + wavePhase * 0.8f) * 0.2f + 0.5f
                            (breath * 0.45f * breathingScale * envelope).coerceIn(0.05f, 0.55f)
                        } else {
                            (0.20f * envelope).coerceIn(0.05f, 0.35f)
                        }
                    }
                }

                val halfH = minHalfHeight + (maxHalfHeight - minHalfHeight) * heightFraction
                val startY = centerY - halfH
                val endY = centerY + halfH

                // Pass 1: Soft diffuse glow aura behind the bar
                drawLine(
                    brush = geminiBrush,
                    start = Offset(x, startY),
                    end = Offset(x, endY),
                    strokeWidth = barWidthPx * 2.3f,
                    cap = StrokeCap.Round,
                    alpha = 0.18f,
                )

                // Pass 2: Crisp foreground rounded spectrum bar
                drawLine(
                    brush = geminiBrush,
                    start = Offset(x, startY),
                    end = Offset(x, endY),
                    strokeWidth = barWidthPx,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
