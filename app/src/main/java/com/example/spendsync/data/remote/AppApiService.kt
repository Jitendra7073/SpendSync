package com.example.spendsync.data.remote

import com.example.spendsync.data.remote.model.*
import retrofit2.Response
import retrofit2.http.*

interface AppApiService {

    // ── Account ───────────────────────────────────────────────────────────────

    @DELETE("api/account")
    suspend fun deleteAccount(
        @Header("Authorization") token: String
    ): Response<SuccessResponse<Map<String, Boolean>>>

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
    ): Response<SuccessResponse<DeleteTransactionResult>>

    @POST("api/transactions/{id}/restore")
    suspend fun restoreTransaction(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SuccessResponse<RestoredTransactionDto>>

    @POST("api/holds/{id}/restore")
    suspend fun restoreHold(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SuccessResponse<HoldDto>>

    // ── Trash ─────────────────────────────────────────────────────────────────

    @GET("api/trash")
    suspend fun getTrash(
        @Header("Authorization") token: String,
        @Query("cursor") cursor: String?
    ): Response<SuccessResponse<TrashPageDto>>

    @DELETE("api/trash/{kind}/{id}")
    suspend fun deleteForever(
        @Header("Authorization") token: String,
        @Path("kind") kind: String,
        @Path("id") id: String
    ): Response<Unit>

    @DELETE("api/trash")
    suspend fun emptyTrash(
        @Header("Authorization") token: String
    ): Response<SuccessResponse<TrashEmptiedDto>>

    // ── Holds ─────────────────────────────────────────────────────────────────

    @POST("api/holds")
    suspend fun createHold(
        @Header("Authorization") token: String,
        @Body body: CreateHoldRequest
    ): Response<SuccessResponse<HoldDto>>

    @GET("api/holds")
    suspend fun getHolds(
        @Header("Authorization") token: String,
        @Query("status") status: String?,
        @Query("direction") direction: String?
    ): Response<SuccessResponseList<HoldDto>>

    @PATCH("api/holds/{id}")
    suspend fun updateHold(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body body: UpdateHoldRequest
    ): Response<SuccessResponse<HoldDto>>

    @DELETE("api/holds/{id}")
    suspend fun deleteHold(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<SuccessResponse<Unit>>

    // ── Planify ───────────────────────────────────────────────────────────────

    @GET("api/plans/{month}")
    suspend fun getPlan(
        @Header("Authorization") token: String,
        @Path("month") month: String,
        @Query("today") today: String,
    ): Response<SuccessResponse<PlanViewDto>>

    @DELETE("api/plans/{month}")
    suspend fun deletePlan(
        @Header("Authorization") token: String,
        @Path("month") month: String,
        @Query("today") today: String,
    ): Response<SuccessResponse<PlanViewDto>>

    @PUT("api/plans/{month}")
    suspend fun savePlan(
        @Header("Authorization") token: String,
        @Path("month") month: String,
        @Query("today") today: String,
        @Body body: SavePlanRequest,
    ): Response<SuccessResponse<PlanViewDto>>

    @POST("api/plans/{month}/move")
    suspend fun movePlanMoney(
        @Header("Authorization") token: String,
        @Path("month") month: String,
        @Query("today") today: String,
        @Body body: MoveMoneyRequest,
    ): Response<SuccessResponse<PlanViewDto>>

    @POST("api/export/email")
    suspend fun emailExport(
        @Header("Authorization") token: String,
        @Body body: ExportEmailRequest,
    ): Response<SuccessResponse<ExportEmailDto>>

    @POST("api/holds/message")
    suspend fun holdMessage(
        @Header("Authorization") token: String,
        @Body body: HoldMessageRequest,
    ): Response<SuccessResponse<HoldMessageDto>>

    @POST("api/plans/{month}/match")
    suspend fun answerPlanMatch(
        @Header("Authorization") token: String,
        @Path("month") month: String,
        @Query("today") today: String,
        @Body body: MatchAnswerRequest,
    ): Response<SuccessResponse<PlanViewDto>>

    @POST("api/plans/guide")
    suspend fun planGuide(
        @Header("Authorization") token: String,
        @Body body: GuideTurnRequest,
    ): Response<SuccessResponse<GuideTurnDto>>

    @GET("api/plans/suggestions")
    suspend fun getPlanSuggestions(
        @Header("Authorization") token: String,
        @Query("month") month: String,
    ): Response<SuccessResponse<SuggestionDto>>

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
