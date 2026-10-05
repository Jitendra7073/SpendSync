package com.example.spendsync.data.assistant

/** The user's own assistant settings (Settings -> Assistant). Sent with every question; synced across devices. */
data class AssistantPrefs(
    /** "auto" = the app picks the best available free model; otherwise a model id from the status list. */
    val model: String = "auto",
    /** short | balanced | detailed */
    val style: String = "balanced",
    /** simple | friendly | professional */
    val tone: String = "friendly",
    val instructions: String = "",
    /** Tool names the user switched off. Locked actions are not tools, so they never appear here. */
    val disabledTools: Set<String> = emptySet(),
) {
    companion object {
        const val MAX_INSTRUCTIONS = 300
        val STYLES = listOf("short", "balanced", "detailed")
        val TONES = listOf("simple", "friendly", "professional")
    }
}

internal fun parseToolSet(raw: String?): Set<String> =
    raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

internal fun serializeToolSet(tools: Set<String>): String = tools.sorted().joinToString(",")

/** Tools the assistant has, shown as switches. Order = display order. Keep in sync with `ASSISTANT_TOOLS` in the backend. */
enum class AssistantToolInfo(val id: String) {
    SearchHelp("search_help"),
    GetBalance("get_balance"),
    GetSpendingSummary("get_spending_summary"),
    SearchTransactions("search_transactions"),
    GetBudgetStatus("get_budget_status"),
    GetPlanStatus("get_plan_status"),
    ListHolds("list_holds"),
    GetTopMerchants("get_top_merchants"),
    GetSettings("get_settings"),
    ProposeEntry("propose_entry"),
    PrepareFollowup("prepare_followup"),
    ShareMessage("share_message"),
    OpenScreen("open_screen"),
}

/** Actions the assistant can never do, whatever the user chooses. Shown locked. */
val ASSISTANT_LOCKED_ACTIONS = listOf("clear_data", "sign_out", "delete_account")
