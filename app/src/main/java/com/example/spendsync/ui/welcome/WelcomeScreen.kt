package com.example.spendsync.ui.welcome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.BrandYellow
import com.example.spendsync.ui.theme.NeutralBlack
import com.example.spendsync.ui.theme.NeutralLight
import com.example.spendsync.ui.theme.NeutralMid
import com.example.spendsync.ui.theme.NeutralOffWhite
import com.example.spendsync.ui.theme.NeutralWhite

/**
 * Shown after the splash screen when there is no active session.
 *
 * Lets the user pick their path:
 *  • [onContinueAsGuest] — jump straight into the app, no account/auth.
 *  • [onSignIn]          — go to the login / create-account flow.
 */
@Composable
fun WelcomeScreen(
    onContinueAsGuest: () -> Unit,
    onSignIn: () -> Unit,
) {
    val NeutralOffWhite = MaterialTheme.colorScheme.background
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val BrandBlue = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeutralOffWhite),
    ) {
        // ── Blue header ────────────────────────────────────────────────────────
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .background(BrandBlue)
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text          = "SpendSync",
                color         = NeutralWhite,
                fontSize      = 34.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BrandYellow),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text     = "Track. Save. Sync your spending.",
                color    = NeutralWhite.copy(alpha = 0.85f),
                fontSize = 14.sp,
            )
        }

        // ── Body ───────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text       = "How would you like to start?",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = NeutralBlack,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text     = "You can always sign in later from your profile.",
                fontSize = 13.sp,
                color    = NeutralMid,
            )

            Spacer(Modifier.height(24.dp))

            // Primary path — sign in / create account
            ChoiceCard(
                icon        = Icons.AutoMirrored.Filled.Login,
                title       = "Sign In or Create Account",
                subtitle    = "Sync your data across devices and keep it safe.",
                emphasized  = true,
                onClick     = onSignIn,
            )

            Spacer(Modifier.height(16.dp))

            // Secondary path — guest
            ChoiceCard(
                icon        = Icons.Default.Explore,
                title       = "Continue as Guest",
                subtitle    = "Jump right in. No account needed.",
                emphasized  = false,
                onClick     = onContinueAsGuest,
            )
        }
    }
}

// ── Card ────────────────────────────────────────────────────────────────────────

@Composable
private fun ChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val NeutralWhite = MaterialTheme.colorScheme.surface
    val NeutralBlack = MaterialTheme.colorScheme.onBackground
    val NeutralLight = MaterialTheme.colorScheme.outlineVariant
    val NeutralMid = MaterialTheme.colorScheme.onSurfaceVariant
    val BrandBlue = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label         = "card_scale",
    )

    // Emphasized card is filled blue; the guest card is a light outlined surface.
    val containerColor = if (emphasized) BrandBlue else NeutralWhite
    val titleColor     = if (emphasized) NeutralWhite else NeutralBlack
    val subtitleColor  = if (emphasized) NeutralWhite.copy(alpha = 0.82f) else NeutralMid
    val iconTint       = if (emphasized) NeutralWhite else BrandBlue
    val iconBg         = if (emphasized) NeutralWhite.copy(alpha = 0.18f) else BrandBlue.copy(alpha = 0.10f)

    Card(
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(if (emphasized) 6.dp else 2.dp),
        modifier  = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (emphasized) Modifier
                else Modifier.border(1.dp, NeutralLight, RoundedCornerShape(20.dp)),
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
            ),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = iconTint,
                    modifier           = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = title,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = titleColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = subtitle,
                    fontSize = 13.sp,
                    color    = subtitleColor,
                )
            }
            Spacer(Modifier.size(8.dp))
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint               = if (emphasized) NeutralWhite else NeutralMid,
                modifier           = Modifier.size(20.dp),
            )
        }
    }
}
