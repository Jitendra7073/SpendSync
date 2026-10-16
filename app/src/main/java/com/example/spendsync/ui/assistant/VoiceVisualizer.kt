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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.spendsync.ui.theme.LocalMotion
import com.example.spendsync.ui.theme.MotionKind
import com.example.spendsync.voice.VoicePhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Celestial iridescent cloud palette inspired by ChatGPT Voice Assistant
private val CloudCyan = Color(0xFF00F2FE)
private val CloudAzure = Color(0xFF4FACFE)
private val CloudCobalt = Color(0xFF0072FF)
private val CloudViolet = Color(0xFF7F00FF)
private val CloudMagenta = Color(0xFFA18CD1)
private val CloudWhiteGlow = Color(0xFFFFFFFF)
private val CloudSoftMist = Color(0xFFE0F7FA)

/**
 * ChatGPT-style real-time organic fluid cloud visualizer.
 *
 * Renders an ethereal, continuously morphing multi-layered fluid cloud (nebula orb)
 * that breathes, undulates, and ripples dynamically:
 * - [VoicePhase.Listening]: Billows and surges organically in real-time response to speech volume levels,
 *   radiating delicate acoustic shockwave rings into space.
 * - [VoicePhase.Thinking]: Swirls into a mesmerizing celestial vortex with dual counter-rotating nebula layers.
 * - [VoicePhase.Speaking]: Vibrates and ripples with energetic harmonic waveforms simulating live vocal synthesis.
 * - [VoicePhase.Paused]: Drifts calmly in an ambient celestial mist resting state.
 *
 * Respects [LocalMotion] to gracefully fall back to static high-contrast rendering when motion is disabled.
 */
@Composable
fun VoiceVisualizer(
    phase: VoicePhase,
    audioLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualizerSize: Dp = 240.dp,
) {
    val motion = LocalMotion.current
    val animate = motion.enabled(MotionKind.Transitions)

    // Smooth audio reactivity with spring physics for natural fluid expansion
    val targetAudioScale = if (phase == VoicePhase.Listening) {
        1f + (audioLevel.coerceIn(0f, 1f) * 0.85f)
    } else {
        1f
    }
    val reactiveAudioScale by animateFloatAsState(
        targetValue = if (animate) targetAudioScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "cloud_audio_scale",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "cloud_motion_transition")

    // Slow organic fluid morphing phase
    val cloudMorphPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cloud_morph_1",
    )

    // Counter-drifting secondary fluid phase
    val cloudMorphPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cloud_morph_2",
    )

    // Ambient breathing cycle
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cloud_breathing",
    )

    // Thinking rotation angle
    val thinkingSpin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cloud_thinking_spin",
    )

    // Speaking vocal acoustic harmonic pulse
    val speakingHarmonic by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cloud_speaking_harmonic",
    )

    // Acoustic shockwave ripple progress
    val shockwaveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cloud_shockwave",
    )

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
                        rotationZ = thinkingSpin
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.28f

            when (phase) {
                VoicePhase.Listening -> {
                    drawListeningCloud(
                        center = center,
                        baseRadius = baseRadius,
                        scale = if (animate) reactiveAudioScale * breathingScale else 1f,
                        audioLevel = audioLevel,
                        phase1 = if (animate) cloudMorphPhase1 else 0f,
                        phase2 = if (animate) cloudMorphPhase2 else 0f,
                        rippleProgress = if (animate) shockwaveProgress else 0.5f,
                    )
                }
                VoicePhase.Thinking -> {
                    drawThinkingCloud(
                        center = center,
                        baseRadius = baseRadius,
                        phase1 = if (animate) cloudMorphPhase1 else 0f,
                        phase2 = if (animate) cloudMorphPhase2 else 0f,
                        breathing = if (animate) breathingScale else 1f,
                    )
                }
                VoicePhase.Speaking -> {
                    drawSpeakingCloud(
                        center = center,
                        baseRadius = baseRadius,
                        phase1 = if (animate) cloudMorphPhase1 else 0f,
                        speakingWave = if (animate) speakingHarmonic else 0f,
                        rippleProgress = if (animate) shockwaveProgress else 0.5f,
                    )
                }
                VoicePhase.Paused, VoicePhase.Off -> {
                    drawPausedCloud(
                        center = center,
                        baseRadius = baseRadius,
                        phase1 = if (animate) cloudMorphPhase1 * 0.5f else 0f,
                        breathing = if (animate) breathingScale else 1f,
                    )
                }
            }
        }
    }
}

/**
 * Builds an organic, silky-smooth closed fluid cloud contour path using harmonic Fourier perturbation.
 */
private fun buildOrganicCloudPath(
    center: Offset,
    radius: Float,
    timePhase1: Float,
    timePhase2: Float,
    wobbleAmount: Float,
    audioEnergy: Float = 0f,
): Path {
    val path = Path()
    val numPoints = 28
    val angleStep = (2 * PI / numPoints).toFloat()
    val points = ArrayList<Offset>(numPoints)

    for (i in 0 until numPoints) {
        val angle = i * angleStep
        val h1 = sin(angle * 2f + timePhase1) * 0.12f
        val h2 = cos(angle * 3f - timePhase2) * 0.08f
        val h3 = sin(angle * 5f + timePhase1 * 1.4f) * 0.04f
        val audioDeform = audioEnergy * sin(angle * 2f + timePhase1) * 0.25f

        val totalRadius = radius * (1f + (h1 + h2 + h3) * wobbleAmount + audioDeform)
        val x = center.x + totalRadius * cos(angle)
        val y = center.y + totalRadius * sin(angle)
        points.add(Offset(x, y))
    }

    val firstMid = Offset(
        (points[0].x + points[numPoints - 1].x) / 2f,
        (points[0].y + points[numPoints - 1].y) / 2f,
    )
    path.moveTo(firstMid.x, firstMid.y)

    for (i in 0 until numPoints) {
        val current = points[i]
        val next = points[(i + 1) % numPoints]
        val mid = Offset((current.x + next.x) / 2f, (current.y + next.y) / 2f)
        path.quadraticTo(current.x, current.y, mid.x, mid.y)
    }
    path.close()
    return path
}

/** Draws the reactive listening cloud that expands and radiates sound ripples when speaking. */
private fun DrawScope.drawListeningCloud(
    center: Offset,
    baseRadius: Float,
    scale: Float,
    audioLevel: Float,
    phase1: Float,
    phase2: Float,
    rippleProgress: Float,
) {
    val cloudRadius = (baseRadius * scale).coerceAtMost(size.minDimension * 0.44f)

    // 1. Acoustic shockwave ripples expanding into deep space
    val maxRippleRadius = size.minDimension * 0.48f
    val rippleRadius = cloudRadius + (maxRippleRadius - cloudRadius) * rippleProgress
    val rippleAlpha = ((1f - rippleProgress) * (0.35f + audioLevel * 0.5f)).coerceIn(0f, 0.7f)
    drawCircle(
        color = CloudCyan.copy(alpha = rippleAlpha),
        radius = rippleRadius,
        center = center,
        style = Stroke(width = 2.dp.toPx() * (1f - rippleProgress * 0.5f)),
    )

    // 2. Outer ethereal atmospheric mist
    val mistRadius = cloudRadius * 1.55f
    val mistBrush = Brush.radialGradient(
        colors = listOf(
            CloudCyan.copy(alpha = 0.38f + audioLevel * 0.35f),
            CloudViolet.copy(alpha = 0.18f + audioLevel * 0.2f),
            Color.Transparent,
        ),
        center = center,
        radius = mistRadius,
    )
    drawCircle(brush = mistBrush, radius = mistRadius, center = center)

    // 3. Outer organic fluid cloud body
    val outerCloudPath = buildOrganicCloudPath(
        center = center,
        radius = cloudRadius * 1.15f,
        timePhase1 = phase1,
        timePhase2 = phase2,
        wobbleAmount = 1f,
        audioEnergy = audioLevel,
    )
    val outerCloudBrush = Brush.radialGradient(
        colors = listOf(
            CloudAzure.copy(alpha = 0.85f),
            CloudCobalt.copy(alpha = 0.75f),
            CloudViolet.copy(alpha = 0.45f),
        ),
        center = center - Offset(cloudRadius * 0.2f, cloudRadius * 0.2f),
        radius = cloudRadius * 1.25f,
    )
    drawPath(path = outerCloudPath, brush = outerCloudBrush)

    // 4. Mid volumetric cloud body with counter-morphing fluid wave
    val midCloudPath = buildOrganicCloudPath(
        center = center,
        radius = cloudRadius * 0.88f,
        timePhase1 = -phase2 * 1.2f,
        timePhase2 = phase1 * 0.9f,
        wobbleAmount = 1.15f,
        audioEnergy = audioLevel * 0.8f,
    )
    val midCloudBrush = Brush.radialGradient(
        colors = listOf(
            CloudWhiteGlow.copy(alpha = 0.9f),
            CloudCyan.copy(alpha = 0.95f),
            CloudCobalt.copy(alpha = 0.8f),
        ),
        center = center + Offset(cloudRadius * 0.15f, cloudRadius * 0.15f),
        radius = cloudRadius * 0.95f,
    )
    drawPath(path = midCloudPath, brush = midCloudBrush)

    // 5. Luminous iridescent white-cyan inner core
    val coreRadius = cloudRadius * 0.48f
    val coreBrush = Brush.radialGradient(
        colors = listOf(
            CloudWhiteGlow,
            CloudSoftMist.copy(alpha = 0.9f),
            CloudCyan.copy(alpha = 0.6f),
            Color.Transparent,
        ),
        center = center,
        radius = coreRadius,
    )
    drawCircle(brush = coreBrush, radius = coreRadius, center = center)
}

/** Draws the hypnotic thinking celestial nebula cloud in swirling motion. */
private fun DrawScope.drawThinkingCloud(
    center: Offset,
    baseRadius: Float,
    phase1: Float,
    phase2: Float,
    breathing: Float,
) {
    val cloudRadius = baseRadius * 1.05f * breathing

    // 1. Outer celestial aura
    val nebulaRadius = cloudRadius * 1.6f
    val nebulaBrush = Brush.radialGradient(
        colors = listOf(
            CloudViolet.copy(alpha = 0.45f),
            CloudAzure.copy(alpha = 0.25f),
            Color.Transparent,
        ),
        center = center,
        radius = nebulaRadius,
    )
    drawCircle(brush = nebulaBrush, radius = nebulaRadius, center = center)

    // 2. Swirling vortex outer cloud
    val vortexPath1 = buildOrganicCloudPath(
        center = center,
        radius = cloudRadius * 1.12f,
        timePhase1 = phase1 * 1.8f,
        timePhase2 = phase2 * 1.4f,
        wobbleAmount = 1.35f,
    )
    val vortexBrush1 = Brush.radialGradient(
        colors = listOf(
            CloudCyan.copy(alpha = 0.85f),
            CloudViolet.copy(alpha = 0.8f),
            CloudCobalt.copy(alpha = 0.6f),
        ),
        center = center - Offset(cloudRadius * 0.25f, cloudRadius * 0.25f),
        radius = cloudRadius * 1.25f,
    )
    drawPath(path = vortexPath1, brush = vortexBrush1)

    // 3. Counter-swirling inner cloud node
    val vortexPath2 = buildOrganicCloudPath(
        center = center,
        radius = cloudRadius * 0.8f,
        timePhase1 = -phase2 * 2f,
        timePhase2 = -phase1 * 1.6f,
        wobbleAmount = 1.4f,
    )
    val vortexBrush2 = Brush.radialGradient(
        colors = listOf(
            CloudWhiteGlow.copy(alpha = 0.95f),
            CloudMagenta.copy(alpha = 0.85f),
            CloudCyan.copy(alpha = 0.7f),
        ),
        center = center + Offset(cloudRadius * 0.2f, cloudRadius * 0.2f),
        radius = cloudRadius * 0.9f,
    )
    drawPath(path = vortexPath2, brush = vortexBrush2)

    // 4. Concentrated luminous core
    val coreRadius = cloudRadius * 0.38f
    val coreBrush = Brush.radialGradient(
        colors = listOf(
            CloudWhiteGlow,
            CloudCyan.copy(alpha = 0.7f),
            Color.Transparent,
        ),
        center = center,
        radius = coreRadius,
    )
    drawCircle(brush = coreBrush, radius = coreRadius, center = center)
}

/** Draws the undulating harmonic speaking cloud rippling with vocal energy. */
private fun DrawScope.drawSpeakingCloud(
    center: Offset,
    baseRadius: Float,
    phase1: Float,
    speakingWave: Float,
    rippleProgress: Float,
) {
    val harmonicModulation = (sin(speakingWave) * 0.09f) + (cos(speakingWave * 2.2f) * 0.05f)
    val cloudRadius = baseRadius * (1.14f + harmonicModulation)

    // 1. Two radiant acoustic wave rings rippling outward
    val ring1Expansion = (sin(speakingWave) + 1f) * 0.5f
    val ring1Radius = cloudRadius + (size.minDimension * 0.22f * ring1Expansion)
    drawCircle(
        color = CloudCyan.copy(alpha = (1f - ring1Expansion) * 0.45f),
        radius = ring1Radius,
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )

    val ring2Expansion = (cos(speakingWave + PI.toFloat() / 2f) + 1f) * 0.5f
    val ring2Radius = cloudRadius + (size.minDimension * 0.32f * ring2Expansion)
    drawCircle(
        color = CloudAzure.copy(alpha = (1f - ring2Expansion) * 0.35f),
        radius = ring2Radius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )

    // 2. Outer vocal cloud mist
    val mistRadius = cloudRadius * 1.5f
    val mistBrush = Brush.radialGradient(
        colors = listOf(
            CloudCyan.copy(alpha = 0.45f),
            CloudCobalt.copy(alpha = 0.22f),
            Color.Transparent,
        ),
        center = center,
        radius = mistRadius,
    )
    drawCircle(brush = mistBrush, radius = mistRadius, center = center)

    // 3. Fluid undulating speaking cloud body
    val speakPath1 = buildOrganicCloudPath(
        center = center,
        radius = cloudRadius * 1.12f,
        timePhase1 = phase1 * 1.6f + speakingWave,
        timePhase2 = phase1 * 1.2f,
        wobbleAmount = 1.25f,
        audioEnergy = 0.4f,
    )
    val speakBrush1 = Brush.radialGradient(
        colors = listOf(
            CloudWhiteGlow.copy(alpha = 0.95f),
            CloudCyan.copy(alpha = 0.9f),
            CloudCobalt.copy(alpha = 0.75f),
            CloudViolet.copy(alpha = 0.5f),
        ),
        center = center - Offset(cloudRadius * 0.15f, cloudRadius * 0.15f),
        radius = cloudRadius * 1.2f,
    )
    drawPath(path = speakPath1, brush = speakBrush1)

    // 4. Vibrant luminous speaking core
    val coreRadius = cloudRadius * 0.52f
    val coreBrush = Brush.radialGradient(
        colors = listOf(
            CloudWhiteGlow,
            CloudSoftMist,
            CloudCyan.copy(alpha = 0.75f),
            Color.Transparent,
        ),
        center = center,
        radius = coreRadius,
    )
    drawCircle(brush = coreBrush, radius = coreRadius, center = center)
}

/** Draws the calm, peaceful resting cloud drifting in ambient celestial mist. */
private fun DrawScope.drawPausedCloud(
    center: Offset,
    baseRadius: Float,
    phase1: Float,
    breathing: Float,
) {
    val cloudRadius = baseRadius * 0.96f * breathing

    // 1. Soft quiet mist
    val mistRadius = cloudRadius * 1.4f
    val mistBrush = Brush.radialGradient(
        colors = listOf(
            CloudAzure.copy(alpha = 0.22f),
            Color.Transparent,
        ),
        center = center,
        radius = mistRadius,
    )
    drawCircle(brush = mistBrush, radius = mistRadius, center = center)

    // 2. Calm organic drifting cloud
    val pausedPath = buildOrganicCloudPath(
        center = center,
        radius = cloudRadius,
        timePhase1 = phase1,
        timePhase2 = -phase1 * 0.8f,
        wobbleAmount = 0.75f,
    )
    val pausedBrush = Brush.radialGradient(
        colors = listOf(
            CloudSoftMist.copy(alpha = 0.85f),
            CloudAzure.copy(alpha = 0.7f),
            CloudCobalt.copy(alpha = 0.4f),
        ),
        center = center,
        radius = cloudRadius * 1.1f,
    )
    drawPath(path = pausedPath, brush = pausedBrush)

    // 3. Resting serene core
    val coreRadius = cloudRadius * 0.42f
    val coreBrush = Brush.radialGradient(
        colors = listOf(
            CloudWhiteGlow.copy(alpha = 0.9f),
            CloudCyan.copy(alpha = 0.45f),
            Color.Transparent,
        ),
        center = center,
        radius = coreRadius,
    )
    drawCircle(brush = coreBrush, radius = coreRadius, center = center)
}
