package com.example.spendsync.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that detects HTTP 401 Unauthorized responses and
 * signals the rest of the app via [AuthEvents.unauthorizedFlow].
 *
 * The interceptor always returns the original response unchanged — it never
 * consumes the response body or throws. Emission is fire-and-forget on
 * [Dispatchers.IO] so the interceptor thread is never blocked.
 */
/**
 * A 401 on these means "wrong email or password" (or a bad code), NOT "your session expired". Treating it as an
 * expiry cleared the session and reloaded the login screen on every wrong password.
 */
internal fun isCredentialAttempt(path: String): Boolean =
    path.startsWith("/api/auth/sign-in") || path.startsWith("/api/auth/sign-up") || path.startsWith("/api/password/")

class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == 401 && !isCredentialAttempt(chain.request().url.encodedPath)) {
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) {
                AuthEvents.unauthorizedFlow.emit(Unit)
            }
        }

        return response
    }
}
