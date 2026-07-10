package com.example.spendsync.data.repository

import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.ApiClient
import com.example.spendsync.data.remote.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.firstOrNull

/**
 * Handles communication with transaction, budget, and analytics endpoints.
 * Automatically injects the stored Better Auth token into requests.
 */
class FinanceRepository(
    private val sessionDataStore: SessionDataStore,
) {
    private val api = ApiClient.appApi
    private val gson = Gson()

    // Read-through cache for GET endpoints, keyed by endpoint+params. Cleared
    // per-prefix whenever a mutation could have changed that data, so screens
    // revisiting the same query (e.g. switching tabs back to Home) don't
    // re-hit the network unless something actually changed.
    private val cache = mutableMapOf<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> cached(key: String): T? = cache[key] as? T

    private fun cacheInvalidate(vararg prefixes: String) {
        cache.keys.removeAll { key -> prefixes.any { key.startsWith(it) } }
    }

    /** Call on sign-out so the next signed-in user never sees a stale cache. */
    fun clearCache() {
        cache.clear()
    }

    private suspend fun getAuthHeader(): String {
        val token = sessionDataStore.sessionToken.firstOrNull() ?: ""
        return if (token.isNotBlank()) "Bearer $token" else ""
    }

    // ── Dashboard Summary ─────────────────────────────────────────────────────

    suspend fun getDashboardSummary(month: String? = null, forceRefresh: Boolean = false): AuthResult<DashboardSummaryDto> {
        val key = "dashboard:$month"
        if (!forceRefresh) cached<DashboardSummaryDto>(key)?.let { return AuthResult.Success(it) }
        return try {
            val response = api.getDashboardSummary(getAuthHeader(), month)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!.data
                cache[key] = data
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    suspend fun getTransactions(
        startDate: String? = null,
        endDate: String? = null,
        category: String? = null,
        type: String? = null,
        page: Int? = 1,
        limit: Int? = 50,
        forceRefresh: Boolean = false,
    ): AuthResult<List<TransactionDto>> {
        val key = "transactions:$startDate:$endDate:$category:$type:$page:$limit"
        if (!forceRefresh) cached<List<TransactionDto>>(key)?.let { return AuthResult.Success(it) }
        return try {
            val response = api.getTransactions(getAuthHeader(), startDate, endDate, category, type, page, limit)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!.data
                cache[key] = data
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun createTransaction(
        amount: Double,
        type: String,
        merchant: String,
        category: String,
        sourceApp: String? = null,
        note: String? = null
    ): AuthResult<TransactionDto> {
        return try {
            val request = CreateTransactionRequest(amount, type, merchant, category, sourceApp, note)
            val response = api.createTransaction(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("transactions", "dashboard")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun updateTransaction(
        id: String,
        amount: Double? = null,
        type: String? = null,
        merchant: String? = null,
        category: String? = null,
        sourceApp: String? = null,
        note: String? = null
    ): AuthResult<TransactionDto> {
        return try {
            val request = UpdateTransactionRequest(amount, type, merchant, category, sourceApp, note)
            val response = api.updateTransaction(getAuthHeader(), id, request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("transactions", "dashboard")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun deleteTransaction(id: String): AuthResult<Unit> {
        return try {
            val response = api.deleteTransaction(getAuthHeader(), id)
            if (response.isSuccessful) {
                cacheInvalidate("transactions", "dashboard")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Budgets ───────────────────────────────────────────────────────────────

    suspend fun getBudgets(month: String? = null, category: String? = null, forceRefresh: Boolean = false): AuthResult<List<BudgetDto>> {
        val key = "budgets:$month:$category"
        if (!forceRefresh) cached<List<BudgetDto>>(key)?.let { return AuthResult.Success(it) }
        return try {
            val response = api.getBudgets(getAuthHeader(), month, category)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!.data
                cache[key] = data
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun createBudget(category: String, month: String, limitAmount: Double): AuthResult<BudgetDto> {
        return try {
            val request = CreateBudgetRequest(category, month, limitAmount)
            val response = api.createBudget(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("budgets", "dashboard")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun updateBudget(id: String, limitAmount: Double): AuthResult<BudgetDto> {
        return try {
            val request = UpdateBudgetRequest(limitAmount = limitAmount)
            val response = api.updateBudget(getAuthHeader(), id, request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("budgets", "dashboard")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun deleteBudget(id: String): AuthResult<Unit> {
        return try {
            val response = api.deleteBudget(getAuthHeader(), id)
            if (response.isSuccessful) {
                cacheInvalidate("budgets", "dashboard")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    suspend fun getSettings(forceRefresh: Boolean = false): AuthResult<SettingsDto> {
        val key = "settings"
        if (!forceRefresh) cached<SettingsDto>(key)?.let { return AuthResult.Success(it) }
        return try {
            val response = api.getSettings(getAuthHeader())
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!.data
                cache[key] = data
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun updateSettings(
        darkMode: Boolean? = null,
        pushNotifications: Boolean? = null,
        autoBackup: Boolean? = null,
        accentColor: String? = null,
        language: String? = null,
        currency: String? = null,
        dateFormat: String? = null,
    ): AuthResult<SettingsDto> {
        return try {
            val request = UpdateSettingsRequest(
                darkMode = darkMode,
                pushNotifications = pushNotifications,
                autoBackup = autoBackup,
                accentColor = accentColor,
                language = language,
                currency = currency,
                dateFormat = dateFormat,
            )
            val response = api.updateSettings(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!.data
                cache["settings"] = data
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Categories ────────────────────────────────────────────────────────────

    suspend fun getCategories(forceRefresh: Boolean = false): AuthResult<List<CategoryDto>> {
        val key = "categories"
        if (!forceRefresh) cached<List<CategoryDto>>(key)?.let { return AuthResult.Success(it) }
        return try {
            val response = api.getCategories(getAuthHeader())
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!.data
                cache[key] = data
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun createCategory(keyword: String, category: String): AuthResult<CategoryDto> {
        return try {
            val request = CreateCategoryRequest(keyword = keyword, category = category)
            val response = api.createCategory(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("categories")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun deleteCategory(id: String): AuthResult<Unit> {
        return try {
            val response = api.deleteCategory(getAuthHeader(), id)
            if (response.isSuccessful) {
                cacheInvalidate("categories")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun suggestCategory(merchant: String): AuthResult<CategorySuggestResponse> {
        return try {
            val response = api.suggestCategory(getAuthHeader(), CategorySuggestRequest(merchant))
            if (response.isSuccessful && response.body() != null) {
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return "An unexpected error occurred."
        return try {
            val json = gson.fromJson(errorBody, JsonObject::class.java)
            val errorObj = json.getAsJsonObject("error")
            errorObj?.get("message")?.asString
                ?: json.get("message")?.asString
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
