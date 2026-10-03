package com.example.spendsync.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Reusable outlined text field styled to match the SpendSync auth screens.
 *
 * Colors follow the current [MaterialTheme.colorScheme] so the field stays
 * legible in both light and dark mode.
 */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    trailingIconDescription: String? = null,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary

    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        leadingIcon   = leadingIcon?.let {
            {
                Icon(
                    imageVector        = it,
                    contentDescription = null,
                    tint               = onSurfaceVariant,
                )
            }
        },
        trailingIcon = trailingIcon?.let {
            {
                com.example.spendsync.ui.components.AppIconButton(
                    icon = it,
                    contentDescription = trailingIconDescription.orEmpty(),
                    onClick = { onTrailingIconClick?.invoke() },
                    tint = onSurfaceVariant,
                )
            }
        },
        visualTransformation = if (isPassword && !passwordVisible)
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        else
            androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape           = RoundedCornerShape(12.dp),
        singleLine      = singleLine,
        enabled         = enabled,
        colors          = OutlinedTextFieldDefaults.colors(
            // ── Text ──────────────────────────────────────────────────────────
            focusedTextColor          = onSurface,
            unfocusedTextColor        = onSurface,
            disabledTextColor         = onSurfaceVariant,
            // ── Container (transparent so the card's own surface shows through) ─
            focusedContainerColor     = Color.Transparent,
            unfocusedContainerColor   = Color.Transparent,
            disabledContainerColor    = Color.Transparent,
            // ── Border ────────────────────────────────────────────────────────
            focusedBorderColor        = primary,
            unfocusedBorderColor      = outline,
            disabledBorderColor       = outline.copy(alpha = 0.5f),
            // ── Label ─────────────────────────────────────────────────────────
            focusedLabelColor         = primary,
            unfocusedLabelColor       = onSurfaceVariant,
            disabledLabelColor        = onSurfaceVariant.copy(alpha = 0.6f),
            // ── Cursor ────────────────────────────────────────────────────────
            cursorColor               = primary,
            // ── Placeholder ───────────────────────────────────────────────────
            focusedPlaceholderColor   = onSurfaceVariant,
            unfocusedPlaceholderColor = onSurfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
