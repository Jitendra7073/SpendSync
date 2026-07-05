package com.example.spendsync.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.utils.LocalizationUtils

/**
 * Floating pill-shaped bottom navigation bar that dynamically inherits colors and language preferences.
 */
@Composable
fun SpendSyncBottomBar(
    sessionDataStore: SessionDataStore,
    currentRoute: String,
    onItemSelected: (BottomNavItem) -> Unit,
) {
    val language by sessionDataStore.language.collectAsState(initial = "English")

    val navBgColor = MaterialTheme.colorScheme.surface
    val shadowColor = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Pill container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                // Soft elevation shadow
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = shadowColor.copy(alpha = 0.08f),
                    spotColor = shadowColor.copy(alpha = 0.12f),
                )
                .clip(RoundedCornerShape(32.dp))
                .background(navBgColor)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavItem.all.forEach { item ->
                if (item.isFab) {
                    FabNavItem(onClick = { onItemSelected(item) })
                } else {
                    val labelKey = when (item.route) {
                        BottomNavItem.Home.route -> "home"
                        BottomNavItem.Analytics.route -> "analytics"
                        BottomNavItem.Budget.route -> "budget"
                        BottomNavItem.Profile.route -> "profile"
                        else -> item.label.lowercase()
                    }
                    val translatedLabel = LocalizationUtils.getTranslation(labelKey, language)

                    RegularNavItem(
                        item = item,
                        translatedLabel = translatedLabel,
                        isSelected = currentRoute == item.route,
                        onClick = { onItemSelected(item) },
                    )
                }
            }
        }
    }
}

// ── Regular item ──────────────────────────────────────────────────────────────

@Composable
private fun RegularNavItem(
    item: BottomNavItem,
    translatedLabel: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val activeColor = MaterialTheme.colorScheme.primary
    val unactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else unactiveColor,
        animationSpec = tween(220),
        label = "icon_color",
    )
    
    val indicatorColor by animateColorAsState(
        targetValue = if (isSelected) activeColor.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(220),
        label = "indicator_color",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = activeColor),
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Icon container with animated pill background when selected
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(indicatorColor)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = translatedLabel,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = translatedLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) activeColor else unactiveColor,
        )
    }
}

// ── Centre FAB ────────────────────────────────────────────────────────────────

@Composable
private fun FabNavItem(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
        label = "fab_scale",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val shadowColor = MaterialTheme.colorScheme.onBackground

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(y = (-12).dp)
            .size(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation = 8.dp, shape = CircleShape, spotColor = shadowColor.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(primaryColor) // Making FAB prominent primary accent
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = onPrimaryColor),
                onClick = onClick,
            ),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Add transaction",
            tint = onPrimaryColor,
            modifier = Modifier.size(28.dp),
        )
    }
}
