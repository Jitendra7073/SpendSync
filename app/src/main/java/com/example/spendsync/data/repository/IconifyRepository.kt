package com.example.spendsync.data.repository

import com.example.spendsync.data.remote.IconifyApiClient

/**
 * Thin wrapper over the public Iconify search API for the category icon
 * picker. Deliberately outside [FinanceRepository] — this talks to a
 * completely different, unauthenticated host and has no auth header, cache
 * invalidation, or mutation concerns to share with it.
 */
class IconifyRepository {

    private val api = IconifyApiClient.api

    // Small in-memory cache — re-searching the same query (e.g. clearing and
    // retyping) shouldn't re-hit the network every keystroke.
    private val cache = mutableMapOf<String, List<String>>()

    suspend fun searchIcons(query: String): AuthResult<List<String>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return AuthResult.Success(emptyList())
        cache[trimmed]?.let { return AuthResult.Success(it) }
        return try {
            val response = api.searchIcons(query = trimmed, limit = 60)
            cache[trimmed] = response.icons
            AuthResult.Success(response.icons)
        } catch (e: Exception) {
            AuthResult.Error(
                when {
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                        "No internet connection. Please check your network."
                    else -> "Couldn't search icons. Please try again."
                }
            )
        }
    }
}
