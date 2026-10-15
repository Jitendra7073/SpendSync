package com.example.spendsync.data.bills

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Fired with a transaction id whenever its bills changed (upload finished), so open screens reload. */
object BillEvents {
    private val _changed = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val changed: SharedFlow<String> = _changed
    fun emit(txId: String) { _changed.tryEmit(txId) }
}
