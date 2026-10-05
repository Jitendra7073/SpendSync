package com.example.spendsync.data.remote.model

import com.google.gson.annotations.SerializedName

// ── Planify: the month's plan, its buckets and live status ───────────────────

data class PlanViewDto(
    @SerializedName("month")     val month: String,
    /** A plan header or at least one bucket exists for this month. */
    @SerializedName("exists")    val exists: Boolean,
    /** False for old budgets that never had an income entered. */
    @SerializedName("hasIncome") val hasIncome: Boolean,
    @SerializedName("income")    val income: Double,
    @SerializedName("carryOver") val carryOver: Double,
    @SerializedName("status")    val status: PlanStatusDto,
    /** Spending the user confirmed also counts in a bucket (Food counts in Eating out). */
    @SerializedName("aliases")   val aliases: List<AliasDto> = emptyList(),
    /** Yes/No questions about spending that looks like it belongs in a bucket. */
    @SerializedName("matches")   val matches: List<MatchDto> = emptyList(),
)

data class AliasDto(
    @SerializedName("bucket") val bucket: String,
    @SerializedName("kind")   val kind: String, // category | merchant
    @SerializedName("key")    val key: String,
    @SerializedName("label")  val label: String,
)

data class MatchDto(
    @SerializedName("bucket") val bucket: String,
    @SerializedName("kind")   val kind: String,
    @SerializedName("key")    val key: String,
    @SerializedName("label")  val label: String,
    @SerializedName("count")  val count: Int,
    @SerializedName("total")  val total: Double,
)

data class MatchAnswerRequest(
    @SerializedName("bucket")  val bucket: String,
    @SerializedName("kind")    val kind: String,
    @SerializedName("label")   val label: String,
    @SerializedName("verdict") val verdict: String, // yes | no | forget
)

data class PlanStatusDto(
    @SerializedName("daysInMonth")          val daysInMonth: Int,
    @SerializedName("day")                  val day: Int,
    @SerializedName("daysLeft")             val daysLeft: Int,
    @SerializedName("monthProgressPercent") val monthProgressPercent: Double,
    @SerializedName("available")            val available: Double,
    @SerializedName("planned")              val planned: Double,
    /** Money still unassigned (positive) or planned beyond what is available (negative). */
    @SerializedName("leftToPlan")           val leftToPlan: Double,
    @SerializedName("spentInPlan")          val spentInPlan: Double,
    @SerializedName("buckets")              val buckets: List<BucketDto>,
    @SerializedName("counts")               val counts: PlanCountsDto,
    @SerializedName("safeToSpendToday")     val safeToSpendToday: Double,
    @SerializedName("unplanned")            val unplanned: List<UnplannedDto>,
    @SerializedName("unplannedTotal")       val unplannedTotal: Double,
)

data class PlanCountsDto(
    @SerializedName("ok")    val ok: Int,
    @SerializedName("close") val close: Int,
    @SerializedName("over")  val over: Int,
)

data class UnplannedDto(
    @SerializedName("category") val category: String,
    @SerializedName("spent")    val spent: Double,
)

/** One bucket of the plan: a limit on one transaction category, with its live numbers. */
data class BucketDto(
    @SerializedName("id")          val id: String,
    @SerializedName("category")    val category: String,
    @SerializedName("name")        val name: String,
    /** "fixed" | "spend" | "savings" */
    @SerializedName("kind")        val kind: String,
    @SerializedName("limit")       val limit: Double,
    @SerializedName("spent")       val spent: Double,
    @SerializedName("sortOrder")   val sortOrder: Int,
    @SerializedName("rollover")    val rollover: Boolean,
    @SerializedName("remaining")   val remaining: Double,
    @SerializedName("percent")     val percent: Double,
    /** "ok" | "close" | "over" | "paid" | "saved" */
    @SerializedName("state")       val state: String,
    @SerializedName("paceRatio")   val paceRatio: Double?,
    @SerializedName("projected")   val projected: Double?,
    @SerializedName("runsOutOnDay") val runsOutOnDay: Int?,
    @SerializedName("paceWarning") val paceWarning: Boolean,
)

data class PlanItemRequest(
    @SerializedName("category")    val category: String,
    @SerializedName("name")        val name: String,
    @SerializedName("kind")        val kind: String,
    @SerializedName("limitAmount") val limitAmount: Double,
    @SerializedName("sortOrder")   val sortOrder: Int,
    @SerializedName("rollover")    val rollover: Boolean = false,
)

data class SavePlanRequest(
    @SerializedName("income")    val income: Double,
    @SerializedName("carryOver") val carryOver: Double,
    @SerializedName("items")     val items: List<PlanItemRequest>,
)

data class MoveMoneyRequest(
    @SerializedName("fromCategory") val fromCategory: String,
    @SerializedName("toCategory")   val toCategory: String,
    @SerializedName("amount")       val amount: Double,
)

data class SuggestionDto(
    @SerializedName("items")           val items: List<SuggestedItemDto>,
    @SerializedName("suggestedIncome") val suggestedIncome: Double,
    @SerializedName("monthsUsed")      val monthsUsed: Int,
    @SerializedName("confidence")      val confidence: String = "good",
    /** What the income was read from ("Enacton Salary") and how sure that is: this_month, history, largest or none. */
    @SerializedName("incomeLabel")     val incomeLabel: String = "",
    @SerializedName("incomeSource")    val incomeSource: String = "none",
    @SerializedName("carryOver")       val carryOver: Double = 0.0,
    @SerializedName("carryLabel")      val carryLabel: String = "",
    /** Net spend per category for each month used (oldest first), to test a draft plan against the past. */
    @SerializedName("monthlySpend")    val monthlySpend: Map<String, List<Double>> = emptyMap(),
    @SerializedName("monthLabels")     val monthLabels: List<String> = emptyList(),
)

data class SuggestedItemDto(
    @SerializedName("category")  val category: String,
    @SerializedName("name")      val name: String,
    @SerializedName("kind")      val kind: String,
    @SerializedName("limit")     val limit: Double,
    /** What was spent on average, shown as "last month" beside the new number. */
    @SerializedName("average")   val average: Double,
    @SerializedName("sortOrder") val sortOrder: Int,
    @SerializedName("reason")     val reason: String = "average",
    @SerializedName("monthsSeen") val monthsSeen: Int = 0,
    @SerializedName("lowest")     val lowest: Double = 0.0,
    @SerializedName("highest")    val highest: Double = 0.0,
)

// ── AI planning guide ────────────────────────────────────────────────────────

data class GuideTurnItem(
    @SerializedName("topic")    val topic: String,
    @SerializedName("question") val question: String,
    @SerializedName("answer")   val answer: String,
)

data class GuideTurnRequest(
    @SerializedName("month")      val month: String,
    @SerializedName("income")     val income: Double,
    @SerializedName("language")   val language: String,
    @SerializedName("transcript") val transcript: List<GuideTurnItem>,
    @SerializedName("focus")      val focus: String? = null,
)

/** What choosing an option does to the plan. The server only ever sends the kinds PlanGuide.apply understands. */
data class GuideEffectDto(
    @SerializedName("type")     val type: String,
    @SerializedName("value")    val value: Double? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("name")     val name: String? = null,
)

data class GuideOptionDto(
    @SerializedName("label")  val label: String,
    @SerializedName("effect") val effect: GuideEffectDto,
)

data class GuideQuestionDto(
    @SerializedName("topic")    val topic: String,
    @SerializedName("question") val question: String,
    @SerializedName("options")  val options: List<GuideOptionDto>,
)

/** `source` is "ai" or "basic" (no model could answer: use the built-in questions). */
data class GuideTurnDto(
    @SerializedName("source")   val source: String,
    @SerializedName("done")     val done: Boolean = false,
    @SerializedName("note")     val note: String? = null,
    @SerializedName("model")    val model: String? = null,
    @SerializedName("question") val question: GuideQuestionDto? = null,
)
