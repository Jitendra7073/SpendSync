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
import java.util.concurrent.TimeUnit

class PendingCaptureRetryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionDataStore = SessionDataStore(applicationContext)
        val financeRepository = FinanceRepository(sessionDataStore)
        val pending = sessionDataStore.pendingCaptures.firstOrNull().orEmpty()
        if (pending.isEmpty()) return Result.success()

        var anyFailed = false
        for (capture in pending) {
            val type = if (capture.direction == TransactionDirection.DEBIT) "debit" else "credit"
            when (val result = financeRepository.createTransaction(
                amount = capture.amount,
                type = type,
                merchant = capture.payee ?: "Unknown",
                category = "Other",
                sourceApp = capture.sourceApp,
            )) {
                is AuthResult.Success -> {
                    sessionDataStore.removePendingCapture(capture)
                    TransactionCaptureNotifier.postDescriptionRequest(applicationContext, result.data)
                }
                is AuthResult.Error -> anyFailed = true
            }
        }
        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "pending_capture_retry"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<PendingCaptureRetryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
