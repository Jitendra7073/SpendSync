package com.example.spendsync.data.bills

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.model.ConfirmBillRequest
import com.example.spendsync.data.remote.model.ReserveBillRequest
import com.example.spendsync.data.repository.FinanceRepository
import java.io.File
import java.util.concurrent.TimeUnit

/** Drains [BillQueue]: reserve/sign → upload to Cloudinary → confirm. Transient failures retry with backoff. */
class BillUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        BillQueue.init(ctx)
        val repo = FinanceRepository(SessionDataStore(ctx))
        var retry = false
        for (start in BillQueue.items.value.filter { it.state != QueuedBill.State.Failed }) {
            if (!File(start.localPath).exists()) { BillQueue.update(ctx, BillQueueRules.failed(start, "file missing")); continue }
            retry = retry or !process(ctx, repo, start)
        }
        return if (retry) Result.retry() else Result.success()
    }

    /** false = try again later. */
    private suspend fun process(ctx: Context, repo: FinanceRepository, start: QueuedBill): Boolean {
        var item = start
        repeat(2) { attempt ->
            if (BillQueueRules.needsSign(item, System.currentTimeMillis())) {
                val r = if (item.billId == null) repo.reserveBill(item.txId, ReserveBillRequest(item.clientKey, item.mime, item.bytes, item.position, item.replaces))
                        else repo.signBill(item.billId)
                when (r) {
                    is BillCall.Ok -> {
                        item = BillQueueRules.afterReserve(item, r.data, System.currentTimeMillis())
                            ?: run { BillQueue.remove(ctx, start.clientKey); BillEvents.emit(start.txId); return true }
                        BillQueue.update(ctx, item)
                    }
                    is BillCall.Fail -> { if (r.permanent) BillQueue.update(ctx, BillQueueRules.failed(item, r.message)); return r.permanent }
                }
            }
            if (item.uploaded == null) {
                BillQueue.update(ctx, item.copy(state = QueuedBill.State.Uploading))
                when (val up = CloudinaryUploader.upload(File(item.localPath), item.mime, item.uploadUrl!!, item.params!!) { BillQueue.setProgress(item.clientKey, it) }) {
                    is UploadOutcome.Done -> { item = BillQueueRules.afterUpload(item, up.uploaded); BillQueue.update(ctx, item) }
                    UploadOutcome.Resign -> { item = item.copy(signedAt = 0); if (attempt == 0) return@repeat else { BillQueue.update(ctx, item.copy(state = QueuedBill.State.Waiting)); return false } }
                    is UploadOutcome.Retry -> { BillQueue.update(ctx, item.copy(state = QueuedBill.State.Waiting)); return false }
                    is UploadOutcome.Rejected -> { BillQueue.update(ctx, BillQueueRules.failed(item, up.message)); return true }
                }
            }
            val u = item.uploaded!!
            return when (val c = repo.confirmBill(item.billId!!, ConfirmBillRequest(u.publicId, u.version, u.signature, u.format, u.bytes, u.width, u.height, u.pages))) {
                is BillCall.Ok -> { BillQueue.remove(ctx, item.clientKey); BillEvents.emit(item.txId); true }
                is BillCall.Fail -> { if (c.permanent) BillQueue.update(ctx, BillQueueRules.failed(item, c.message)); c.permanent }
            }
        }
        return false
    }

    companion object {
        private const val WORK_NAME = "bill_uploads"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<BillUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            // APPEND_OR_REPLACE: items added while a run is going get their own run afterwards.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
