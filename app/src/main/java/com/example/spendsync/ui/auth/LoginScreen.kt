package com.example.spendsync.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.spendsync.R
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AuthTextField
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.settings.cascadeIn

/** The log-in form. It lives inside [AuthShell]; the shell owns the backdrop, hero, panel and toasts. */
@Composable
fun LoginContent(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Column {
        AuthPageHeader(tr(R.string.welcome_back_2), tr(R.string.login_to_your_spendsync_account))
        AuthTextField(
            modifier = Modifier.cascadeIn(0),
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = tr(R.string.email_address),
            leadingIcon = Icons.Default.Email,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            enabled = !uiState.isLoading,
        )
        Spacer(Modifier.height(14.dp))
        AuthTextField(
            modifier = Modifier.cascadeIn(1),
            value = uiState.password,
            onValueChange = viewModel::onPasswordChanged,
            label = tr(R.string.password),
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            passwordVisible = uiState.passwordVisible,
            trailingIcon = if (uiState.passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            onTrailingIconClick = viewModel::togglePasswordVisibility,
            trailingIconDescription = if (uiState.passwordVisible) tr(R.string.hide_password) else tr(R.string.show_password),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); viewModel.signIn() }),
            enabled = !uiState.isLoading,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AppButton(tr(R.string.auth_forgot_link), onClick = { viewModel.navigateToForgot() }, variant = ButtonVariant.Text, size = ButtonSize.Small)
        }
        Spacer(Modifier.height(8.dp))
        AppButton(
            text = tr(R.string.log_in),
            onClick = { focusManager.clearFocus(); viewModel.signIn() },
            size = ButtonSize.Large,
            loading = uiState.isLoading,
            enabled = !uiState.isLoading,
            fullWidth = true,
        )
    }
}
