package com.example.spendsync.data.remote

import com.example.spendsync.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit client.
 *
 * Better Auth uses session cookies, so we attach a persistent [CookieJar]
 * backed by the JVM [CookieManager]. The cookie is sent automatically on
 * every subsequent request (e.g. getSession, signOut).
 *
 * For a real production app you would persist cookies to DataStore/EncryptedSharedPrefs
 * and restore them on app restart. Here we keep it simple — the session token
 * is stored separately in [SessionDataStore] after a successful sign-in.
 */
object ApiClient {

    // No cookie jar on purpose: the app authenticates with the bearer token. A cookie jar kept the old
    // session cookie around, and after a sign-out or password reset it went out on the next login request
    // with no Origin header, which Better Auth rejects ("Missing or null Origin").
    private val apiOrigin: String by lazy {
        BuildConfig.API_BASE_URL.toHttpUrl().newBuilder().encodedPath("/").build().toString().trimEnd('/')
    }

    private val originInterceptor = Interceptor { chain ->
        val request = chain.request()
        chain.proceed(if (request.header("Origin") == null) request.newBuilder().header("Origin", apiOrigin).build() else request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG)
            HttpLoggingInterceptor.Level.BODY
        else
            HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(originInterceptor)
            .addInterceptor(AuthInterceptor())
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * For Server-Sent Events (the assistant). Same auth, but without the body-logging
     * interceptor — it would buffer the whole stream and defeat streaming — and with a read timeout
     * long enough for a slow first token (it only counts the gap between bytes).
     */
    val streamingClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .apply { interceptors().removeAll { it is HttpLoggingInterceptor } }
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApiService by lazy {
        retrofit.create(AuthApiService::class.java)
    }

    val appApi: AppApiService by lazy {
        retrofit.create(AppApiService::class.java)
    }
}
