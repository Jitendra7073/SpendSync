package com.example.spendsync.data.repository

import com.example.spendsync.data.ServerMessages
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.ApiClient
import com.example.spendsync.data.remote.model.ForgotPasswordRequest
import com.example.spendsync.data.remote.model.ResetPasswordRequest
import com.example.spendsync.data.remote.model.SignInRequest
import com.example.spendsync.data.remote.model.SignUpRequest
import com.example.spendsync.data.remote.model.VerifyCodeRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.firstOrNull

/**
 * Sealed result type used throughout the auth flow.
 */
sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
}

/**
 * Thin repository that wraps the [AuthApiService] and [SessionDataStore].
 *
 * All public functions return [AuthResult] — callers never need to handle
 * raw HTTP exceptions or parse error bodies themselves.
 */
class AuthRepository(
    private val sessionDataStore: SessionDataStore,
) {
    private val api   = ApiClient.authApi
    private val gson  = Gson()

    // ── Sign In ───────────────────────────────────────────────────────────────

    suspend fun signIn(email: String, password: String): AuthResult<String> {
        return try {
            val response = api.signIn(SignInRequest(email = email, password = password))

            if (response.isSuccessful) {
                val body = response.body()
                // The bearer plugin mirrors the session cookie into this response
                // header — that's the actual usable bearer token. Body fields are
                // a fallback for older/differently-configured Better Auth setups.
                val token = response.headers()["set-auth-token"]
                    ?: body?.session?.token
                    ?: body?.token

                if (token != null) {
                    val user = body?.user
                    sessionDataStore.saveSession(
                        token  = token,
                        userId = user?.id ?: "",
                        email  = user?.email ?: email,
                        name   = user?.name,
                        createdAt = user?.createdAt,
                    )
                    AuthResult.Success(token)
                } else {
                    // No usable bearer token — a saved placeholder here would silently
                    // break every subsequent authenticated request, so treat as failure.
                    AuthResult.Error(tr(R.string.sign_in_succeeded_but_no_session))
                }
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Sign Up ───────────────────────────────────────────────────────────────

    suspend fun signUp(name: String, email: String, password: String): AuthResult<String> {
        return try {
            val response = api.signUp(SignUpRequest(email = email, password = password, name = name))

            if (response.isSuccessful) {
                val body  = response.body()
                val token = response.headers()["set-auth-token"] ?: body?.session?.token ?: body?.token
                val user  = body?.user

                if (token != null) {
                    sessionDataStore.saveSession(
                        token  = token,
                        userId = user?.id ?: "",
                        email  = user?.email ?: email,
                        name   = user?.name ?: name,
                        createdAt = user?.createdAt,
                    )
                    AuthResult.Success(token)
                } else {
                    AuthResult.Error(tr(R.string.sign_up_succeeded_but_no_session))
                }
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Forgot password ───────────────────────────────────────────────────────

    /** Asks the server to email a reset code. Success does not prove the email has an account (on purpose). */
    suspend fun requestResetCode(email: String, language: String): AuthResult<Unit> = try {
        val response = api.forgotPassword(ForgotPasswordRequest(email = email, language = language))
        if (response.isSuccessful) AuthResult.Success(Unit) else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    suspend fun verifyResetCode(email: String, code: String): AuthResult<Unit> = try {
        val response = api.verifyResetCode(VerifyCodeRequest(email = email, code = code))
        if (response.isSuccessful) AuthResult.Success(Unit) else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String): AuthResult<Unit> = try {
        val response = api.resetPassword(ResetPasswordRequest(email = email, code = code, newPassword = newPassword))
        if (response.isSuccessful) AuthResult.Success(Unit) else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    // ── Session Check ─────────────────────────────────────────────────────────

    /**
     * Returns true if a locally persisted session exists AND the server
     * confirms it is still valid.
     */
    suspend fun isSessionValid(): Boolean {
        val localToken = sessionDataStore.sessionToken.firstOrNull()
        if (localToken.isNullOrBlank()) return false

        return try {
            val response = api.getSession("Bearer $localToken")
            response.isSuccessful && response.body()?.session != null
        } catch (e: Exception) {
            // Network error — assume the local session might still be valid
            // so we don't force-logout on flaky connections
            false
        }
    }

    /** Quick local check — no network call. */
    suspend fun hasLocalSession(): Boolean {
        return !sessionDataStore.sessionToken.firstOrNull().isNullOrBlank()
    }

    // ── Sign Out ──────────────────────────────────────────────────────────────

    suspend fun signOut(): AuthResult<Unit> {
        return try {
            val localToken = sessionDataStore.sessionToken.firstOrNull()
            if (!localToken.isNullOrBlank()) {
                api.signOut("Bearer $localToken") // best-effort — ignore result
            }
            sessionDataStore.clearSession()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            sessionDataStore.clearSession() // clear locally even if server call fails
            AuthResult.Success(Unit)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return tr(R.string.an_unexpected_error_occurred)
        return try {
            val json = gson.fromJson(errorBody, JsonObject::class.java)
            ServerMessages.localize(
                message = json.get("message")?.asString ?: json.get("error")?.takeIf { it.isJsonPrimitive }?.asString,
                code = json.get("code")?.asString,
            )
        } catch (e: Exception) {
            tr(R.string.an_unexpected_error_occurred)
        }
    }

    private fun Exception.toUserMessage(): String = when {
        message?.contains("Unable to resolve host", ignoreCase = true) == true ->
            tr(R.string.no_internet_connection_please_check_your)
        message?.contains("timeout", ignoreCase = true) == true ->
            tr(R.string.request_timed_out_please_try_again)
        message?.contains("Connection refused", ignoreCase = true) == true ->
            tr(R.string.cannot_reach_the_server_please_try)
        else -> message ?: tr(R.string.an_unexpected_error_occurred)
    }
}
