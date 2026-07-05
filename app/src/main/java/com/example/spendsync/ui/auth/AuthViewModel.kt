package com.example.spendsync.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendsync.data.repository.AuthRepository
import com.example.spendsync.data.repository.AuthResult
import kotlinx.coroutines.channels.Channel
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
)

// ── One-shot events (toast messages, navigation) ──────────────────────────────

sealed class AuthEvent {
    data class ShowToast(val message: String, val isError: Boolean = true) : AuthEvent()
    object NavigateToHome     : AuthEvent()
    object NavigateToLogin    : AuthEvent()
    object NavigateToRegister : AuthEvent()
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
                    _events.send(AuthEvent.ShowToast("Welcome back!", isError = false))
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
                    _events.send(AuthEvent.ShowToast("Account created! Welcome to SpendSync.", isError = false))
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

    fun navigateToLogin() {
        viewModelScope.launch { _events.send(AuthEvent.NavigateToLogin) }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateLoginFields(state: AuthUiState): Boolean {
        return when {
            state.email.isBlank() -> {
                sendError("Please enter your email address.")
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches() -> {
                sendError("Please enter a valid email address.")
                false
            }
            state.password.isBlank() -> {
                sendError("Please enter your password.")
                false
            }
            else -> true
        }
    }

    private fun validateRegisterFields(state: AuthUiState): Boolean {
        return when {
            state.name.isBlank() -> {
                sendError("Please enter your full name.")
                false
            }
            state.email.isBlank() -> {
                sendError("Please enter your email address.")
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches() -> {
                sendError("Please enter a valid email address.")
                false
            }
            state.password.length < 8 -> {
                sendError("Password must be at least 8 characters.")
                false
            }
            state.password != state.confirmPassword -> {
                sendError("Passwords do not match.")
                false
            }
            else -> true
        }
    }

    private fun sendError(message: String) {
        viewModelScope.launch { _events.send(AuthEvent.ShowToast(message)) }
    }
}
