package com.example.spendsync.notifications

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
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

class PendingCaptureRetryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionDataStore = SessionDataStore(applicationContext)
        val financeRepository = FinanceRepository.shared(sessionDataStore)
        val pending = sessionDataStore.pendingCaptures.firstOrNull().orEmpty()

        var anyFailed = false
        for (capture in pending) {
            val type = if (capture.direction == TransactionDirection.DEBIT) "debit" else "credit"
            when (val result = financeRepository.createTransaction(
                amount = capture.amount,
                type = type,
                merchant = capture.payee ?: "Unknown",
                category = "Other",
                sourceApp = capture.sourceApp,
                // Booked at the time of the payment, not the time of the retry.
                transactionDate = Instant.ofEpochMilli(capture.capturedAtMillis).toString(),
                // Same capture -> same id, so a retry after a lost response is not stored twice.
                id = UUID.nameUUIDFromBytes("${capture.sourceApp}|${capture.capturedAtMillis}|${capture.amount}|${capture.direction}".toByteArray()).toString(),
            )) {
                is AuthResult.Success -> {
                    sessionDataStore.removePendingCapture(capture)
                    TransactionCaptureNotifier.postDescriptionRequest(applicationContext, result.data)
                    com.example.spendsync.data.planify.PlanAlerts.onTransactionChanged(applicationContext, financeRepository, sessionDataStore, result.data)
                }
                is AuthResult.Error -> anyFailed = true
            }
        }
        // Offline writes (manual entries and captures) waiting in the outbox.
        if (!financeRepository.syncOutbox()) anyFailed = true
        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "pending_capture_retry"

        /** Runs once online; a call while a run is in progress queues one more run after it. */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<PendingCaptureRetryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
