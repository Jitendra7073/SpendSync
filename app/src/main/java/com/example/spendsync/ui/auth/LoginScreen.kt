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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage
import com.example.spendsync.ui.i18n.tr

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgot: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.ShowToast -> toast = ToastMessage(event.message, event.isError)
                is AuthEvent.NavigateToHome -> onNavigateToHome()
                is AuthEvent.NavigateToRegister -> onNavigateToRegister()
                is AuthEvent.NavigateToForgot -> onNavigateToForgot()
                else -> Unit
            }
        }
    }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        AuthScaffold(
            title = tr(R.string.welcome_back_2),
            subtitle = tr(R.string.login_to_your_spendsync_account),
            footer = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.don_t_have_an_account), color = scheme.onSurfaceVariant, fontSize = 14.sp)
                    AppButton(tr(R.string.sign_up), onClick = { viewModel.navigateToRegister() }, variant = ButtonVariant.Text, size = ButtonSize.Small)
                }
            },
        ) {
            AuthTextField(
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
}
