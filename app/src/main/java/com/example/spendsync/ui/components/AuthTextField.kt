package com.example.spendsync.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.spendsync.ui.theme.BrandBlue
import com.example.spendsync.ui.theme.NeutralBlack
import com.example.spendsync.ui.theme.NeutralDark
import com.example.spendsync.ui.theme.NeutralLight
import com.example.spendsync.ui.theme.NeutralMid

/**
 * Reusable outlined text field styled to match the SpendSync auth screens.
 *
 * All text/container colors are hardcoded to dark-on-white so the field is
 * always legible on the white card regardless of system theme or dynamic color.
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
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        leadingIcon   = leadingIcon?.let {
            {
                Icon(
                    imageVector        = it,
                    contentDescription = null,
                    tint               = NeutralMid,
                )
            }
        },
        trailingIcon = trailingIcon?.let {
            {
                IconButton(onClick = { onTrailingIconClick?.invoke() }) {
                    Icon(
                        imageVector        = it,
                        contentDescription = trailingIconDescription,
                        tint               = NeutralMid,
                    )
                }
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
            focusedTextColor          = NeutralBlack,
            unfocusedTextColor        = NeutralBlack,
            disabledTextColor         = NeutralMid,
            // ── Container (transparent so the white card shows through) ───────
            focusedContainerColor     = Color.Transparent,
            unfocusedContainerColor   = Color.Transparent,
            disabledContainerColor    = Color.Transparent,
            // ── Border ────────────────────────────────────────────────────────
            focusedBorderColor        = BrandBlue,
            unfocusedBorderColor      = NeutralLight,
            disabledBorderColor       = NeutralLight.copy(alpha = 0.5f),
            // ── Label ─────────────────────────────────────────────────────────
            focusedLabelColor         = BrandBlue,
            unfocusedLabelColor       = NeutralMid,
            disabledLabelColor        = NeutralMid.copy(alpha = 0.6f),
            // ── Cursor ────────────────────────────────────────────────────────
            cursorColor               = BrandBlue,
            // ── Placeholder ───────────────────────────────────────────────────
            focusedPlaceholderColor   = NeutralMid,
            unfocusedPlaceholderColor = NeutralMid,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
