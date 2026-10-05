package com.example.spendsync.data.assistant

import android.content.Context
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.notifications.HoldReminderWorker
import java.time.LocalDate

/**
 * Saves an entry the assistant prepared, but only after the user tapped Confirm. It uses the same
 * calls and the same rules as the Add Expense screen, so an assistant entry looks like any other.
 */
class ProposalExecutor(
    private val finance: FinanceRepository,
    private val context: Context,
) {
    /** Returns null on success, or a short reason the user can read. */
    suspend fun execute(p: Proposal): String? {
        val type = if (p.kind == "income") "credit" else "debit"
        val merchant = p.note?.takeIf { it.isNotBlank() } ?: p.person ?: p.category
        val res = finance.createTransaction(
            amount = p.amount, type = type, merchant = merchant, category = p.category,
            note = p.note, transactionDate = "${p.date}T00:00:00.000Z",
        )
        if (res !is AuthResult.Success) return (res as AuthResult.Error).message
        com.example.spendsync.data.planify.PlanAlerts.onTransactionChanged(context, finance, com.example.spendsync.data.local.SessionDataStore(context), res.data)

        val person = p.person
        val returnDate = p.returnDate
        if (person != null && returnDate != null) {
            val direction = if (p.kind == "income") "owed_by_me" else "owed_to_me"
            val hold = finance.createHold(res.data.id, direction, person, p.amount, "${returnDate}T00:00:00.000Z")
            if (hold !is AuthResult.Success) return (hold as AuthResult.Error).message
            runCatching {
                HoldReminderWorker.schedule(context, hold.data.id, person, p.amount, direction, LocalDate.parse(returnDate))
            }
        }
        return null
    }
}
