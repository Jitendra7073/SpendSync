package com.example.spendsync.data.repository

import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.ApiClient
import com.example.spendsync.data.remote.model.SignInRequest
import com.example.spendsync.data.remote.model.SignUpRequest
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
                    AuthResult.Error("Sign in succeeded but no session token was returned.")
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
                    AuthResult.Error("Sign up succeeded but no session token was returned.")
                }
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
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
        if (errorBody.isNullOrBlank()) return "An unexpected error occurred."
        return try {
            val json = gson.fromJson(errorBody, JsonObject::class.java)
            json.get("message")?.asString
                ?: json.get("error")?.asString
                ?: "An unexpected error occurred."
        } catch (e: Exception) {
            "An unexpected error occurred."
        }
    }

    private fun Exception.toUserMessage(): String = when {
        message?.contains("Unable to resolve host", ignoreCase = true) == true ->
            "No internet connection. Please check your network."
        message?.contains("timeout", ignoreCase = true) == true ->
            "Request timed out. Please try again."
        message?.contains("Connection refused", ignoreCase = true) == true ->
            "Cannot reach the server. Please try again later."
        else -> message ?: "An unexpected error occurred."
    }
}
