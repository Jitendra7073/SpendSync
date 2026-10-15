package com.example.spendsync.data.repository

import com.example.spendsync.data.ServerMessages
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.data.local.CachedResponse
import com.example.spendsync.data.local.OfflineDb
import com.example.spendsync.data.local.OutboxOp
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.ApiClient
import com.example.spendsync.data.remote.model.*
import com.example.spendsync.notifications.CaptureNotifier
import com.example.spendsync.notifications.PendingCaptureRetryWorker
import com.example.spendsync.data.bills.BillCall
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** A capture waiting in the outbox, with whether it should already show as a payment. */
private data class QueuedCapture(val request: CaptureRequest, val addsTransaction: Boolean)

/**
 * Handles communication with transaction, budget, and analytics endpoints.
 * Automatically injects the stored Better Auth token into requests.
 *
 * Offline-first: every GET is cached in memory and on disk ([OfflineDb]); when the network is
 * down the last good copy is returned. Transaction writes made offline go to the outbox and are
 * replayed by [PendingCaptureRetryWorker]; lists show them right away via [overlayPending].
 * One instance per process ([shared]) so every screen, worker and service sees the same cache.
 */
class FinanceRepository private constructor(
    private val sessionDataStore: SessionDataStore,
) {
    private val api = ApiClient.appApi
    private val gson = Gson()
    private val appContext = sessionDataStore.appContext
    private val dao = OfflineDb.get(appContext).dao()

    companion object {
        @Volatile private var instance: FinanceRepository? = null

        fun shared(sessionDataStore: SessionDataStore): FinanceRepository = instance ?: synchronized(this) {
            instance ?: FinanceRepository(sessionDataStore).also { instance = it }
        }
    }

    // Read-through cache for GET endpoints, keyed by endpoint+params. Cleared
    // per-prefix whenever a mutation could have changed that data, so screens
    // revisiting the same query (e.g. switching tabs back to Home) don't
    // re-hit the network unless something actually changed. Concurrent: the UI,
    // workers and the notification service all share it.
    private val cache = ConcurrentHashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> cached(key: String): T? = cache[key] as? T

    private val _changes = MutableStateFlow(0L)

    /**
     * Ticks whenever data may have changed: any write (screen, notification capture, assistant,
     * outbox sync). Open tabs collect it (skipping the current value) and reload, so nobody has to
     * pull to refresh after a new transaction.
     */
    val changes: StateFlow<Long> = _changes

    private fun cacheInvalidate(vararg prefixes: String) {
        cache.keys.removeAll { key -> prefixes.any { key.startsWith(it) } }
        _changes.update { it + 1 }
    }

    /** Call on sign-out so the next signed-in user never sees a stale cache (memory and disk). */
    fun clearCache() {
        cache.clear()
        CoroutineScope(Dispatchers.IO).launch { dao.clearResponses() }
    }

    private suspend fun getAuthHeader(): String {
        val token = sessionDataStore.sessionToken.firstOrNull() ?: ""
        return if (token.isNotBlank()) "Bearer $token" else ""
    }

    private suspend fun userId(): String = sessionDataStore.userId.firstOrNull().orEmpty()

    /**
     * Memory → network → disk. A network success refreshes memory and disk; a network failure
     * (no connection, timeout) falls back to the last copy saved on disk for this user.
     */
    private suspend inline fun <reified T : Any> read(key: String, forceRefresh: Boolean, crossinline fetch: suspend () -> Response<*>): AuthResult<T> {
        if (!forceRefresh) cached<T>(key)?.let { return AuthResult.Success(it) }
        val diskKey = "${userId()}|$key"
        return try {
            val response = fetch()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val data = when (body) {
                    is SuccessResponse<*> -> body.data
                    is SuccessResponseList<*> -> body.data
                    else -> body
                } as T
                cache[key] = data
                dao.putResponse(CachedResponse(diskKey, gson.toJson(data), System.currentTimeMillis()))
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: IOException) {
            dao.getResponse(diskKey)
                ?.let { json -> runCatching { gson.fromJson<T>(json, object : TypeToken<T>() {}.type) }.getOrNull() }
                ?.let { AuthResult.Success(it) }
                ?: AuthResult.Error(e.toUserMessage())
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Dashboard Summary ─────────────────────────────────────────────────────

    suspend fun getDashboardSummary(month: String? = null, forceRefresh: Boolean = false): AuthResult<DashboardSummaryDto> =
        read("dashboard:$month", forceRefresh) { api.getDashboardSummary(getAuthHeader(), month) }

    // ── Transactions ──────────────────────────────────────────────────────────

    suspend fun getTransactions(
        startDate: String? = null,
        endDate: String? = null,
        category: String? = null,
        type: String? = null,
        page: Int? = 1,
        limit: Int? = 50,
        forceRefresh: Boolean = false,
    ): AuthResult<List<TransactionDto>> {
        val result = read<List<TransactionDto>>("transactions:$startDate:$endDate:$category:$type:$page:$limit", forceRefresh) {
            api.getTransactions(getAuthHeader(), startDate, endDate, category, type, page, limit)
        }
        if (result !is AuthResult.Success) return result
        return AuthResult.Success(overlayPending(result.data, firstPage = (page ?: 1) == 1) { tx ->
            (type == null || tx.type == type) && (category == null || tx.category == category) &&
                (startDate == null || tx.createdAt >= startDate) && (endDate == null || tx.createdAt <= endDate)
        })
    }

    /**
     * Applies not-yet-synced outbox writes on top of a server list: queued creates are added on the
     * first page (when they [matches] the query), queued updates replace fields, queued deletes disappear.
     */
    private suspend fun overlayPending(list: List<TransactionDto>, firstPage: Boolean, matches: (TransactionDto) -> Boolean): List<TransactionDto> {
        val ops = dao.pending(userId())
        if (ops.isEmpty()) return list
        val rows = list.toMutableList()
        val shownCaptures = mutableListOf<CaptureRequest>()
        for (op in ops) {
            val i = rows.indexOfFirst { it.id == op.transactionId }
            when (op.kind) {
                "create" -> if (i < 0 && firstPage) {
                    val tx = localDto(op.transactionId, gson.fromJson(op.body, CreateTransactionRequest::class.java))
                    if (matches(tx)) rows.add(0, tx)
                }
                "capture" -> if (i < 0 && firstPage) {
                    val q = gson.fromJson(op.body, QueuedCapture::class.java)
                    val r = q.request
                    // The server makes the final call; offline only the obvious SMS + email twin is hidden.
                    val twin = shownCaptures.any { s ->
                        (r.refNo != null && s.refNo == r.refNo) ||
                            (s.amount == r.amount && s.type == r.type && abs(Instant.parse(s.receivedAt).toEpochMilli() - Instant.parse(r.receivedAt).toEpochMilli()) < 2 * 60_000)
                    }
                    if (q.addsTransaction && r.amount != null && r.type != null && r.ignoreReason == null && !twin) {
                        shownCaptures += r
                        val tx = localDto(r.id, CreateTransactionRequest(r.amount, r.type, r.payee ?: "Unknown", "Other", r.packageName, null, r.receivedAt))
                        if (matches(tx)) rows.add(0, tx)
                    }
                }
                "update" -> if (i >= 0) {
                    val u = gson.fromJson(op.body, UpdateTransactionRequest::class.java)
                    rows[i] = rows[i].copy(
                        amount = u.amount?.let { money(it) } ?: rows[i].amount,
                        type = u.type ?: rows[i].type,
                        merchant = u.merchant ?: rows[i].merchant,
                        category = u.category ?: rows[i].category,
                        sourceApp = u.sourceApp ?: rows[i].sourceApp,
                        note = u.note ?: rows[i].note,
                        createdAt = u.transactionDate ?: rows[i].createdAt,
                    )
                }
                "delete" -> if (i >= 0) rows.removeAt(i)
            }
        }
        return rows
    }

    private fun money(amount: Double) = String.format(Locale.US, "%.2f", amount)

    private suspend fun localDto(id: String, r: CreateTransactionRequest) = TransactionDto(
        id = id, userId = userId(), amount = money(r.amount), type = r.type, merchant = r.merchant,
        category = r.category, sourceApp = r.sourceApp, note = r.note,
        createdAt = r.transactionDate ?: Instant.now().toString(), updatedAt = null,
    )

    /** Returns the user's all-time net balance (credits minus debits) across all transactions. */
    suspend fun getAllTimeBalance(forceRefresh: Boolean = false): AuthResult<Double> {
        val key = "transactions:alltime"
        if (!forceRefresh) cached<Double>(key)?.let { return AuthResult.Success(it) }
        return when (val result = getTransactions(limit = 2000, forceRefresh = forceRefresh)) {
            is AuthResult.Success -> {
                val balance = result.data.sumOf { tx ->
                    val amount = tx.amount.toDoubleOrNull() ?: 0.0
                    if (tx.type == "credit") amount else -amount
                }
                cache[key] = balance
                AuthResult.Success(balance)
            }
            is AuthResult.Error -> result
        }
    }

    /** No connection, a timeout or a 5xx: the write is kept in the outbox and retried, not lost. */
    private fun shouldQueue(e: Exception?, code: Int = 0) = e is IOException || code >= 500

    private suspend fun enqueue(kind: String, transactionId: String, body: Any?) {
        dao.enqueue(OutboxOp(userId = userId(), kind = kind, transactionId = transactionId, body = gson.toJson(body), createdAt = System.currentTimeMillis()))
        cacheInvalidate("transactions", "dashboard", "holds", "plan")
        PendingCaptureRetryWorker.schedule(appContext)
    }

    /**
     * [id] is made on the phone so a retried create is never stored twice (the server ignores a
     * repeat of the same id). Pass a stable id when the same entry might be sent more than once.
     */
    suspend fun createTransaction(
        amount: Double,
        type: String,
        merchant: String,
        category: String,
        sourceApp: String? = null,
        note: String? = null,
        transactionDate: String? = null,
        id: String = UUID.randomUUID().toString(),
    ): AuthResult<TransactionDto> {
        val request = CreateTransactionRequest(amount, type, merchant, category, sourceApp, note, transactionDate, id)
        // A queued entry keeps the time it was made, not the time it finally syncs.
        val queued = request.copy(transactionDate = transactionDate ?: Instant.now().toString())
        return try {
            val response = api.createTransaction(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("transactions", "dashboard", "plan")
                AuthResult.Success(response.body()!!.data)
            } else if (shouldQueue(null, response.code())) {
                enqueue("create", id, queued)
                AuthResult.Success(localDto(id, queued))
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            if (!shouldQueue(e)) return AuthResult.Error(e.toUserMessage())
            enqueue("create", id, queued)
            AuthResult.Success(localDto(id, queued))
        }
    }

    suspend fun updateTransaction(
        id: String,
        amount: Double? = null,
        type: String? = null,
        merchant: String? = null,
        category: String? = null,
        sourceApp: String? = null,
        note: String? = null,
        transactionDate: String? = null,
    ): AuthResult<TransactionDto> {
        val request = UpdateTransactionRequest(amount, type, merchant, category, sourceApp, note, transactionDate)
        suspend fun queue(): AuthResult<TransactionDto> {
            enqueue("update", id, request)
            return AuthResult.Success(localDto(id, CreateTransactionRequest(amount ?: 0.0, type.orEmpty(), merchant.orEmpty(), category.orEmpty(), sourceApp, note, transactionDate)))
        }
        // Earlier writes for this transaction are still waiting: stay behind them.
        if (dao.pendingFor(userId(), id) > 0) return queue()
        return try {
            val response = api.updateTransaction(getAuthHeader(), id, request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("transactions", "dashboard", "plan")
                AuthResult.Success(response.body()!!.data)
            } else if (shouldQueue(null, response.code())) {
                queue()
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            if (shouldQueue(e)) queue() else AuthResult.Error(e.toUserMessage())
        }
    }

    /** Moves it to the Trash. Returns the ids of holds that went with it (their reminders must stop). */
    suspend fun deleteTransaction(id: String): AuthResult<List<String>> {
        // Offline: the hold ids are not known yet; the server moves the holds when the delete syncs.
        suspend fun queue(): AuthResult<List<String>> = AuthResult.Success(emptyList<String>()).also { enqueue("delete", id, null) }
        if (dao.pendingFor(userId(), id) > 0) return queue()
        return try {
            val response = api.deleteTransaction(getAuthHeader(), id)
            if (response.isSuccessful) {
                // "holds" too — the backend moves the linked holds to the Trash with it.
                cacheInvalidate("transactions", "dashboard", "holds", "plan")
                AuthResult.Success(response.body()?.data?.holdIds.orEmpty())
            } else if (shouldQueue(null, response.code())) {
                queue()
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            if (shouldQueue(e)) queue() else AuthResult.Error(e.toUserMessage())
        }
    }

    /**
     * Replays this user's queued writes in order. Returns true when nothing is left to retry. A write
     * the server refuses for good (4xx other than 401/408/429, e.g. already deleted) is dropped so it
     * cannot block the ones behind it.
     */
    suspend fun syncOutbox(): Boolean {
        val uid = userId()
        if (uid.isBlank()) return true // signed out: the ops wait until that user signs in again
        val ops = dao.pending(uid)
        if (ops.isEmpty()) return true
        val auth = getAuthHeader()
        try {
            for (op in ops) {
                val response = when (op.kind) {
                    "create" -> api.createTransaction(auth, gson.fromJson(op.body, CreateTransactionRequest::class.java))
                    "update" -> api.updateTransaction(auth, op.transactionId, gson.fromJson(op.body, UpdateTransactionRequest::class.java))
                    "capture" -> api.capture(auth, gson.fromJson(op.body, QueuedCapture::class.java).request)
                    else -> api.deleteTransaction(auth, op.transactionId)
                }
                val code = response.code()
                if (!response.isSuccessful && (code >= 500 || code == 401 || code == 408 || code == 429)) return false
                dao.remove(op.seq)
                // A capture that waited offline gets its notification (added / "same payment?") now.
                val captured = response.body() as? SuccessResponse<*>
                (captured?.data as? CaptureResultDto)?.let { CaptureNotifier.onResult(appContext, this, sessionDataStore, it) }
            }
        } catch (e: IOException) {
            return false
        } finally {
            cacheInvalidate("transactions", "dashboard", "holds", "plan", "capture")
        }
        return true
    }

    // ── Smart capture ─────────────────────────────────────────────────────────

    /**
     * Sends one bank message to the server, which adds it, marks it a duplicate or asks the user. Offline
     * (or on a 5xx) it waits in the outbox and comes back with status "pending"; a 4xx (e.g. an OTP the
     * server refuses to store) is an error and the message is dropped. [addsTransaction] = the user's
     * "add automatically" setting, so a queued capture shows in lists only when it will become a payment.
     */
    suspend fun capture(request: CaptureRequest, addsTransaction: Boolean): AuthResult<CaptureResultDto> {
        val pending = CaptureResultDto(request.toPendingMessage())
        suspend fun queue(): AuthResult<CaptureResultDto> {
            enqueue("capture", request.id, QueuedCapture(request, addsTransaction))
            return AuthResult.Success(pending)
        }
        // The same notification posted again while the first copy still waits: keep one.
        if (dao.pendingFor(userId(), request.id) > 0) return AuthResult.Success(pending)
        return try {
            val response = api.capture(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("transactions", "dashboard", "plan", "capture")
                AuthResult.Success(response.body()!!.data)
            } else if (shouldQueue(null, response.code())) {
                queue()
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            if (shouldQueue(e)) queue() else AuthResult.Error(e.toUserMessage())
        }
    }

    private fun CaptureRequest.toPendingMessage() = CapturedMessageDto(
        id = id, source = source, sender = sender, packageName = packageName, body = body, receivedAt = receivedAt,
        amount = amount?.let { money(it) }, type = type, payee = payee, refNo = refNo, last4 = last4,
        balance = balance?.let { money(it) }, status = "pending", reason = ignoreReason,
    )

    /** Captured messages, newest first; ones still waiting to sync are listed on top as "pending". */
    suspend fun getCapturedMessages(status: String? = null, transactionId: String? = null, forceRefresh: Boolean = false): AuthResult<List<CapturedMessageDto>> {
        val result = read<List<CapturedMessageDto>>("capture:$status:$transactionId", forceRefresh) {
            api.getCapturedMessages(getAuthHeader(), status, transactionId, 1)
        }
        if (result !is AuthResult.Success || status != null) return result
        val queued = dao.pending(userId()).filter { it.kind == "capture" }
            .map { gson.fromJson(it.body, QueuedCapture::class.java).request }
            .filter { transactionId == null || it.id == transactionId }
            .filter { r -> result.data.none { it.id == r.id } }
            .map { it.toPendingMessage() }
        return AuthResult.Success(queued.reversed() + result.data)
    }

    /** add | same | different | ignore. Needs the network: the answer decides what gets stored. */
    suspend fun actOnCapturedMessage(id: String, action: String): AuthResult<CaptureResultDto> =
        captureCall { api.actOnCapturedMessage(getAuthHeader(), id, CaptureActionRequest(action)) }

    suspend fun deleteCapturedMessage(id: String): AuthResult<Unit> =
        captureCall { api.deleteCapturedMessage(getAuthHeader(), id) }.let { if (it is AuthResult.Success) AuthResult.Success(Unit) else it as AuthResult.Error }

    suspend fun deleteAllCapturedMessages(): AuthResult<Unit> =
        captureCall { api.deleteAllCapturedMessages(getAuthHeader()) }.let { if (it is AuthResult.Success) AuthResult.Success(Unit) else it as AuthResult.Error }

    private suspend fun <T> captureCall(block: suspend () -> Response<SuccessResponse<T>>): AuthResult<T> = try {
        val response = block()
        if (response.isSuccessful && response.body() != null) {
            cacheInvalidate("capture", "transactions", "dashboard", "plan")
            AuthResult.Success(response.body()!!.data)
        } else {
            AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
        }
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    suspend fun restoreTransaction(id: String): AuthResult<RestoredTransactionDto> {
        return try {
            val response = api.restoreTransaction(getAuthHeader(), id)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                cacheInvalidate("transactions", "dashboard", "holds", "plan")
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun restoreHold(id: String): AuthResult<HoldDto> {
        return try {
            val response = api.restoreHold(getAuthHeader(), id)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                cacheInvalidate("holds")
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    /** Never cached: the Trash changes from several screens. */
    suspend fun getTrash(cursor: String?): AuthResult<TrashPageDto> {
        return try {
            val response = api.getTrash(getAuthHeader(), cursor)
            val data = response.body()?.data
            if (response.isSuccessful && data != null) AuthResult.Success(data)
            else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun deleteForever(kind: String, id: String): AuthResult<Unit> {
        return try {
            val response = api.deleteForever(getAuthHeader(), kind, id)
            if (response.isSuccessful) AuthResult.Success(Unit)
            else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun emptyTrash(): AuthResult<Int> {
        return try {
            val response = api.emptyTrash(getAuthHeader())
            if (response.isSuccessful) AuthResult.Success(response.body()?.data?.deleted ?: 0)
            else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Holds ─────────────────────────────────────────────────────────────────

    suspend fun createHold(
        transactionId: String,
        direction: String,
        personName: String,
        amount: Double,
        expectedReturnDate: String,
    ): AuthResult<HoldDto> {
        return try {
            val request = CreateHoldRequest(transactionId, direction, personName, amount, expectedReturnDate)
            val response = api.createHold(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("holds")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun getHolds(status: String? = null, direction: String? = null, forceRefresh: Boolean = false): AuthResult<List<HoldDto>> =
        read("holds:$status:$direction", forceRefresh) { api.getHolds(getAuthHeader(), status, direction) }

    suspend fun updateHold(
        id: String,
        status: String? = null,
        personName: String? = null,
        expectedReturnDate: String? = null,
    ): AuthResult<HoldDto> {
        return try {
            val request = UpdateHoldRequest(personName, expectedReturnDate, status)
            val response = api.updateHold(getAuthHeader(), id, request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("holds")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    /** Moves the hold to the Trash. */
    suspend fun deleteHold(id: String): AuthResult<Unit> {
        return try {
            val response = api.deleteHold(getAuthHeader(), id)
            if (response.isSuccessful) {
                cacheInvalidate("holds")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Planify ───────────────────────────────────────────────────────────────

    /** The month's plan with live numbers. [today] (YYYY-MM-DD, the user's own calendar) drives pace and days left. */
    suspend fun getPlan(month: String, today: String = java.time.LocalDate.now().toString(), forceRefresh: Boolean = false): AuthResult<PlanViewDto> =
        read("plan:$month:$today", forceRefresh) { api.getPlan(getAuthHeader(), month, today) }

    suspend fun savePlan(month: String, request: SavePlanRequest, today: String = java.time.LocalDate.now().toString()): AuthResult<PlanViewDto> =
        planCall { api.savePlan(getAuthHeader(), month, today, request) }.also { if (it is AuthResult.Success) cacheInvalidate("plan", "budgets", "dashboard") }

    suspend fun deletePlan(month: String, today: String = java.time.LocalDate.now().toString()): AuthResult<PlanViewDto> =
        planCall { api.deletePlan(getAuthHeader(), month, today) }.also { if (it is AuthResult.Success) cacheInvalidate("plan", "budgets", "dashboard") }

    suspend fun movePlanMoney(month: String, from: String, to: String, amount: Double, today: String = java.time.LocalDate.now().toString()): AuthResult<PlanViewDto> =
        planCall { api.movePlanMoney(getAuthHeader(), month, today, MoveMoneyRequest(from, to, amount)) }.also { if (it is AuthResult.Success) cacheInvalidate("plan", "budgets", "dashboard") }

    /** A first draft from the last 3 months. Nothing is saved on the server. */
    suspend fun getPlanSuggestions(month: String): AuthResult<SuggestionDto> = try {
        val response = api.getPlanSuggestions(getAuthHeader(), month)
        if (response.isSuccessful && response.body() != null) AuthResult.Success(response.body()!!.data)
        else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    /** Emails the user's own data to their account address (server side). */
    suspend fun emailExport(request: ExportEmailRequest): AuthResult<ExportEmailDto> = try {
        val response = api.emailExport(getAuthHeader(), request)
        if (response.isSuccessful && response.body() != null) AuthResult.Success(response.body()!!.data)
        else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    /** An AI-written follow-up draft for a hold, or source "none". Nothing is sent. */
    suspend fun holdMessage(request: HoldMessageRequest): AuthResult<HoldMessageDto> = try {
        val response = api.holdMessage(getAuthHeader(), request)
        if (response.isSuccessful && response.body() != null) AuthResult.Success(response.body()!!.data)
        else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    /** The user's Yes/No (or "forget") on whether some spending belongs in a bucket. Returns the refreshed plan. */
    suspend fun answerPlanMatch(month: String, request: MatchAnswerRequest, today: String = java.time.LocalDate.now().toString()): AuthResult<PlanViewDto> =
        planCall { api.answerPlanMatch(getAuthHeader(), month, today, request) }.also { if (it is AuthResult.Success) cacheInvalidate("plan", "budgets", "dashboard") }

    /** One turn of the AI planning guide. */
    suspend fun planGuide(request: GuideTurnRequest): AuthResult<GuideTurnDto> = try {
        val response = api.planGuide(getAuthHeader(), request)
        if (response.isSuccessful && response.body() != null) AuthResult.Success(response.body()!!.data)
        else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    private suspend fun planCall(block: suspend () -> retrofit2.Response<SuccessResponse<PlanViewDto>>): AuthResult<PlanViewDto> = try {
        val response = block()
        if (response.isSuccessful && response.body() != null) AuthResult.Success(response.body()!!.data)
        else AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
    } catch (e: Exception) {
        AuthResult.Error(e.toUserMessage())
    }

    // ── Budgets ───────────────────────────────────────────────────────────────

    suspend fun getBudgets(month: String? = null, category: String? = null, forceRefresh: Boolean = false): AuthResult<List<BudgetDto>> =
        read("budgets:$month:$category", forceRefresh) { api.getBudgets(getAuthHeader(), month, category) }

    suspend fun createBudget(category: String, month: String, limitAmount: Double): AuthResult<BudgetDto> {
        return try {
            val request = CreateBudgetRequest(category, month, limitAmount)
            val response = api.createBudget(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("budgets", "dashboard", "plan")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun updateBudget(id: String, limitAmount: Double): AuthResult<BudgetDto> {
        return try {
            val request = UpdateBudgetRequest(limitAmount = limitAmount)
            val response = api.updateBudget(getAuthHeader(), id, request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("budgets", "dashboard", "plan")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun deleteBudget(id: String): AuthResult<Unit> {
        return try {
            val response = api.deleteBudget(getAuthHeader(), id)
            if (response.isSuccessful) {
                cacheInvalidate("budgets", "dashboard", "plan")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    suspend fun getSettings(forceRefresh: Boolean = false): AuthResult<SettingsDto> =
        read("settings", forceRefresh) { api.getSettings(getAuthHeader()) }

    suspend fun updateSettings(request: UpdateSettingsRequest): AuthResult<SettingsDto> {
        return try {
            val response = api.updateSettings(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!.data
                cache["settings"] = data
                AuthResult.Success(data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    /** Permanently deletes the account on the server (all data cascades). */
    suspend fun deleteAccount(): AuthResult<Unit> {
        return try {
            val response = api.deleteAccount(getAuthHeader())
            if (response.isSuccessful) {
                clearCache()
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Categories ────────────────────────────────────────────────────────────

    suspend fun getCategories(forceRefresh: Boolean = false): AuthResult<List<CategoryDto>> =
        read("categories", forceRefresh) { api.getCategories(getAuthHeader()) }

    suspend fun createCategory(keyword: String, category: String): AuthResult<CategoryDto> {
        return try {
            val request = CreateCategoryRequest(keyword = keyword, category = category)
            val response = api.createCategory(getAuthHeader(), request)
            if (response.isSuccessful && response.body() != null) {
                cacheInvalidate("categories")
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun deleteCategory(id: String): AuthResult<Unit> {
        return try {
            val response = api.deleteCategory(getAuthHeader(), id)
            if (response.isSuccessful) {
                cacheInvalidate("categories")
                AuthResult.Success(Unit)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    suspend fun suggestCategory(merchant: String): AuthResult<CategorySuggestResponse> {
        return try {
            val response = api.suggestCategory(getAuthHeader(), CategorySuggestRequest(merchant))
            if (response.isSuccessful && response.body() != null) {
                AuthResult.Success(response.body()!!.data)
            } else {
                AuthResult.Error(parseErrorMessage(response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            AuthResult.Error(e.toUserMessage())
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // ── Bills ─────────────────────────────────────────────────────────────────

    private suspend fun <T> billCall(block: suspend () -> retrofit2.Response<SuccessResponse<T>>): BillCall<T> = try {
        val r = block()
        val data = r.body()?.data
        if (r.isSuccessful && data != null) BillCall.Ok(data)
        else BillCall.Fail(parseErrorMessage(r.errorBody()?.string()), BillCall.isPermanent(r.code()), r.code())
    } catch (e: Exception) {
        BillCall.Fail(e.toUserMessage(), permanent = false)
    }

    suspend fun listBills(txId: String): AuthResult<List<BillDto>> = when (val r = billCall { api.listBills(getAuthHeader(), txId) }) {
        is BillCall.Ok -> AuthResult.Success(r.data)
        is BillCall.Fail -> AuthResult.Error(r.message)
    }

    suspend fun reserveBill(txId: String, req: ReserveBillRequest) = billCall { api.reserveBill(getAuthHeader(), txId, req) }
    suspend fun signBill(id: String) = billCall { api.signBill(getAuthHeader(), id) }

    suspend fun confirmBill(id: String, req: ConfirmBillRequest): BillCall<BillDto> =
        billCall { api.confirmBill(getAuthHeader(), id, req) }.also { if (it is BillCall.Ok) cacheInvalidate("transactions") }

    suspend fun deleteBill(id: String): AuthResult<Unit> = try {
        val r = api.deleteBill(getAuthHeader(), id)
        if (r.isSuccessful) { cacheInvalidate("transactions"); AuthResult.Success(Unit) } else AuthResult.Error(parseErrorMessage(r.errorBody()?.string()))
    } catch (e: Exception) { AuthResult.Error(e.toUserMessage()) }

    suspend fun restoreBill(id: String): AuthResult<BillDto> = when (val r = billCall { api.restoreBill(getAuthHeader(), id) }) {
        is BillCall.Ok -> { cacheInvalidate("transactions"); AuthResult.Success(r.data) }
        is BillCall.Fail -> AuthResult.Error(r.message)
    }

    suspend fun billUsage(): AuthResult<BillUsageDto> = when (val r = billCall { api.billUsage(getAuthHeader()) }) {
        is BillCall.Ok -> AuthResult.Success(r.data)
        is BillCall.Fail -> AuthResult.Error(r.message)
    }

    suspend fun getTransaction(id: String): AuthResult<TransactionDto> = when (val r = billCall { api.getTransactionById(getAuthHeader(), id) }) {
        is BillCall.Ok -> AuthResult.Success(r.data)
        is BillCall.Fail -> AuthResult.Error(r.message)
    }

    suspend fun billRead(txId: String): AuthResult<BillReadStatusDto> = when (val r = billCall { api.getBillRead(getAuthHeader(), txId) }) {
        is BillCall.Ok -> AuthResult.Success(r.data); is BillCall.Fail -> AuthResult.Error(r.message)
    }
    suspend fun runBillRead(txId: String, req: RunBillReadRequest): AuthResult<BillReadStatusDto> = when (val r = billCall { api.runBillRead(getAuthHeader(), txId, req) }) {
        is BillCall.Ok -> AuthResult.Success(r.data); is BillCall.Fail -> AuthResult.Error(r.message)
    }
    /** AI switched off: the server stops bill reads still waiting in its queue. Best effort. */
    suspend fun stopBillReads() { runCatching { api.stopBillReads(getAuthHeader()) } }

    /** Bills to the user's own account email (the user tapped Send on the email card). */
    suspend fun emailBills(ids: List<String>) = billCall { api.emailBills(getAuthHeader(), EmailBillsRequest(ids)) }

    suspend fun readBillImages(req: ReadBillImagesRequest): AuthResult<BillImagesReadDto> = when (val r = billCall { api.readBillImages(getAuthHeader(), req) }) {
        is BillCall.Ok -> AuthResult.Success(r.data); is BillCall.Fail -> AuthResult.Error(r.message)
    }

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return tr(R.string.an_unexpected_error_occurred)
        return try {
            val json = gson.fromJson(errorBody, JsonObject::class.java)
            val errorObj = json.getAsJsonObject("error")
            ServerMessages.localize(
                message = errorObj?.get("message")?.asString ?: json.get("message")?.asString ?: json.get("error")?.takeIf { it.isJsonPrimitive }?.asString,
                code = errorObj?.get("code")?.asString ?: json.get("code")?.asString,
            )
        } catch (e: Exception) {
            tr(R.string.an_unexpected_error_occurred)
        }
    }

    private fun Exception.toUserMessage(): String = when {
        message?.contains("Unable to resolve host", ignoreCase = true) == true ->
            tr(R.string.no_internet_connection_please_check_your)
        message?.contains("timeout", ignoreCase = true) == true ->
            tr(R.string.request_timed_out_please_try_again)
        message?.contains("Connection refused", ignoreCase = true) == true ->
            tr(R.string.cannot_reach_the_server_please_try)
        else -> message ?: tr(R.string.an_unexpected_error_occurred)
    }
}
