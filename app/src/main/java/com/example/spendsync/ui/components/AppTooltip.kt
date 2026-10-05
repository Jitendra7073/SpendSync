package com.example.spendsync.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The name of an icon or button, shown in a small bubble in the app's accent colour when the user long-presses it
 * (or hovers it with a mouse). Wrap anything that has no visible label. Blank label = no tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTooltip(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (label.isBlank()) {
        content()
        return
    }
    val scheme = MaterialTheme.colorScheme
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(spacingBetweenTooltipAndAnchor = 6.dp),
        tooltip = {
            PlainTooltip(containerColor = scheme.primary, contentColor = scheme.onPrimary, shape = RoundedCornerShape(10.dp)) {
                Text(label, fontSize = 12.sp, color = scheme.onPrimary)
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
