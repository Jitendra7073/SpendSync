package com.example.spendsync.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.spendsync.R
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.planify.PlanDailyCap
import com.example.spendsync.data.planify.PlanMath
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.utils.formatInr
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * One evening notification (about 8 pm) with what is safe to spend today and whether any bucket needs attention;
 * on the last day of the month it becomes a wrap-up. Off unless the user turns on "Daily summary".
 * It counts toward Planify's 3-notifications-a-day cap, shows no amounts while amount hiding is on, and stays quiet
 * when there is no plan, no session or notifications are not allowed.
 */
class PlanDailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SessionDataStore(applicationContext)
        if (store.sessionToken.first().isNullOrBlank()) return Result.success()
        if (!store.planifySettings.first().daily) return Result.success()

        val today = LocalDate.now()
        val todayKey = today.toString()
        val capRaw = store.planDailyCountRaw()
        if (PlanDailyCap.countFor(capRaw, todayKey) >= PlanDailyCap.LIMIT) return Result.success()

        val finance = FinanceRepository(store)
        val plan = (finance.getPlan(PlanMath.monthKey(today), todayKey, forceRefresh = true) as? AuthResult.Success)?.data ?: return Result.retry()
        if (!plan.exists) return Result.success()

        val s = plan.status
        val showAmounts = !store.amountMaskingEnabled.first()
        fun money(v: Double) = formatInr(v)
        val needAttention = s.counts.close + s.counts.over
        val lastDay = s.daysLeft <= 1 && today.dayOfMonth == today.lengthOfMonth()

        val (title, body) = if (lastDay) {
            tr(R.string.pl_n_wrap_title) to
                if (showAmounts) tr(R.string.pl_n_wrap_body, money(s.spentInPlan), money(s.planned)) else tr(R.string.pl_n_wrap_hidden)
        } else {
            tr(R.string.pl_n_daily_title) to buildString {
                append(if (showAmounts) tr(R.string.pl_n_daily_safe, money(s.safeToSpendToday)) else tr(R.string.pl_n_daily_safe_hidden))
                if (needAttention > 0) append(" ").append(tr(R.string.pl_n_daily_watch, needAttention))
            }
        }
        if (PlanNotifier.post(applicationContext, "plan_daily", title, body, PlanifyLinks.OPEN)) {
            store.savePlanDailyCount(PlanDailyCap.bump(capRaw, todayKey))
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "planify_daily_summary"
        private val SEND_AT: LocalTime = LocalTime.of(20, 0)

        /** Starts or stops the daily job to match the setting. Safe to call as often as needed. */
        fun sync(context: Context, enabled: Boolean, reschedule: Boolean = false) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!enabled) { wm.cancelUniqueWork(WORK_NAME); return }
            val now = LocalDateTime.now()
            var first = now.toLocalDate().atTime(SEND_AT)
            if (!first.isAfter(now)) first = first.plusDays(1)
            val request = PeriodicWorkRequestBuilder<PlanDailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, first).toMinutes(), TimeUnit.MINUTES)
                .build()
            // At app start keep the existing timing; when the user just changed the setting, re-time it.
            wm.enqueueUniquePeriodicWork(WORK_NAME, if (reschedule) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
