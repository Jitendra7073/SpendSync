package com.example.spendsync.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.spendsync.ui.components.ToastHost
import com.example.spendsync.ui.components.ToastMessage

/**
 * Everything before sign-in as ONE screen: log in, sign up and forgot password are pages inside a single
 * [AuthShell], so switching between them never slides the whole design out and in again. This is also the
 * only place that listens to the view model's events and shows toasts.
 */
@Composable
fun AuthFlowScreen(
    viewModel: AuthViewModel,
    onNavigateToHome: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(AuthPage.Login) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AuthEvent.ShowToast -> toast = ToastMessage(event.message, event.isError)
                is AuthEvent.NavigateToHome -> onNavigateToHome()
                is AuthEvent.NavigateToRegister -> page = AuthPage.Register
                is AuthEvent.NavigateToLogin -> page = AuthPage.Login
                is AuthEvent.NavigateToForgot -> { viewModel.backToResetEmail(); page = AuthPage.Forgot }
            }
        }
    }

    // Back (arrow or the phone's back gesture) steps back inside the flow before it would leave the screen.
    fun goBack() {
        when (page) {
            AuthPage.Login -> Unit
            AuthPage.Register -> page = AuthPage.Login
            AuthPage.Forgot -> when (uiState.resetStep) {
                ResetStep.Email -> page = AuthPage.Login
                ResetStep.Code -> viewModel.backToResetEmail()
                ResetStep.Password -> viewModel.backToCodeStep()
            }
        }
    }
    BackHandler(enabled = page != AuthPage.Login) { goBack() }

    ToastHost(toast = toast, onDismiss = { toast = null }) {
        AuthShell(
            page = page,
            onPageChange = { next -> if (next == AuthPage.Forgot) viewModel.backToResetEmail(); page = next },
            onBack = if (page == AuthPage.Forgot) ({ goBack() }) else null,
        ) { current ->
            when (current) {
                AuthPage.Login -> LoginContent(viewModel)
                AuthPage.Register -> RegisterContent(viewModel, onOverview = onNavigateToHome)
                AuthPage.Forgot -> ForgotContent(viewModel, onBackToLogin = { page = AuthPage.Login })
            }
        }
    }
}
