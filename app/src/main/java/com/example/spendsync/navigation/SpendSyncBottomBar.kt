package com.example.spendsync.navigation

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text

/**
 * Floating bottom navigation: a frosted pill with five equal slots (so labels in any language never
 * shift the layout), a selected state that grows a tinted capsule behind the icon, hover/press
 * feedback, and a raised "+" in the middle.
 *
 * The host hides this bar on full-screen overlays (assistant, holds, add expense) and while the keyboard
 * is open, so it never covers an input.
 */
@Composable
fun SpendSyncBottomBar(
    @Suppress("UNUSED_PARAMETER") sessionDataStore: SessionDataStore,
    currentRoute: String,
    onItemSelected: (BottomNavItem) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(30.dp)

    // A soft fade behind the pill so scrolling content never collides with it visually.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, scheme.background.copy(alpha = 0.92f)))),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = shape,
                    ambientColor = scheme.onBackground.copy(alpha = 0.10f),
                    spotColor = scheme.onBackground.copy(alpha = 0.16f),
                )
                .clip(shape)
                .background(scheme.surface.copy(alpha = 0.96f))
                .border(BorderStroke(0.5.dp, scheme.outlineVariant.copy(alpha = 0.7f)), shape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavItem.all.forEach { item ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (item.isFab) {
                        FabNavItem(onClick = { onItemSelected(item) })
                    } else {
                        RegularNavItem(
                            item = item,
                            isSelected = currentRoute == item.route,
                            onClick = { onItemSelected(item) },
                        )
                    }
                }
            }
        }
    }
}

// ── Regular item ──────────────────────────────────────────────────────────────

@Composable
private fun RegularNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val label = item.label

    val tint by animateColorAsState(
        if (isSelected) scheme.primary else scheme.onSurfaceVariant,
        tween(200), label = "nav_tint",
    )
    // Capsule behind the icon: grows and tints when selected, a faint wash on hover/press.
    val capsuleWidth by animateDpAsState(
        if (isSelected) 58.dp else 40.dp,
        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium), label = "nav_capsule",
    )
    val capsuleColor by animateColorAsState(
        when {
            isSelected -> scheme.primary.copy(alpha = 0.16f)
            pressed -> scheme.onSurface.copy(alpha = 0.10f)
            hovered -> scheme.onSurface.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        tween(160), label = "nav_capsule_color",
    )
    val iconScale by animateFloatAsState(
        when {
            pressed -> 0.88f
            isSelected -> 1.08f
            else -> 1f
        },
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "nav_icon_scale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(20.dp))
            .hoverable(interaction)
            .selectable(
                selected = isSelected,
                interactionSource = interaction,
                indication = ripple(bounded = true, color = scheme.primary),
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(capsuleWidth)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(capsuleColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null, // the label below names the tab; avoids reading it twice
                tint = tint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Centre "+" ────────────────────────────────────────────────────────────────

@Composable
private fun FabNavItem(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.90f
            hovered -> 1.06f
            else -> 1f
        },
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "fab_scale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(y = (-10).dp)
            .size(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(10.dp, CircleShape, spotColor = scheme.primary.copy(alpha = 0.45f))
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.primary.copy(alpha = 0.82f))))
            // A ring in the bar's own colour separates the button from the pill it overlaps.
            .border(3.dp, scheme.surface, CircleShape)
            .hoverable(interaction)
            .selectable(
                selected = false,
                interactionSource = interaction,
                indication = ripple(bounded = true, color = scheme.onPrimary),
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = tr(R.string.add_transaction),
            tint = scheme.onPrimary,
            modifier = Modifier.size(28.dp),
        )
    }
}
