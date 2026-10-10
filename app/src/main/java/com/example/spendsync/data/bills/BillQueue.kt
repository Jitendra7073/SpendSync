package com.example.spendsync.data.bills

import android.content.Context
import com.example.spendsync.data.remote.model.BillReservationDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class Uploaded(
    val publicId: String, val version: Long, val signature: String, val format: String,
    val bytes: Long, val width: Int?, val height: Int?, val pages: Int?,
)

/** One bill page waiting to reach Cloudinary + our API. The file is a private copy in filesDir/bills. */
data class QueuedBill(
    val clientKey: String,
    val txId: String,
    val localPath: String,
    val mime: String,
    val bytes: Long,
    val position: Int?,
    val replaces: String?,
    val billId: String? = null,
    val uploadUrl: String? = null,
    val params: Map<String, String>? = null,
    val signedAt: Long = 0,
    val uploaded: Uploaded? = null,
    val state: State = State.Waiting,
    val error: String? = null,
) {
    enum class State { Waiting, Uploading, Confirming, Failed }
}

/** Pure state changes, unit-tested. */
object BillQueueRules {
    /** Cloudinary accepts a signature for 1 hour; renew with margin. */
    const val RESIGN_AFTER_MS = 50 * 60_000L

    fun needsSign(item: QueuedBill, now: Long): Boolean =
        item.uploaded == null && (item.billId == null || item.params == null || item.uploadUrl == null || now - item.signedAt >= RESIGN_AFTER_MS)

    /** null = the server already has it ready (a retry after success): drop the item. */
    fun afterReserve(item: QueuedBill, r: BillReservationDto, now: Long): QueuedBill? =
        if (r.status == "ready") null
        else item.copy(billId = r.billId, uploadUrl = r.uploadUrl, params = r.params, signedAt = now, state = QueuedBill.State.Waiting, error = null)

    fun afterUpload(item: QueuedBill, u: Uploaded): QueuedBill = item.copy(uploaded = u, state = QueuedBill.State.Confirming, error = null)

    fun failed(item: QueuedBill, message: String): QueuedBill = item.copy(state = QueuedBill.State.Failed, error = message)
}

/** Persisted queue (a small JSON file next to the copies). Survives app restarts; cleared on sign-out. */
object BillQueue {
    private val gson = Gson()
    private val lock = Mutex()
    private val _items = MutableStateFlow<List<QueuedBill>>(emptyList())
    val items: StateFlow<List<QueuedBill>> = _items
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress
    @Volatile private var loaded = false

    fun dir(context: Context) = File(context.filesDir, "bills").apply { mkdirs() }
    private fun file(context: Context) = File(dir(context), "queue.json")

    suspend fun init(context: Context) = lock.withLock { loadLocked(context) }

    private suspend fun loadLocked(context: Context) {
        if (loaded) return
        _items.value = withContext(Dispatchers.IO) {
            runCatching {
                val f = file(context)
                if (f.exists()) gson.fromJson<List<QueuedBill>>(f.readText(), object : TypeToken<List<QueuedBill>>() {}.type) else emptyList()
            }.getOrDefault(emptyList()).orEmpty()
        }
        loaded = true
    }

    private suspend fun write(context: Context, list: List<QueuedBill>) {
        _items.value = list
        withContext(Dispatchers.IO) {
            val tmp = File(dir(context), "queue.json.tmp")
            tmp.writeText(gson.toJson(list))
            tmp.renameTo(file(context)) // atomic replace on the same filesystem
        }
    }

    suspend fun add(context: Context, item: QueuedBill) = lock.withLock { loadLocked(context); write(context, _items.value + item) }
    suspend fun update(context: Context, item: QueuedBill) = lock.withLock {
        loadLocked(context); write(context, _items.value.map { if (it.clientKey == item.clientKey) item else it })
    }
    suspend fun remove(context: Context, clientKey: String) = lock.withLock {
        loadLocked(context)
        _items.value.find { it.clientKey == clientKey }?.let { File(it.localPath).delete() }
        write(context, _items.value.filterNot { it.clientKey == clientKey })
        _progress.value = _progress.value - clientKey
    }
    fun forTx(txId: String): Flow<List<QueuedBill>> = items.map { all -> all.filter { it.txId == txId } }
    fun setProgress(clientKey: String, p: Float) { _progress.value = _progress.value + (clientKey to p) }

    suspend fun clear(context: Context) = lock.withLock {
        withContext(Dispatchers.IO) { dir(context).deleteRecursively() }
        _items.value = emptyList(); _progress.value = emptyMap(); loaded = true
    }

    /** Queues prepared files for a saved transaction and starts the worker. */
    suspend fun enqueuePrepared(context: Context, txId: String, prepared: List<BillImages.Prepared>, replaces: String? = null, position: Int? = null) {
        prepared.forEach {
            add(context, QueuedBill(UUID.randomUUID().toString(), txId, it.file.absolutePath, it.mime, it.bytes, position, replaces))
        }
        BillUploadWorker.schedule(context)
    }
}
