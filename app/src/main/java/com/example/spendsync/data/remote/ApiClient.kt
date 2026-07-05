package com.example.spendsync.data.remote

import com.example.spendsync.BuildConfig
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.CookieManager
import java.net.CookiePolicy
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

    // Shared cookie jar — keeps the Better Auth session cookie alive in-memory
    val cookieJar: JavaNetCookieJar by lazy {
        val cookieManager = CookieManager().apply {
            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        }
        JavaNetCookieJar(cookieManager)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG)
            HttpLoggingInterceptor.Level.BODY
        else
            HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
