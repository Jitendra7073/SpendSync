package com.example.spendsync.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Iconify (https://iconify.design) — a free, keyless, public icon API that
 * aggregates 200,000+ icons from 150+ open-source icon sets. Used to power
 * the category icon picker so users can search for and attach an icon to any
 * custom category, instead of being limited to a hand-picked local set.
 */
data class IconifySearchResponse(
    val icons: List<String>, // "prefix:name", e.g. "mdi:pizza"
    val total: Int,
)

interface IconifyApiService {
    @GET("search")
    suspend fun searchIcons(
        @Query("query") query: String,
        @Query("limit") limit: Int = 64,
    ): IconifySearchResponse
}
