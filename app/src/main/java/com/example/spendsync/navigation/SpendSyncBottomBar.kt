package com.example.spendsync.navigation

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.example.spendsync.R
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.i18n.tr

private val BarHeight = 64.dp

/**
 * Floating bottom navigation: one rounded pill with four icons (Home, Analytics, Profile, Assistant) and a
 * round "+" beside it for adding a transaction. The current page gets a capsule that slides between
 * icons; the assistant opens the chat instead of switching pages, so it is never "selected".
 * Icons only: every item has a spoken label for screen readers.
 *
 * The host hides this bar on full-screen overlays and while the keyboard is open.
 */
@Composable
fun SpendSyncBottomBar(
    @Suppress("UNUSED_PARAMETER") sessionDataStore: SessionDataStore,
    currentRoute: String,
    onItemSelected: (BottomNavItem) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Home | Analytics | + | Planify | Settings sit in one pill; the assistant is its own button beside it.
    val assistant = BottomNavItem.Assistant
    val pillItems = BottomNavItem.all.filter { it !== assistant }

    // A soft fade behind the controls so scrolling content never collides with them visually.
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavPill(Modifier.weight(1f), pillItems, currentRoute, onItemSelected)
            AssistantButton(onClick = { onItemSelected(assistant) })
        }
    }
}

@Composable
private fun Modifier.floatingSurface(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val scheme = MaterialTheme.colorScheme
    return this
        .shadow(
            elevation = 14.dp,
            shape = shape,
            ambientColor = scheme.onBackground.copy(alpha = 0.10f),
            spotColor = scheme.onBackground.copy(alpha = 0.16f),
        )
        .clip(shape)
        .background(scheme.surface.copy(alpha = 0.96f))
        .border(BorderStroke(0.5.dp, scheme.outlineVariant.copy(alpha = 0.7f)), shape)
}

@Composable
private fun NavPill(
    modifier: Modifier,
    items: List<BottomNavItem>,
    currentRoute: String,
    onItemSelected: (BottomNavItem) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(BarHeight / 2)
    BoxWithConstraints(modifier.height(BarHeight).floatingSurface(shape)) {
        val slot = maxWidth / items.size
        val index = items.indexOfFirst { it.selectable && it.route == currentRoute }

        // The highlight capsule: slides to the current page, fades out when the page has no tab (Budget).
        val offset by animateDpAsState(slot * index.coerceAtLeast(0), spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "pill_offset")
        val alpha by animateFloatAsState(if (index >= 0) 1f else 0f, tween(180), label = "pill_alpha")
        Box(
            Modifier
                .offset(x = offset)
                .width(slot)
                .fillMaxHeight()
                .padding(6.dp)
                .graphicsLayer { this.alpha = alpha }
                .clip(RoundedCornerShape(50))
                .background(scheme.primary.copy(alpha = 0.16f)),
        )

        Row(Modifier.fillMaxSize()) {
            items.forEach { item ->
                // The weight sits on a plain Box: a tooltip wrapper must not decide the slot's width.
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    NavSlot(Modifier.fillMaxSize(), item, selected = item.selectable && item.route == currentRoute) { onItemSelected(item) }
                }
            }
        }
    }
}

@Composable
private fun NavSlot(modifier: Modifier, item: BottomNavItem, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val isAssistant = !item.selectable && !item.isFab
    if (item.isFab) { AddSlot(modifier, item, onClick); return }

    val tint by animateColorAsState(
        when {
            selected || isAssistant -> scheme.primary // the assistant always wears the accent so it reads as "AI"
            else -> scheme.onSurfaceVariant
        },
        tween(200), label = "slot_tint",
    )
    val wash by animateColorAsState(
        when {
            pressed -> scheme.onSurface.copy(alpha = 0.10f)
            hovered && !selected -> scheme.onSurface.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        tween(150), label = "slot_wash",
    )
    val scale by animateFloatAsState(
        when {
            pressed -> 0.86f
            selected -> 1.1f
            else -> 1f
        },
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "slot_scale",
    )

    com.example.spendsync.ui.components.AppTooltip(item.label, modifier) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(6.dp)
            .clip(RoundedCornerShape(50))
            .background(wash)
            .hoverable(interaction)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = ripple(bounded = true, color = scheme.primary),
                role = if (isAssistant) Role.Button else Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(26.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
    }
}

/** The "+" in the middle of the pill: a filled circle, so recording a transaction is the obvious main action. */
@Composable
private fun AddSlot(modifier: Modifier, item: BottomNavItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "add_scale")
    com.example.spendsync.ui.components.AppTooltip(tr(R.string.add_transaction), modifier) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(BarHeight - 14.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(scheme.primary)
                .selectable(selected = false, interactionSource = interaction, indication = ripple(bounded = true, color = scheme.onPrimary), role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = tr(R.string.add_transaction), tint = scheme.onPrimary, modifier = Modifier.size(28.dp))
        }
    }
    }
}

/** The assistant, separate from the pill: always in the accent colour so it reads as "AI". */
@Composable
private fun AssistantButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        when { pressed -> 0.9f; hovered -> 1.06f; else -> 1f },
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "assistant_scale",
    )
    com.example.spendsync.ui.components.AppTooltip(BottomNavItem.Assistant.label) {
    Box(
        Modifier
            .size(BarHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .floatingSurface(CircleShape)
            .hoverable(interaction)
            .selectable(selected = false, interactionSource = interaction, indication = ripple(bounded = true, color = scheme.primary), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BottomNavItem.Assistant.icon, contentDescription = BottomNavItem.Assistant.label, tint = scheme.primary, modifier = Modifier.size(28.dp))
    }
    }
}
