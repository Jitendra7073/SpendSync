package com.example.spendsync.ui.splash

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.BrandYellow
import com.example.spendsync.ui.theme.NeutralWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val APP_NAME              = "SpendSync"
private const val SPLASH_DURATION_MS    = 2_800L
private const val CHAR_STAGGER_MS       = 110L
private const val CHAR_ANIM_DURATION_MS = 500

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    // Collect navigation events — must use a unique key so both effects run
    LaunchedEffect("nav_events") {
        viewModel.navEvent.collectLatest { event ->
            when (event) {
                SplashNavEvent.GoHome  -> onNavigateToHome()
                SplashNavEvent.GoLogin -> onNavigateToLogin()
            }
        }
    }

    // Start the auth check — different key so this runs independently
    LaunchedEffect("check_auth") {
        viewModel.checkAuth(animationDurationMs = SPLASH_DURATION_MS)
    }

    SplashContent()
}

// ── UI ────────────────────────────────────────────────────────────────────────

@Composable
private fun SplashContent() {
    val BrandBlue = MaterialTheme.colorScheme.primary
    val progressAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progressAnim.animateTo(
            targetValue   = 1f,
            animationSpec = tween(
                durationMillis = SPLASH_DURATION_MS.toInt(),
                easing         = LinearEasing,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBlue),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier            = Modifier.fillMaxSize(),
        ) {
            Spacer(Modifier.weight(1f))
            AnimatedAppName(text = APP_NAME)
            Spacer(Modifier.height(10.dp))
            YellowAccentBar()
            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 52.dp),
            ) {
                ProgressBar(progress = progressAnim.value)
                Spacer(Modifier.height(16.dp))
                Text(
                    text       = "Welcome to SpendSync",
                    color      = NeutralWhite.copy(alpha = 0.85f),
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

// ── Character animation ───────────────────────────────────────────────────────

@Composable
private fun AnimatedAppName(text: String) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        text.forEachIndexed { index, char ->
            AnimatedCharacter(
                char       = char,
                delayMs    = index * CHAR_STAGGER_MS,
                durationMs = CHAR_ANIM_DURATION_MS,
            )
        }
    }
}

@Composable
private fun AnimatedCharacter(char: Char, delayMs: Long, durationMs: Int) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(delayMs)
        progress.animateTo(
            targetValue   = 1f,
            animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
        )
    }

    val blurRadius: Dp = (20f * (1f - progress.value)).dp

    Text(
        text       = char.toString(),
        color      = NeutralWhite.copy(alpha = 0.2f + 0.8f * progress.value),
        fontSize   = 38.sp,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier
            .graphicsLayer {
                scaleX = 0.8f + 0.2f * progress.value
                scaleY = 0.8f + 0.2f * progress.value
            }
            .blurCompat(blurRadius),
    )
}

private fun Modifier.blurCompat(radius: Dp): Modifier {
    if (radius.value <= 0f) return this
    return drawWithContent {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                asFrameworkPaint().apply {
                    isAntiAlias = true
                    maskFilter  = BlurMaskFilter(radius.toPx(), BlurMaskFilter.Blur.NORMAL)
                }
            }
            canvas.saveLayer(
                androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                paint,
            )
            drawContent()
            canvas.restore()
        }
    }
}

// ── Yellow bar ────────────────────────────────────────────────────────────────

@Composable
private fun YellowAccentBar() {
    val widthAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay((APP_NAME.length * CHAR_STAGGER_MS) + CHAR_ANIM_DURATION_MS)
        widthAnim.animateTo(
            targetValue   = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        )
    }

    Box(
        modifier = Modifier
            .width((APP_NAME.length * 22 * widthAnim.value).dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(BrandYellow),
    )
}

// ── Progress bar ──────────────────────────────────────────────────────────────

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .width(220.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(NeutralWhite.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(fraction = progress)
                .clip(RoundedCornerShape(2.dp))
                .background(NeutralWhite),
        )
    }
}
