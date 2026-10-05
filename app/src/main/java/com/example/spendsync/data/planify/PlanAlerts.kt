package com.example.spendsync.data.planify

import android.content.Context
import com.example.spendsync.R
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.planify.PlanMath.Level
import com.example.spendsync.data.remote.model.BucketDto
import com.example.spendsync.data.remote.model.TransactionDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.notifications.PlanNotifier
import com.example.spendsync.notifications.PlanifyLinks
import com.example.spendsync.ui.i18n.categoryLabel
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.utils.formatInr
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Looks at the plan after a transaction was added, captured, edited or removed, and tells the user only what is
 * new: a bucket at 80%, at its limit, over it, or on course to run out early; or a salary that has no plan yet.
 * Rules: each level once per bucket per month ("over" at most once a day), at most [PlanDailyCap.LIMIT] a day,
 * nothing when the user switched alerts off, and no rupee amounts when amount hiding is on.
 * It never throws: a failed alert must not break saving a transaction.
 */
object PlanAlerts {

    suspend fun onTransactionChanged(
        context: Context,
        finance: FinanceRepository,
        store: SessionDataStore,
        tx: TransactionDto? = null,
    ) {
        runCatching { evaluate(context.applicationContext, finance, store, tx) }
    }

    private suspend fun evaluate(context: Context, finance: FinanceRepository, store: SessionDataStore, tx: TransactionDto?) {
        val settings = store.planifySettings.first()
        if (!settings.alerts) return
        val today = LocalDate.now()
        val todayKey = today.toString()
        val month = PlanMath.monthKey(today)
        val showAmounts = !store.amountMaskingEnabled.first()
        fun money(v: Double) = formatInr(v)

        val plan = (finance.getPlan(month, todayKey, forceRefresh = true) as? AuthResult.Success)?.data ?: return

        // A salary arrived and there is no plan yet: offer to plan it.
        if (tx != null && tx.type == "credit" && !plan.exists && !store.salaryPrompted(month)) {
            val amount = tx.amount.toDoubleOrNull() ?: 0.0
            if (PlanMath.looksLikeSalary(tx.type, amount, tx.category, tx.merchant, tx.note, settings.salaryMin.toDouble())) {
                val posted = PlanNotifier.post(
                    context, "plan_salary_$month",
                    if (showAmounts) tr(R.string.pl_n_salary_title, money(amount)) else tr(R.string.pl_n_salary_title_hidden),
                    tr(R.string.pl_n_salary_body),
                    PlanifyLinks.BUILD,
                )
                if (posted) store.markSalaryPrompted(month)
            }
        }
        if (!plan.exists) return

        val memory = PlanAlertMemory.parse(store.planAlertMemoryRaw())
        var capRaw = store.planDailyCountRaw()
        var shown = PlanDailyCap.countFor(capRaw, todayKey)

        for (b in plan.status.buckets.filter { it.kind == "spend" }) {
            val entry = memory.get(month, b.category)
            val d = PlanMath.decideAlert(PlanMath.levelOf(b), entry.level, entry.overDay, todayKey)
            if (d.fire != null) {
                if (shown < PlanDailyCap.LIMIT && post(context, month, b, d.fire, plan.status.daysLeft, showAmounts, ::money)) {
                    shown++
                    capRaw = PlanDailyCap.bump(capRaw, todayKey)
                    memory.set(month, b.category, PlanAlertMemory.Entry(d.notified, d.overDay))
                } // else: today's quota is used up (or notifications are off): leave it unremembered so it can fire later
            } else {
                memory.set(month, b.category, PlanAlertMemory.Entry(d.notified, d.overDay))
            }

            // "Moving fast": once per bucket per month, while it is still inside its limit.
            val paceKey = "pace:${b.category}"
            if (b.paceWarning && b.runsOutOnDay != null && memory.get(month, paceKey).level == Level.None && shown < PlanDailyCap.LIMIT) {
                val posted = PlanNotifier.post(
                    context, "plan_pace_${month}_${b.category}",
                    tr(R.string.pl_n_pace_title, bucketName(b)),
                    tr(R.string.pl_n_pace_body, b.runsOutOnDay),
                    PlanifyLinks.OPEN,
                )
                if (posted) {
                    shown++
                    capRaw = PlanDailyCap.bump(capRaw, todayKey)
                    memory.set(month, paceKey, PlanAlertMemory.Entry(Level.Close, null))
                }
            }
        }

        memory.keepMonths(setOf(month, PlanMath.previousMonth(month)))
        store.savePlanAlertMemory(memory.serialize())
        store.savePlanDailyCount(capRaw)
    }

    private fun bucketName(b: BucketDto) = if (b.name.isBlank() || b.name == b.category) categoryLabel(b.category) else b.name

    private fun post(context: Context, month: String, b: BucketDto, level: Level, daysLeft: Int, showAmounts: Boolean, money: (Double) -> String): Boolean {
        val name = bucketName(b)
        val (title, body) = when (level) {
            Level.Close -> tr(R.string.pl_n_close_title, name, b.percent.toInt()) to
                if (showAmounts) tr(R.string.pl_n_close_body, money(b.remaining), daysLeft) else tr(R.string.pl_n_close_body_hidden)
            Level.Reached -> tr(R.string.pl_n_reached_title, name) to tr(R.string.pl_n_reached_body)
            Level.Over -> tr(R.string.pl_n_over_title, name) to
                if (showAmounts) tr(R.string.pl_n_over_body, money(-b.remaining)) else tr(R.string.pl_n_over_body_hidden)
            Level.None -> return false
        }
        return PlanNotifier.post(context, "plan_${level.name}_${month}_${b.category}", title, body, PlanifyLinks.OPEN)
    }
}
