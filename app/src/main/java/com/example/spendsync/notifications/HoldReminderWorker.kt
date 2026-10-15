package com.example.spendsync.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class HoldReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val holdId = inputData.getString(KEY_HOLD_ID) ?: return Result.failure()
        val personName = inputData.getString(KEY_PERSON_NAME) ?: return Result.failure()
        val amount = inputData.getDouble(KEY_AMOUNT, 0.0)
        val direction = inputData.getString(KEY_DIRECTION) ?: return Result.failure()

        HoldReminderNotifier.postReminder(applicationContext, holdId, personName, amount, direction)
        return Result.success()
    }

    companion object {
        private const val KEY_HOLD_ID = "hold_id"
        private const val KEY_PERSON_NAME = "person_name"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_DIRECTION = "direction"

        private fun workName(holdId: String) = "hold_reminder_$holdId"

        /**
         * One work item per hold (unlike PendingCaptureRetryWorker's single
         * global queue-flush worker) — REPLACE, not KEEP, since re-scheduling
         * the same hold (e.g. the user edits its return date) must supersede
         * any previously-queued reminder for it, not stack a second one.
         */
        fun schedule(
            context: Context,
            holdId: String,
            personName: String,
            amount: Double,
            direction: String,
            expectedReturnDate: LocalDate,
        ) {
            val delay = computeReminderDelayMillis(expectedReturnDate, System.currentTimeMillis())
            val data = workDataOf(
                KEY_HOLD_ID to holdId,
                KEY_PERSON_NAME to personName,
                KEY_AMOUNT to amount,
                KEY_DIRECTION to direction,
            )
            val request = OneTimeWorkRequestBuilder<HoldReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName(holdId), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, holdId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(holdId))
        }

        /** Re-arms the reminder of a hold that came back from the Trash (pending only). */
        fun scheduleFor(context: Context, hold: com.example.spendsync.data.remote.model.HoldDto) {
            if (hold.status != "pending") return
            val due = runCatching { java.time.ZonedDateTime.parse(hold.expectedReturnDate).toLocalDate() }.getOrNull() ?: return
            schedule(context, hold.id, hold.personName, hold.amount.toDoubleOrNull() ?: 0.0, hold.direction, due)
        }
    }
}
