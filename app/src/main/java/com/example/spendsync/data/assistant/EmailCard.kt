package com.example.spendsync.data.assistant

import com.example.spendsync.data.bills.BillCall
import com.example.spendsync.data.remote.model.EmailBillsResultDto

sealed interface EmailCardState {
    data object Idle : EmailCardState
    data object Sending : EmailCardState
    data class Sent(val to: String, val attached: Int, val notAttached: Int, val skipped: Int) : EmailCardState
    data class Failed(val message: String, val cooldown: Boolean) : EmailCardState
}

object EmailCard {
    fun from(r: BillCall<EmailBillsResultDto>): EmailCardState = when (r) {
        is BillCall.Ok -> EmailCardState.Sent(r.data.sentTo, r.data.attached, r.data.notAttached, r.data.skipped)
        is BillCall.Fail -> EmailCardState.Failed(r.message, cooldown = r.code == 429)
    }
}

/**
 * Email card state kept outside the chat list, keyed by "<message id>:email:<n>": a LazyColumn forgets
 * `remember` state when a card scrolls away, which would re-enable Send on a card that already sent.
 */
object EmailCardMemory {
    private val states = androidx.compose.runtime.mutableStateMapOf<String, EmailCardState>()
    private val closed = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    fun state(key: String): EmailCardState = states[key] ?: EmailCardState.Idle
    fun set(key: String, s: EmailCardState) { states[key] = s }
    fun dismissed(key: String): Boolean = closed[key] == true
    fun dismiss(key: String) { closed[key] = true }
    /** A new card for the same key (the user asked again). */
    fun reset(key: String) { states.remove(key); closed.remove(key) }
}
