package com.example.spendsync.data.remote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Separate Retrofit instance for the public Iconify API — a different host
 * from the SpendSync backend, unauthenticated, no cookies needed.
 */
object IconifyApiClient {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.iconify.design/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: IconifyApiService by lazy {
        retrofit.create(IconifyApiService::class.java)
    }

    /** Direct-render URL Coil loads the SVG from — no Retrofit call needed. */
    fun iconUrl(iconId: String, colorHex: String? = null): String {
        val (prefix, name) = iconId.split(":", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else "mdi" to it[0]
        }
        val color = colorHex?.let { "?color=" + it.replace("#", "%23") } ?: ""
        return "https://api.iconify.design/$prefix/$name.svg$color"
    }
}
