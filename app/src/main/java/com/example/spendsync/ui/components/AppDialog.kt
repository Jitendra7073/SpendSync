package com.example.spendsync.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spendsync.ui.theme.SemanticWarning
import com.example.spendsync.ui.theme.incomeColor

/** Sets the colour of the dialog's icon badge: neutral/info = accent, success = green, warning = amber, danger = red. */
enum class DialogTone { Neutral, Info, Success, Warning, Danger }

/** A button at the bottom of an [AppDialog]. */
data class DialogAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val loading: Boolean = false,
)

@Composable
private fun DialogTone.color(): Color = when (this) {
    DialogTone.Neutral, DialogTone.Info -> MaterialTheme.colorScheme.primary
    DialogTone.Success -> incomeColor()
    DialogTone.Warning -> SemanticWarning
    DialogTone.Danger -> MaterialTheme.colorScheme.error
}

/**
 * The only dialog shell used across SpendSync.
 *
 * Built to behave on every device: it is capped at 420dp wide on tablets, never taller than
 * the screen (the body scrolls instead), lifts above the keyboard, and respects cut-outs and
 * gesture bars. On narrow screens or large font sizes the buttons stack full-width so labels
 * are never cut off, whatever the language. Copy should be one short sentence a first-time
 * user would understand — say what will happen, not how the app works inside.
 *
 * Structure: round icon badge → title → plain-language [message] → optional [content]
 * (inputs, pickers, lists) → up to two buttons ([secondary] quiet, [primary] bold).
 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    tone: DialogTone = DialogTone.Neutral,
    primary: DialogAction? = null,
    secondary: DialogAction? = null,
    dismissOnOutside: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }

    Dialog(
        onDismissRequest = { if (dismissOnOutside) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f * appear.value))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (dismissOnOutside) onDismiss() }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val stackButtons = maxWidth < 340.dp || LocalConfiguration.current.fontScale > 1.3f
            Column(
                modifier = modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .graphicsLayer {
                        alpha = appear.value
                        val s = 0.92f + 0.08f * appear.value
                        scaleX = s
                        scaleY = s
                        translationY = (1f - appear.value) * 24.dp.toPx()
                    }
                    .shadow(16.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(scheme.surface)
                    .border(0.5.dp, scheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                    // Taps inside the card must not reach the dismiss layer behind it.
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                    .semantics { paneTitle = title }
                    .padding(top = 24.dp, bottom = 16.dp),
            ) {
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (icon != null) {
                        val toneColor = tone.color()
                        Box(Modifier.size(56.dp).clip(CircleShape).background(toneColor.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = toneColor, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, textAlign = TextAlign.Center)
                    if (message != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(message, fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    if (content != null) {
                        Spacer(Modifier.height(16.dp))
                        Column(Modifier.fillMaxWidth(), content = content)
                    }
                }
                if (primary != null || secondary != null) {
                    Spacer(Modifier.height(20.dp))
                    DialogButtons(primary, secondary, stackButtons, tone)
                }
            }
        }
    }
}

@Composable
private fun DialogButtons(primary: DialogAction?, secondary: DialogAction?, stacked: Boolean, tone: DialogTone) {
    val primaryVariant = if (tone == DialogTone.Danger) ButtonVariant.Danger else ButtonVariant.Primary
    val secondaryVariant = ButtonVariant.Text
    if (stacked) {
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (primary != null) AppButton(primary.label, primary.onClick, Modifier.fillMaxWidth(), primaryVariant, loading = primary.loading, enabled = primary.enabled, fullWidth = true)
            if (secondary != null) AppButton(secondary.label, secondary.onClick, Modifier.fillMaxWidth(), secondaryVariant, loading = secondary.loading, enabled = secondary.enabled, fullWidth = true)
        }
    } else {
        Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (secondary != null) AppButton(secondary.label, secondary.onClick, Modifier.weight(1f), secondaryVariant, enabled = secondary.enabled, loading = secondary.loading)
            if (primary != null) AppButton(primary.label, primary.onClick, Modifier.weight(1f), primaryVariant, loading = primary.loading, enabled = primary.enabled)
        }
    }
}

/** One choice in an [AppOptionDialog]. [key] is what gets returned; [label] is what the user reads. */
data class DialogOption(val key: String, val label: String, val description: String? = null)

/**
 * "Pick one" dialog — a short list with the current choice ticked. Tapping an option selects
 * it right away, so there is no extra Save step to forget.
 */
@Composable
fun AppOptionDialog(
    title: String,
    options: List<DialogOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String,
    message: String? = null,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    AppDialog(
        onDismiss = onDismiss,
        title = title,
        message = message,
        icon = icon,
        secondary = DialogAction(cancelLabel, onDismiss),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                val selected = option.key == selectedKey
                val bg by animateColorAsState(if (selected) scheme.primary.copy(alpha = 0.12f) else Color.Transparent, tween(150), label = "opt_bg")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(option.key) })
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) scheme.primary else scheme.onSurface)
                        if (option.description != null) Text(option.description, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** Convenience for the common "are you sure?" pattern. */
@Composable
fun AppConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    loading: Boolean = false,
    icon: ImageVector? = if (destructive) Icons.Default.Warning else Icons.Default.Info,
) {
    AppDialog(
        onDismiss = onDismiss,
        title = title,
        message = message,
        icon = icon,
        tone = if (destructive) DialogTone.Danger else DialogTone.Info,
        primary = DialogAction(confirmLabel, onConfirm, loading = loading),
        secondary = DialogAction(cancelLabel, onDismiss),
    )
}

/**
 * Bottom sheet shell. Rounded top, themed surface, and capped at 560dp wide so it doesn't
 * stretch across a tablet. Content scrolls if it outgrows the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = scheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetMaxWidth = 560.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        ) {
            if (title != null) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}
