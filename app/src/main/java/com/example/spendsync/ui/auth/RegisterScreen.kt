package com.example.spendsync.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AuthTextField
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.settings.cascadeIn

/** The sign-up form, inside [AuthShell]. */
@Composable
fun RegisterContent(viewModel: AuthViewModel) {
    val scheme = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Column {
        AuthPageHeader(tr(R.string.create_account))
        AuthTextField(
            modifier = Modifier.cascadeIn(0),
            value = uiState.name,
            onValueChange = viewModel::onNameChanged,
            label = tr(R.string.full_name),
            leadingIcon = Icons.Default.Person,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            enabled = !uiState.isLoading,
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            modifier = Modifier.cascadeIn(1),
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = tr(R.string.email_address),
            leadingIcon = Icons.Default.Email,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            enabled = !uiState.isLoading,
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            modifier = Modifier.cascadeIn(2),
            value = uiState.password,
            onValueChange = viewModel::onPasswordChanged,
            label = tr(R.string.password),
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            passwordVisible = uiState.passwordVisible,
            trailingIcon = if (uiState.passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            onTrailingIconClick = viewModel::togglePasswordVisibility,
            trailingIconDescription = if (uiState.passwordVisible) tr(R.string.hide_password) else tr(R.string.show_password),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            enabled = !uiState.isLoading,
        )
        PasswordStrength(uiState.password)
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            modifier = Modifier.cascadeIn(3),
            value = uiState.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChanged,
            label = tr(R.string.confirm_password),
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            passwordVisible = uiState.confirmPasswordVisible,
            trailingIcon = if (uiState.confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            onTrailingIconClick = viewModel::toggleConfirmPasswordVisibility,
            trailingIconDescription = if (uiState.confirmPasswordVisible) tr(R.string.hide_confirm_password) else tr(R.string.show_confirm_password),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); viewModel.signUp() }),
            enabled = !uiState.isLoading,
        )
        Spacer(Modifier.height(24.dp))
        AppButton(
            text = tr(R.string.create_account_2),
            onClick = { focusManager.clearFocus(); viewModel.signUp() },
            size = ButtonSize.Large,
            loading = uiState.isLoading,
            enabled = !uiState.isLoading,
            fullWidth = true,
        )
    }
}
