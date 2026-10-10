package com.example.spendsync.data.repository

import com.example.spendsync.data.ServerMessages
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.ApiClient
import com.example.spendsync.data.remote.model.*
import com.example.spendsync.data.bills.BillCall
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

    /** Returns the user's all-time net balance (credits minus debits) across all transactions. */
    suspend fun getAllTimeBalance(forceRefresh: Boolean = false): AuthResult<Double> {
        val key = "transactions:alltime"
        if (!forceRefresh) cached<Double>(key)?.let { return AuthResult.Success(it) }
        return when (val result = getTransactions(limit = 2000, forceRefresh = forceRefresh)) {
            is AuthResult.Success -> {
                val balance = result.data.sumOf { tx ->
                    val amount = tx.amount.toDoubleOrNull() ?: 0.0
                    if (tx.type == "credit") amount else -amount
                }
                cache[key] = balance
                AuthResult.Success(balance)
            }
            is AuthResult.Error -> result
        }
    }

    suspend fun createTransaction(
        amount: Double,
        type: String,
        merchant: String,
        category: String,
        sourceApp: String? = null,
        note: String? = null,
        transactionDate: String? = null,
    ): AuthResult<TransactionDto> {
        return try {
            val request = CreateTransactionRequest(amount, type, merchant, category, sourceApp, note, transactionDate)
            val response = api.createTransaction(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("transactions", "dashboard", "plan")
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
        note: String? = null,
        transactionDate: String? = null,
    ): AuthResult<TransactionDto> {
        return try {
            val request = UpdateTransactionRequest(amount, type, merchant, category, sourceApp, note, transactionDate)
            val response = api.updateTransaction(getAuthHeader(), id, request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("transactions", "dashboard", "plan")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    /** Moves it to the Trash. Returns the ids of holds that went with it (their reminders must stop). */
    suspend fun deleteTransaction(id: String): AuthResult<List<String>> {
        return try {
            val response = api.deleteTransaction(getAuthHeader(), id)
            if (response.isSuccessful) {
                // "holds" too — the backend moves the linked holds to the Trash with it.
                cacheInvalidate("transactions", "dashboard", "holds", "plan")
                AuthResult.Success(response.body()?.data?.holdIds.orEmpty())
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun restoreTransaction(id: String): AuthResult<RestoredTransactionDto> {
        return try {
            val response = api.restoreTransaction(getAuthHeader(), id)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                cacheInvalidate("transactions", "dashboard", "holds", "plan")
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun restoreHold(id: String): AuthResult<HoldDto> {
        return try {
            val response = api.restoreHold(getAuthHeader(), id)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                cacheInvalidate("holds")
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    /** Never cached: the Trash changes from several screens. */
    suspend fun getTrash(cursor: String?): AuthResult<TrashPageDto> {
        return try {
            val response = api.getTrash(getAuthHeader(), cursor)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) AuthResult.Success(data)
            else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun deleteForever(kind: String, id: String): AuthResult<Unit> {
        return try {
            val response = api.deleteForever(getAuthHeader(), kind, id)
            if (response.isSuccessful) AuthResult.Success(Unit)
            else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun emptyTrash(): AuthResult<Int> {
        return try {
            val response = api.emptyTrash(getAuthHeader())
            if (response.isSuccessful) AuthResult.Success(response.body()?.data?.deleted ?: 0)
            else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Holds ─────────────────────────────────────────────────────────────────

    suspend fun createHold(
        transactionId: String,
        direction: String,
        personName: String,
        amount: Double,
        expectedReturnDate: String,
    ): AuthResult<HoldDto> {
        return try {
            val request = CreateHoldRequest(transactionId, direction, personName, amount, expectedReturnDate)
            val response = api.createHold(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("holds")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun getHolds(status: String? = null, direction: String? = null): AuthResult<List<HoldDto>> {
        val key = "holds:$status:$direction"
        cached<List<HoldDto>>(key)?.let { return AuthResult.Success(it) }
        return try {
            val response = api.getHolds(getAuthHeader(), status, direction)
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

    suspend fun updateHold(
        id: String,
        status: String? = null,
        personName: String? = null,
        expectedReturnDate: String? = null,
    ): AuthResult<HoldDto> {
        return try {
            val request = UpdateHoldRequest(personName, expectedReturnDate, status)
            val response = api.updateHold(getAuthHeader(), id, request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("holds")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    /** Moves the hold to the Trash. */
    suspend fun deleteHold(id: String): AuthResult<Unit> {
        return try {
            val response = api.deleteHold(getAuthHeader(), id)
            if (response.isSuccessful) {
                cacheInvalidate("holds")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Planify ───────────────────────────────────────────────────────────────

    /** The month's plan with live numbers. [today] (YYYY-MM-DD, the user's own calendar) drives pace and days left. */
    suspend fun getPlan(month: String, today: String = java.time.LocalDate.now().toString(), forceRefresh: Boolean = false): AuthResult<PlanViewDto> {
        val key = "plan:$month:$today"
        if (!forceRefresh) cached<PlanViewDto>(key)?.let { return AuthResult.Success(it) }
        return planCall { api.getPlan(getAuthHeader(), month, today) }.also { if (it is AuthResult.Success) cache[key] = it.data }
    }

    suspend fun savePlan(month: String, request: SavePlanRequest, today: String = java.time.LocalDate.now().toString()): AuthResult<PlanViewDto> =
        planCall { api.savePlan(getAuthHeader(), month, today, request) }.also { if (it is AuthResult.Success) cacheInvalidate("plan", "budgets", "dashboard") }

    suspend fun deletePlan(month: String, today: String = java.time.LocalDate.now().toString()): AuthResult<PlanViewDto> =
        planCall { api.deletePlan(getAuthHeader(), month, today) }.also { if (it is AuthResult.Success) cacheInvalidate("plan", "budgets", "dashboard") }

    suspend fun movePlanMoney(month: String, from: String, to: String, amount: Double, today: String = java.time.LocalDate.now().toString()): AuthResult<PlanViewDto> =
        planCall { api.movePlanMoney(getAuthHeader(), month, today, MoveMoneyRequest(from, to, amount)) }.also { if (it is AuthResult.Success) cacheInvalidate("plan", "budgets", "dashboard") }

    /** A first draft from the last 3 months. Nothing is saved on the server. */
    suspend fun getPlanSuggestions(month: String): AuthResult<SuggestionDto> = try {
        val response = api.getPlanSuggestions(getAuthHeader(), month)
        if (response.isSuccessful && response.body() != null) AuthResult.Success(response.body()!!.data)
        else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    /** Emails the user's own data to their account address (server side). */
    suspend fun emailExport(request: ExportEmailRequest): AuthResult<ExportEmailDto> = try {
        val response = api.emailExport(getAuthHeader(), request)
        if (response.isSuccessful && response.body() != null) AuthResult.Success(response.body()!!.data)
        else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    /** An AI-written follow-up draft for a hold, or source "none". Nothing is sent. */
    suspend fun holdMessage(request: HoldMessageRequest): AuthResult<HoldMessageDto> = try {
        val response = api.holdMessage(getAuthHeader(), request)
        if (response.isSuccessful && response.body() != null) AuthResult.Success(response.body()!!.data)
        else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    /** The user's Yes/No (or "forget") on whether some spending belongs in a bucket. Returns the refreshed plan. */
    suspend fun answerPlanMatch(month: String, request: MatchAnswerRequest, today: String = java.time.LocalDate.now().toString()): AuthResult<PlanViewDto> =
        planCall { api.answerPlanMatch(getAuthHeader(), month, today, request) }.also { if (it is AuthResult.Success) cacheInvalidate("plan", "budgets", "dashboard") }

    /** One turn of the AI planning guide. */
    suspend fun planGuide(request: GuideTurnRequest): AuthResult<GuideTurnDto> = try {
        val response = api.planGuide(getAuthHeader(), request)
        if (response.isSuccessful && response.body() != null) AuthResult.Success(response.body()!!.data)
        else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    private suspend fun planCall(block: suspend () -> retrofit2.Response<SuccessResponse<PlanViewDto>>): AuthResult<PlanViewDto> = try {
        val response = block()
        if (response.isSuccessful && response.body() != null) AuthResult.Success(response.body()!!.data)
        else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
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
                cacheInvalidate("budgets", "dashboard", "plan")
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
                cacheInvalidate("budgets", "dashboard", "plan")
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
                cacheInvalidate("budgets", "dashboard", "plan")
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

    suspend fun updateSettings(request: UpdateSettingsRequest): AuthResult<SettingsDto> {
        return try {
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

    /** Permanently deletes the account on the server (all data cascades). */
    suspend fun deleteAccount(): AuthResult<Unit> {
        return try {
            val response = api.deleteAccount(getAuthHeader())
            if (response.isSuccessful) {
                cache.clear()
                AuthResult.Success(Unit)
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

    // ── Bills ─────────────────────────────────────────────────────────────────

    private suspend fun <T> billCall(block: suspend () -> retrofit2.Response<SuccessResponse<T>>): BillCall<T> = try {
        val r = block()
        val data = r.body()?.data
        if (r.isSuccessful && data != null) BillCall.Ok(data)
        else BillCall.Fail(parseErrorMessage(r.errorBody()?.string()), BillCall.isPermanent(r.code()), r.code())
    } catch (e: Exception) {
        BillCall.Fail(e.toUserMessage(), permanent = false)
    }

    suspend fun listBills(txId: String): AuthResult<List<BillDto>> = when (val r = billCall { api.listBills(getAuthHeader(), txId) }) {
        is BillCall.Ok -> AuthResult.Success(r.data)
        is BillCall.Fail -> AuthResult.Error(r.message)
    }

    suspend fun reserveBill(txId: String, req: ReserveBillRequest) = billCall { api.reserveBill(getAuthHeader(), txId, req) }
    suspend fun signBill(id: String) = billCall { api.signBill(getAuthHeader(), id) }

    suspend fun confirmBill(id: String, req: ConfirmBillRequest): BillCall<BillDto> =
        billCall { api.confirmBill(getAuthHeader(), id, req) }.also { if (it is BillCall.Ok) cacheInvalidate("transactions") }

    suspend fun deleteBill(id: String): AuthResult<Unit> = try {
        val r = api.deleteBill(getAuthHeader(), id)
        if (r.isSuccessful) { cacheInvalidate("transactions"); AuthResult.Success(Unit) } else AuthResult.Error(parseErrorMessage(r.errorBody()?.string()))
    } catch (e: Exception) { AuthResult.Error(e.toUserMessage()) }

    suspend fun restoreBill(id: String): AuthResult<BillDto> = when (val r = billCall { api.restoreBill(getAuthHeader(), id) }) {
        is BillCall.Ok -> { cacheInvalidate("transactions"); AuthResult.Success(r.data) }
        is BillCall.Fail -> AuthResult.Error(r.message)
    }

    suspend fun billUsage(): AuthResult<BillUsageDto> = when (val r = billCall { api.billUsage(getAuthHeader()) }) {
        is BillCall.Ok -> AuthResult.Success(r.data)
        is BillCall.Fail -> AuthResult.Error(r.message)
    }

    suspend fun getTransaction(id: String): AuthResult<TransactionDto> = when (val r = billCall { api.getTransactionById(getAuthHeader(), id) }) {
        is BillCall.Ok -> AuthResult.Success(r.data)
        is BillCall.Fail -> AuthResult.Error(r.message)
    }

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return tr(R.string.an_unexpected_error_occurred)
        return try {
            val json = gson.fromJson(errorBody, JsonObject::class.java)
            val errorObj = json.getAsJsonObject("error")
            ServerMessages.localize(
                message = errorObj?.get("message")?.asString ?: json.get("message")?.asString ?: json.get("error")?.takeIf { it.isJsonPrimitive }?.asString,
                code = errorObj?.get("code")?.asString ?: json.get("code")?.asString,
            )
        } catch (e: Exception) {
            tr(R.string.an_unexpected_error_occurred)
        }
    }

    private fun Exception.toUserMessage(): String = when {
        message?.contains("Unable to resolve host", ignoreCase = true) == true ->
            tr(R.string.no_internet_connection_please_check_your)
        message?.contains("timeout", ignoreCase = true) == true ->
            tr(R.string.request_timed_out_please_try_again)
        message?.contains("Connection refused", ignoreCase = true) == true ->
            tr(R.string.cannot_reach_the_server_please_try)
        else -> message ?: tr(R.string.an_unexpected_error_occurred)
    }
}
