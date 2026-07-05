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

    private suspend fun getAuthHeader(): String {
        val token = sessionDataStore.sessionToken.firstOrNull() ?: ""
        return if (token.isNotBlank()) "Bearer $token" else ""
    }

    // ── Dashboard Summary ─────────────────────────────────────────────────────

    suspend fun getDashboardSummary(month: String? = null): AuthResult<DashboardSummaryDto> {
        return try {
            val response = api.getDashboardSummary(getAuthHeader(), month)
            if (response.isSuccessful && response.body() != null) {
                AuthResult.Success(response.body()!!.data)
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
        limit: Int? = 50
    ): AuthResult<List<TransactionDto>> {
        return try {
            val response = api.getTransactions(getAuthHeader(), startDate, endDate, category, type, page, limit)
            if (response.isSuccessful && response.body() != null) {
                AuthResult.Success(response.body()!!.data)
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
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Budgets ───────────────────────────────────────────────────────────────

    suspend fun getBudgets(month: String? = null, category: String? = null): AuthResult<List<BudgetDto>> {
        return try {
            val response = api.getBudgets(getAuthHeader(), month, category)
            if (response.isSuccessful && response.body() != null) {
                AuthResult.Success(response.body()!!.data)
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
                AuthResult.Success(Unit)
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
