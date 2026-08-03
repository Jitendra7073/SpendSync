package com.example.spendsync.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.repository.AuthResult
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
                val result = financeRepository.updateTransaction(id = transactionId, note = reply)
                when (result) {
                    is AuthResult.Success -> {
                        NotificationManagerCompat.from(context).cancel(transactionId.hashCode())
                    }
                    is AuthResult.Error -> {
                        // Save failed (offline, expired session, etc.) — leave the
                        // notification up so the user sees the reply didn't save
                        // and can retry, instead of silently dropping it.
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
