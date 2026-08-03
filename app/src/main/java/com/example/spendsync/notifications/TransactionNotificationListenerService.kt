package com.example.spendsync.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class TransactionNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recentCaptures = RecentCaptures()
    private lateinit var sessionDataStore: SessionDataStore
    private lateinit var financeRepository: FinanceRepository

    override fun onCreate() {
        super.onCreate()
        sessionDataStore = SessionDataStore(applicationContext)
        financeRepository = FinanceRepository(sessionDataStore)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        // Cheap synchronous pre-filter — no text extraction or DataStore reads
        // for the vast majority of notifications, which come from apps we never
        // read. The per-user opt-in check still runs below.
        if (NotificationAppAllowlist.APPS.none { it.packageName == packageName }) return
        val text = extractText(sbn.notification) ?: return

        serviceScope.launch {
            // A notification must never be able to crash the app: swallow
            // parsing/DataStore/network failures rather than letting them reach
            // the uncaught handler.
            runCatching {
                if (sessionDataStore.autoCaptureEnabled.firstOrNull() != true) return@launch
                // No session means the create would 401, which triggers a global
                // sign-out and ejects the user from the foreground UI.
                if (sessionDataStore.sessionToken.firstOrNull().isNullOrBlank()) return@launch
                val enabledPackages = sessionDataStore.autoCapturePackages.firstOrNull().orEmpty()
                if (packageName !in enabledPackages) return@launch

                val parsed = NotificationTransactionParser.parse(text) ?: return@launch
                val now = System.currentTimeMillis()
                // Record before the network call, not after — two notifications for
                // the same payment arrive within ~1s and would both pass the check
                // while the first create is still in flight.
                if (recentCaptures.isDuplicate(parsed.amount, parsed.direction, now)) return@launch
                recentCaptures.record(parsed.amount, parsed.direction, now)

                val type = if (parsed.direction == TransactionDirection.DEBIT) "debit" else "credit"
                when (val result = financeRepository.createTransaction(
                    amount = parsed.amount,
                    type = type,
                    merchant = parsed.payee ?: "Unknown",
                    category = "Other",
                    sourceApp = packageName,
                )) {
                    is AuthResult.Success ->
                        TransactionCaptureNotifier.postDescriptionRequest(applicationContext, result.data)
                    is AuthResult.Error -> {
                        sessionDataStore.addPendingCapture(
                            PendingCapture(parsed.amount, parsed.direction, parsed.payee, packageName, now)
                        )
                        PendingCaptureRetryWorker.schedule(applicationContext)
                    }
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun extractText(notification: Notification): String? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return "$title $body".trim().ifBlank { null }
    }
}
