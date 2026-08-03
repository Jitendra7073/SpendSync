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
class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == 401) {
            @Suppress("OPT_IN_USAGE")
            GlobalScope.launch(Dispatchers.IO) {
                AuthEvents.unauthorizedFlow.emit(Unit)
            }
        }

        return response
    }
}
