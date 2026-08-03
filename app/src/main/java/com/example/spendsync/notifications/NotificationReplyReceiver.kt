package com.example.spendsync.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the inline "add a description" reply on a follow-up notification
 * posted by [TransactionCaptureNotifier]. Constructs its own repository the
 * same way [android.app.Application]-less background components do
 * elsewhere in this app — there's no app-wide DI container.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getStringExtra(NotificationCaptureIds.EXTRA_TRANSACTION_ID) ?: return
        val reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationCaptureIds.KEY_TEXT_REPLY)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: return

        val pendingResult = goAsync()
        val sessionDataStore = SessionDataStore(context.applicationContext)
        val financeRepository = FinanceRepository(sessionDataStore)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                financeRepository.updateTransaction(id = transactionId, note = reply)
                NotificationManagerCompat.from(context).cancel(transactionId.hashCode())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
