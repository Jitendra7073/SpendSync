package com.example.spendsync.ui.auth

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendsync.data.repository.AuthRepository
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.ui.i18n.LanguageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

data class AuthUiState(
    val isLoading: Boolean = false,
    val email: String      = "",
    val password: String   = "",
    val name: String       = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    // Forgot-password flow
    val resetStep: ResetStep = ResetStep.Email,
    val resetCode: String = "",
    val newPassword: String = "",
    val confirmNewPassword: String = "",
    val newPasswordVisible: Boolean = false,
    /** Seconds until "Resend code" works again (0 = ready). */
    val resendSeconds: Int = 0,
)

enum class ResetStep { Email, Code }

// ── One-shot events (toast messages, navigation) ──────────────────────────────

sealed class AuthEvent {
    data class ShowToast(val message: String, val isError: Boolean = true) : AuthEvent()
    object NavigateToHome     : AuthEvent()
    object NavigateToLogin    : AuthEvent()
    object NavigateToRegister : AuthEvent()
    object NavigateToForgot   : AuthEvent()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ── Field updates ─────────────────────────────────────────────────────────

    fun onEmailChanged(value: String)           { _uiState.value = _uiState.value.copy(email   = value.trim()) }
    fun onPasswordChanged(value: String)        { _uiState.value = _uiState.value.copy(password = value) }
    fun onNameChanged(value: String)            { _uiState.value = _uiState.value.copy(name    = value.trim()) }
    fun onConfirmPasswordChanged(value: String) { _uiState.value = _uiState.value.copy(confirmPassword = value) }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(passwordVisible = !_uiState.value.passwordVisible)
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.value = _uiState.value.copy(
            confirmPasswordVisible = !_uiState.value.confirmPasswordVisible
        )
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun signIn() {
        val state = _uiState.value
        if (!validateLoginFields(state)) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)

            when (val result = repository.signIn(state.email, state.password)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.send(AuthEvent.ShowToast(tr(R.string.welcome_back), isError = false))
                    _events.send(AuthEvent.NavigateToHome)
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.send(AuthEvent.ShowToast(result.message))
                }
            }
        }
    }

    fun signUp() {
        val state = _uiState.value
        if (!validateRegisterFields(state)) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)

            when (val result = repository.signUp(state.name, state.email, state.password)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.send(AuthEvent.ShowToast(tr(R.string.account_created_welcome_to_spendsync), isError = false))
                    _events.send(AuthEvent.NavigateToHome)
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.send(AuthEvent.ShowToast(result.message))
                }
            }
        }
    }

    fun navigateToRegister() {
        viewModelScope.launch { _events.send(AuthEvent.NavigateToRegister) }
    }

    fun navigateToForgot() {
        viewModelScope.launch { _events.send(AuthEvent.NavigateToForgot) }
    }

    // ── Forgot password ───────────────────────────────────────────────────────

    private var cooldownJob: Job? = null

    fun onResetCodeChanged(value: String) { _uiState.value = _uiState.value.copy(resetCode = normalizeResetCode(value)) }
    fun onNewPasswordChanged(value: String) { _uiState.value = _uiState.value.copy(newPassword = value) }
    fun onConfirmNewPasswordChanged(value: String) { _uiState.value = _uiState.value.copy(confirmNewPassword = value) }
    fun toggleNewPasswordVisibility() { _uiState.value = _uiState.value.copy(newPasswordVisible = !_uiState.value.newPasswordVisible) }

    /** Step 1 (and "resend"): email a code, then show the code step. */
    fun requestResetCode() {
        val state = _uiState.value
        if (state.isLoading || state.resendSeconds > 0) return
        if (state.email.isBlank()) { sendError(tr(R.string.please_enter_your_email_address)); return }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) { sendError(tr(R.string.please_enter_a_valid_email_address)); return }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = repository.requestResetCode(state.email, LanguageManager.current.storedName)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, resetStep = ResetStep.Code, resetCode = "")
                    _events.send(AuthEvent.ShowToast(tr(R.string.auth_code_sent), isError = false))
                    startResendCooldown()
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.send(AuthEvent.ShowToast(result.message))
                }
            }
        }
    }

    private fun startResendCooldown(seconds: Int = RESEND_COOLDOWN_SECONDS) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (left in seconds downTo 1) {
                _uiState.value = _uiState.value.copy(resendSeconds = left)
                delay(1000)
            }
            _uiState.value = _uiState.value.copy(resendSeconds = 0)
        }
    }

    /** Step 2: check the code and set the new password. */
    fun submitReset() {
        val state = _uiState.value
        if (state.isLoading) return
        when {
            state.resetCode.length != RESET_CODE_LENGTH -> return sendError(tr(R.string.auth_code_invalid_len))
            state.newPassword.length < 8 -> return sendError(tr(R.string.password_must_be_at_least_8))
            state.newPassword != state.confirmNewPassword -> return sendError(tr(R.string.passwords_do_not_match))
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = repository.resetPassword(state.email, state.resetCode, state.newPassword)) {
                is AuthResult.Success -> {
                    cooldownJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, resetStep = ResetStep.Email, resetCode = "", newPassword = "",
                        confirmNewPassword = "", password = "", resendSeconds = 0,
                    )
                    _events.send(AuthEvent.ShowToast(tr(R.string.auth_reset_done), isError = false))
                    _events.send(AuthEvent.NavigateToLogin)
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.send(AuthEvent.ShowToast(result.message))
                }
            }
        }
    }

    /** "Use a different email": back to step 1. */
    fun backToResetEmail() {
        cooldownJob?.cancel()
        _uiState.value = _uiState.value.copy(resetStep = ResetStep.Email, resetCode = "", newPassword = "", confirmNewPassword = "", resendSeconds = 0)
    }

    fun navigateToLogin() {
        viewModelScope.launch { _events.send(AuthEvent.NavigateToLogin) }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateLoginFields(state: AuthUiState): Boolean {
        return when {
            state.email.isBlank() -> {
                sendError(tr(R.string.please_enter_your_email_address))
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches() -> {
                sendError(tr(R.string.please_enter_a_valid_email_address))
                false
            }
            state.password.isBlank() -> {
                sendError(tr(R.string.please_enter_your_password))
                false
            }
            else -> true
        }
    }

    private fun validateRegisterFields(state: AuthUiState): Boolean {
        return when {
            state.name.isBlank() -> {
                sendError(tr(R.string.please_enter_your_full_name))
                false
            }
            state.email.isBlank() -> {
                sendError(tr(R.string.please_enter_your_email_address))
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches() -> {
                sendError(tr(R.string.please_enter_a_valid_email_address))
                false
            }
            state.password.length < 8 -> {
                sendError(tr(R.string.password_must_be_at_least_8))
                false
            }
            state.password != state.confirmPassword -> {
                sendError(tr(R.string.passwords_do_not_match))
                false
            }
            else -> true
        }
    }

    private fun sendError(message: String) {
        viewModelScope.launch { _events.send(AuthEvent.ShowToast(message)) }
    }
}
