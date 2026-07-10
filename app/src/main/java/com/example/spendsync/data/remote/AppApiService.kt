package com.example.spendsync.data.remote

import com.example.spendsync.data.remote.model.*
import retrofit2.Response
import retrofit2.http.*

interface AppApiService {

    // ── Transactions ──────────────────────────────────────────────────────────

    @POST("api/transactions")
    suspend fun createTransaction(
        @Header("Authorization") token: String,
        @Body body: CreateTransactionRequest
    ): Response<SuccessResponse<TransactionDto>>

    @GET("api/transactions")
    suspend fun getTransactions(
        @Header("Authorization") token: String,
        @Query("startDate") startDate: String?,
        @Query("endDate") endDate: String?,
        @Query("category") category: String?,
        @Query("type") type: String?,
        @Query("page") page: Int?,
        @Query("limit") limit: Int?
    ): Response<SuccessResponseList<TransactionDto>>

    @GET("api/transactions/{id}")
    suspend fun getTransactionById(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SuccessResponse<TransactionDto>>

    @PATCH("api/transactions/{id}")
    suspend fun updateTransaction(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: UpdateTransactionRequest
    ): Response<SuccessResponse<TransactionDto>>

    @DELETE("api/transactions/{id}")
    suspend fun deleteTransaction(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SuccessResponse<Unit>>

    // ── Budgets ───────────────────────────────────────────────────────────────

    @POST("api/budgets")
    suspend fun createBudget(
        @Header("Authorization") token: String,
        @Body body: CreateBudgetRequest
    ): Response<SuccessResponse<BudgetDto>>

    @GET("api/budgets")
    suspend fun getBudgets(
        @Header("Authorization") token: String,
        @Query("month") month: String?,
        @Query("category") category: String?
    ): Response<SuccessResponseList<BudgetDto>>

    @PATCH("api/budgets/{id}")
    suspend fun updateBudget(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: UpdateBudgetRequest
    ): Response<SuccessResponse<BudgetDto>>

    @DELETE("api/budgets/{id}")
    suspend fun deleteBudget(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SuccessResponse<Unit>>

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GET("api/dashboard/summary")
    suspend fun getDashboardSummary(
        @Header("Authorization") token: String,
        @Query("month") month: String?
    ): Response<SuccessResponse<DashboardSummaryDto>>

    @GET("api/dashboard/trend")
    suspend fun getMonthlyTrend(
        @Header("Authorization") token: String,
        @Query("months") months: Int?
    ): Response<SuccessResponseList<MonthlyTrendDto>>

    @GET("api/dashboard/top-merchants")
    suspend fun getTopMerchants(
        @Header("Authorization") token: String,
        @Query("limit") limit: Int?
    ): Response<SuccessResponseList<TopMerchantDto>>

    // ── Settings ──────────────────────────────────────────────────────────────

    @GET("api/settings")
    suspend fun getSettings(
        @Header("Authorization") token: String
    ): Response<SuccessResponse<SettingsDto>>

    @PATCH("api/settings")
    suspend fun updateSettings(
        @Header("Authorization") token: String,
        @Body body: UpdateSettingsRequest
    ): Response<SuccessResponse<SettingsDto>>

    // ── Categories ────────────────────────────────────────────────────────────

    @POST("api/categories")
    suspend fun createCategory(
        @Header("Authorization") token: String,
        @Body body: CreateCategoryRequest
    ): Response<SuccessResponse<CategoryDto>>

    @GET("api/categories")
    suspend fun getCategories(
        @Header("Authorization") token: String
    ): Response<SuccessResponseList<CategoryDto>>

    @GET("api/categories/{id}")
    suspend fun getCategoryById(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SuccessResponse<CategoryDto>>

    @PATCH("api/categories/{id}")
    suspend fun updateCategory(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: UpdateCategoryRequest
    ): Response<SuccessResponse<CategoryDto>>

    @DELETE("api/categories/{id}")
    suspend fun deleteCategory(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SuccessResponse<Unit>>

    @POST("api/categories/suggest")
    suspend fun suggestCategory(
        @Header("Authorization") token: String,
        @Body body: CategorySuggestRequest
    ): Response<SuccessResponse<CategorySuggestResponse>>
}
