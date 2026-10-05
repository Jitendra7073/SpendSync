package com.example.spendsync.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import com.example.spendsync.R
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AuthTextField
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.i18n.tr

/**
 * Forgot password, inside [AuthShell], in three steps: (1) email, (2) the emailed code (checked on its own),
 * (3) only then the new password. [onBackToLogin] is the link at the bottom.
 */
@Composable
fun ForgotContent(viewModel: AuthViewModel, onBackToLogin: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    Column {
        AuthPageHeader(
            title = tr(when (uiState.resetStep) { ResetStep.Email -> R.string.auth_forgot_title; ResetStep.Code -> R.string.auth_code_title; ResetStep.Password -> R.string.auth_pw_title }),
            subtitle = if (uiState.resetStep == ResetStep.Code) tr(R.string.auth_code_sub, uiState.email) else null,
        )
        AnimatedContent(
            targetState = uiState.resetStep,
            transitionSpec = com.example.spendsync.ui.theme.motionSpec(com.example.spendsync.ui.theme.LocalMotion.current.enabled(com.example.spendsync.ui.theme.MotionKind.Transitions)) { (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut()) },
            label = "reset_step",
        ) { step ->
            when (step) {
                ResetStep.Email -> Column {
                    AuthTextField(
                        value = uiState.email,
                        onValueChange = viewModel::onEmailChanged,
                        label = tr(R.string.email_address),
                        leadingIcon = Icons.Default.Email,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); viewModel.requestResetCode() }),
                        enabled = !uiState.isLoading,
                    )
                    Spacer(Modifier.height(20.dp))
                    AppButton(
                        text = tr(R.string.auth_send_code),
                        onClick = { focusManager.clearFocus(); viewModel.requestResetCode() },
                        size = ButtonSize.Large,
                        loading = uiState.isLoading,
                        enabled = !uiState.isLoading,
                        fullWidth = true,
                    )
                }
                ResetStep.Code -> Column {
                    CodeField(
                        value = uiState.resetCode,
                        onValueChange = viewModel::onResetCodeChanged,
                        enabled = !uiState.isLoading,
                        onDone = { viewModel.verifyResetCode() },
                    )
                    Spacer(Modifier.height(22.dp))
                    AppButton(
                        text = tr(R.string.auth_verify_button),
                        onClick = { focusManager.clearFocus(); viewModel.verifyResetCode() },
                        size = ButtonSize.Large,
                        loading = uiState.isLoading,
                        enabled = !uiState.isLoading && uiState.resetCode.length == RESET_CODE_LENGTH,
                        fullWidth = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        AppButton(
                            text = if (uiState.resendSeconds > 0) tr(R.string.auth_resend_in, uiState.resendSeconds) else tr(R.string.auth_resend),
                            onClick = { viewModel.requestResetCode() },
                            variant = ButtonVariant.Text,
                            size = ButtonSize.Small,
                            enabled = uiState.resendSeconds == 0 && !uiState.isLoading,
                        )
                        AppButton(tr(R.string.auth_use_other_email), onClick = viewModel::backToResetEmail, variant = ButtonVariant.Text, size = ButtonSize.Small)
                    }
                }
                ResetStep.Password -> Column {
                    AuthTextField(
                        value = uiState.newPassword,
                        onValueChange = viewModel::onNewPasswordChanged,
                        label = tr(R.string.auth_new_password),
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true,
                        passwordVisible = uiState.newPasswordVisible,
                        trailingIcon = if (uiState.newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        onTrailingIconClick = viewModel::toggleNewPasswordVisibility,
                        trailingIconDescription = if (uiState.newPasswordVisible) tr(R.string.hide_password) else tr(R.string.show_password),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        enabled = !uiState.isLoading,
                    )
                    PasswordStrength(uiState.newPassword)
                    Spacer(Modifier.height(12.dp))
                    AuthTextField(
                        value = uiState.confirmNewPassword,
                        onValueChange = viewModel::onConfirmNewPasswordChanged,
                        label = tr(R.string.auth_confirm_new_password),
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true,
                        passwordVisible = uiState.newPasswordVisible,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); viewModel.submitReset() }),
                        enabled = !uiState.isLoading,
                    )
                    Spacer(Modifier.height(22.dp))
                    AppButton(
                        text = tr(R.string.auth_reset_button),
                        onClick = { focusManager.clearFocus(); viewModel.submitReset() },
                        size = ButtonSize.Large,
                        loading = uiState.isLoading,
                        enabled = !uiState.isLoading,
                        fullWidth = true,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            AppButton(tr(R.string.auth_back_to_login), onClick = onBackToLogin, variant = ButtonVariant.Text, size = ButtonSize.Small)
        }
    }
}
