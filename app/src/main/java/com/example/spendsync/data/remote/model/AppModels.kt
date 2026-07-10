package com.example.spendsync.data.remote.model

import com.google.gson.annotations.SerializedName

// ── Generic Wrapper ──────────────────────────────────────────────────────────

data class SuccessResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: T,
)

data class SuccessResponseList<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: List<T>,
    @SerializedName("meta")    val meta: ResponseMeta?,
)

data class ResponseMeta(
    @SerializedName("page")  val page: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("total") val total: Int,
)

// ── Transactions ──────────────────────────────────────────────────────────────

data class TransactionDto(
    @SerializedName("id")        val id: String,
    @SerializedName("userId")    val userId: String,
    @SerializedName("amount")    val amount: String, // decimal string
    @SerializedName("type")      val type: String,   // "debit" or "credit"
    @SerializedName("merchant")  val merchant: String,
    @SerializedName("category")  val category: String,
    @SerializedName("sourceApp") val sourceApp: String?,
    @SerializedName("note")      val note: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String?,
)

data class CreateTransactionRequest(
    @SerializedName("amount")    val amount: Double,
    @SerializedName("type")      val type: String,   // "debit" or "credit"
    @SerializedName("merchant")  val merchant: String,
    @SerializedName("category")  val category: String,
    @SerializedName("sourceApp") val sourceApp: String? = null,
    @SerializedName("note")      val note: String? = null,
    // ISO datetime, never in the future — lets the user backdate a transaction.
    @SerializedName("transactionDate") val transactionDate: String? = null,
)

data class UpdateTransactionRequest(
    @SerializedName("amount")    val amount: Double? = null,
    @SerializedName("type")      val type: String? = null,
    @SerializedName("merchant")  val merchant: String? = null,
    @SerializedName("category")  val category: String? = null,
    @SerializedName("sourceApp") val sourceApp: String? = null,
    @SerializedName("note")      val note: String? = null,
    @SerializedName("transactionDate") val transactionDate: String? = null,
)

// ── Budgets ───────────────────────────────────────────────────────────────────

data class BudgetDto(
    @SerializedName("id")          val id: String,
    @SerializedName("userId")      val userId: String,
    @SerializedName("category")    val category: String,
    @SerializedName("month")       val month: String,      // "YYYY-MM"
    @SerializedName("limitAmount") val limitAmount: String,
    @SerializedName("createdAt")   val createdAt: String,
)

data class CreateBudgetRequest(
    @SerializedName("category")    val category: String,
    @SerializedName("month")       val month: String,      // "YYYY-MM"
    @SerializedName("limitAmount") val limitAmount: Double,
)

data class UpdateBudgetRequest(
    @SerializedName("category")    val category: String? = null,
    @SerializedName("month")       val month: String? = null,
    @SerializedName("limitAmount") val limitAmount: Double? = null,
)

// ── Dashboard ─────────────────────────────────────────────────────────────────

data class DashboardSummaryDto(
    @SerializedName("month")             val month: String,
    @SerializedName("totals")            val totals: DashboardTotalsDto,
    @SerializedName("categoryBreakdown") val categoryBreakdown: List<CategoryBreakdownDto>,
    @SerializedName("monthlyTrend")      val monthlyTrend: List<MonthlyTrendDto>,
)

data class DashboardTotalsDto(
    @SerializedName("totalSpent")        val totalSpent: Double,
    @SerializedName("totalEarned")       val totalEarned: Double,
    @SerializedName("totalTransactions") val totalTransactions: Int,
    @SerializedName("totalBudget")       val totalBudget: Double,
    @SerializedName("netAmount")         val netAmount: Double,
)

data class CategoryBreakdownDto(
    @SerializedName("category")         val category: String,
    @SerializedName("spent")            val spent: Double,
    @SerializedName("earned")           val earned: Double,
    @SerializedName("transactionCount") val transactionCount: Int,
    @SerializedName("budget")           val budget: Double?,
    @SerializedName("remaining")        val remaining: Double?,
    @SerializedName("percentageUsed")   val percentageUsed: Double?,
)

data class MonthlyTrendDto(
    @SerializedName("month")            val month: String,
    @SerializedName("spent")            val spent: Double,
    @SerializedName("earned")           val earned: Double,
    @SerializedName("net")              val net: Double,
    @SerializedName("transactionCount") val transactionCount: Int,
)

data class TopMerchantDto(
    @SerializedName("merchant")   val merchant: String,
    @SerializedName("totalSpent") val totalSpent: String,
    @SerializedName("count")      val count: Int,
)

// ── Settings ─────────────────────────────────────────────────────────────────

data class SettingsDto(
    @SerializedName("developerMode")      val developerMode: Boolean,
    @SerializedName("emailNotifications") val emailNotifications: Boolean,
    @SerializedName("darkMode")           val darkMode: Boolean,
    @SerializedName("pushNotifications")  val pushNotifications: Boolean,
    @SerializedName("autoBackup")         val autoBackup: Boolean,
    @SerializedName("accentColor")        val accentColor: String,
    @SerializedName("language")           val language: String,
    @SerializedName("currency")           val currency: String,
    @SerializedName("dateFormat")         val dateFormat: String,
)

data class UpdateSettingsRequest(
    @SerializedName("darkMode")          val darkMode: Boolean? = null,
    @SerializedName("pushNotifications") val pushNotifications: Boolean? = null,
    @SerializedName("autoBackup")        val autoBackup: Boolean? = null,
    @SerializedName("accentColor")       val accentColor: String? = null,
    @SerializedName("language")          val language: String? = null,
    @SerializedName("currency")          val currency: String? = null,
    @SerializedName("dateFormat")        val dateFormat: String? = null,
)

// ── Categories ───────────────────────────────────────────────────────────────

data class CategoryDto(
    @SerializedName("id")        val id: String,
    @SerializedName("userId")    val userId: String,
    @SerializedName("keyword")   val keyword: String,
    @SerializedName("category")  val category: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String?,
)

data class CreateCategoryRequest(
    @SerializedName("keyword")  val keyword: String,
    @SerializedName("category") val category: String,
)

data class UpdateCategoryRequest(
    @SerializedName("keyword")  val keyword: String? = null,
    @SerializedName("category") val category: String? = null,
)

data class CategorySuggestRequest(
    @SerializedName("merchant") val merchant: String,
)

data class CategorySuggestResponse(
    @SerializedName("merchant")          val merchant: String,
    @SerializedName("suggestedCategory") val suggestedCategory: String?,
)
