package com.example.spendsync.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.BrandYellow
import com.example.spendsync.ui.theme.NeutralBlack
import com.example.spendsync.ui.theme.NeutralWhite

/**
 * Full-width primary action button — yellow pill with:
 *  • Material ripple (built into [Button])
 *  • Press scale-down animation (95%) for tactile feedback
 *  • Loading spinner that swaps in place of the label
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Subtle scale-down on press — adds a satisfying tactile pop
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label         = "btn_scale",
    )

    Button(
        onClick             = onClick,
        enabled             = enabled && !isLoading,
        shape               = RoundedCornerShape(50.dp),
        interactionSource   = interactionSource,
        contentPadding      = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        colors              = ButtonDefaults.buttonColors(
            containerColor         = BrandYellow,
            contentColor           = NeutralBlack,
            disabledContainerColor = BrandYellow.copy(alpha = 0.55f),
            disabledContentColor   = NeutralBlack.copy(alpha = 0.55f),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation  = 4.dp,
            pressedElevation  = 1.dp,
            disabledElevation = 0.dp,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier    = Modifier.size(22.dp),
                color       = NeutralBlack,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text       = text,
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

/**
 * Ghost / outlined secondary button — transparent background, blue border.
 * Used for lower-priority actions like "Skip".
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textColor: Color = BrandBlue,
    borderColor: Color = BrandBlue,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 100),
        label         = "sec_btn_scale",
    )

    OutlinedButton(
        onClick           = onClick,
        enabled           = enabled,
        shape             = RoundedCornerShape(50.dp),
        interactionSource = interactionSource,
        contentPadding    = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        colors            = ButtonDefaults.outlinedButtonColors(
            contentColor         = textColor,
            disabledContentColor = textColor.copy(alpha = 0.4f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = if (enabled) borderColor else borderColor.copy(alpha = 0.4f),
        ),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Text(
            text       = text,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 14.sp,
        )
    }
}

/**
 * Ghost text-only button — used for inline links like "Sign Up" / "Log In".
 * Has a proper bounded ripple so the tap area is clearly felt.
 */
@Composable
fun TextLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = BrandBlue,
    fontWeight: FontWeight = FontWeight.Bold,
    fontSize: Int = 14,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.93f else 1f,
        animationSpec = tween(durationMillis = 80),
        label         = "link_scale",
    )

    Button(
        onClick           = onClick,
        interactionSource = interactionSource,
        shape             = RoundedCornerShape(8.dp),
        contentPadding    = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        colors            = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor   = color,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation  = 0.dp,
            pressedElevation  = 0.dp,
            hoveredElevation  = 0.dp,
            focusedElevation  = 0.dp,
        ),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Text(
            text       = text,
            color      = color,
            fontWeight = fontWeight,
            fontSize   = fontSize.sp,
        )
    }
}
