package com.example.spendsync.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.spendsync.R
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.planify.PlanAlerts
import com.example.spendsync.data.remote.model.CaptureResultDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.utils.formatInr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What the user hears after the server decided about a captured message: "Added …" (reply to describe it),
 * "Same payment?" (Same / Different) or, in "Ask me first" mode, "Add this payment?" (Add / Ignore).
 * Duplicates, ignored messages and ones still waiting to sync stay silent.
 */
object CaptureNotifier {

    suspend fun onResult(context: Context, finance: FinanceRepository, store: SessionDataStore, result: CaptureResultDto) {
        val m = result.message
        when (m.status) {
            "added" -> result.transaction?.let { tx ->
                TransactionCaptureNotifier.postDescriptionRequest(context, tx)
                PlanAlerts.onTransactionChanged(context, finance, store, tx)
            }
            "review" -> {
                val amount = m.amount?.toDoubleOrNull()?.takeIf { !store.amountMaskingEnabled.first() }?.let(::formatInr) ?: "•••"
                val who = m.payee ?: m.sender
                val duplicate = m.reason == "possible_duplicate"
                post(
                    context, m.id,
                    title = tr(if (duplicate) R.string.cap_n_same_title else R.string.cap_n_ask_title),
                    text = tr(if (duplicate) R.string.cap_n_same_text else R.string.cap_n_ask_text, amount, who),
                    actions = if (duplicate) listOf("same" to tr(R.string.cap_same), "different" to tr(R.string.cap_different))
                    else listOf("add" to tr(R.string.cap_add), "ignore" to tr(R.string.cap_ignore)),
                )
            }
        }
    }

    private fun post(context: Context, messageId: String, title: String, text: String, actions: List<Pair<String, String>>) {
        TransactionCaptureNotifier.ensureChannel(context)
        val builder = NotificationCompat.Builder(context, NotificationCaptureIds.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        actions.forEachIndexed { i, (action, label) ->
            val intent = Intent(context, CaptureActionReceiver::class.java)
                .putExtra(EXTRA_ID, messageId)
                .putExtra(EXTRA_ACTION, action)
            val pending = PendingIntent.getBroadcast(context, messageId.hashCode() * 4 + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(0, label, pending)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(messageId.hashCode(), builder.build())
    }

    internal const val EXTRA_ID = "capture_message_id"
    internal const val EXTRA_ACTION = "capture_action"
}

/** Same / Different / Add / Ignore tapped on a capture notification. Offline: the notification stays for a retry. */
class CaptureActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(CaptureNotifier.EXTRA_ID) ?: return
        val action = intent.getStringExtra(CaptureNotifier.EXTRA_ACTION) ?: return
        val pending = goAsync()
        val store = SessionDataStore(context.applicationContext)
        val finance = FinanceRepository.shared(store)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = finance.actOnCapturedMessage(id, action)
                if (result is AuthResult.Success) {
                    NotificationManagerCompat.from(context).cancel(id.hashCode())
                    CaptureNotifier.onResult(context.applicationContext, finance, store, result.data)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
