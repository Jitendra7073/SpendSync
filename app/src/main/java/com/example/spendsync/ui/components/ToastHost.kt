package com.example.spendsync.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.ui.theme.NeutralWhite
import com.example.spendsync.ui.theme.SemanticError
import com.example.spendsync.ui.theme.SemanticSuccess
import kotlinx.coroutines.delay

// ── Model ─────────────────────────────────────────────────────────────────────

data class ToastMessage(
    val message: String,
    val isError: Boolean = true,
    val id: Long = System.currentTimeMillis(), // unique ID so same text re-triggers
)

// ── Host ──────────────────────────────────────────────────────────────────────

/**
 * Overlay that renders a toast at the top of the screen.
 *
 * Usage — wrap your screen content:
 * ```
 * ToastHost(toast = toastState) {
 *     ScreenContent(...)
 * }
 * ```
 */
@Composable
fun ToastHost(
    toast: ToastMessage?,
    autoDismissMs: Long = 3_000L,
    onDismiss: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<ToastMessage?>(null) }

    // Show when a new toast arrives
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            current = toast
            visible = true
            delay(autoDismissMs)
            visible = false
            delay(300) // wait for exit animation
            onDismiss()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(200)) + slideInVertically(
                initialOffsetY = { -it },
                animationSpec  = tween(300),
            ),
            exit    = fadeOut(tween(200)) + slideOutVertically(
                targetOffsetY  = { -it },
                animationSpec  = tween(300),
            ),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            current?.let { msg ->
                ToastBanner(message = msg.message, isError = msg.isError)
            }
        }
    }
}

// ── Banner ────────────────────────────────────────────────────────────────────

@Composable
private fun ToastBanner(message: String, isError: Boolean) {
    val bgColor   = if (isError) SemanticError else SemanticSuccess
    val icon      = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle
    val iconDesc  = if (isError) "Error" else "Success"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = iconDesc,
            tint               = NeutralWhite,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text       = message,
            color      = NeutralWhite,
            fontSize   = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier   = Modifier.weight(1f),
        )
    }
}
