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
