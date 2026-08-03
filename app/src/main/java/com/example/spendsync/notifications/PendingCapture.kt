package com.example.spendsync.notifications

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class PendingCapture(
    val amount: Double,
    val direction: TransactionDirection,
    val payee: String?,
    val sourceApp: String,
    val capturedAtMillis: Long,
)

private val pendingCaptureGson = Gson()
private val pendingCaptureListType = object : TypeToken<List<PendingCapture>>() {}.type

internal fun serializePendingCaptures(captures: List<PendingCapture>): String =
    pendingCaptureGson.toJson(captures)

internal fun parsePendingCaptures(raw: String?): List<PendingCapture> {
    if (raw.isNullOrBlank()) return emptyList()
    return pendingCaptureGson.fromJson<List<PendingCapture>>(raw, pendingCaptureListType) ?: emptyList()
}
