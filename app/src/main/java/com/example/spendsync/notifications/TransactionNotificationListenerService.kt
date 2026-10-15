package com.example.spendsync.notifications

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.remote.model.CaptureRequest
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Smart capture's entry point: bank SMS, bank emails and UPI-app alerts arrive here as notifications,
 * are read by [CaptureParser] and sent through [FinanceRepository.capture] (which queues them offline).
 */
class TransactionNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sessionDataStore: SessionDataStore
    private lateinit var financeRepository: FinanceRepository

    override fun onCreate() {
        super.onCreate()
        sessionDataStore = SessionDataStore(applicationContext)
        financeRepository = FinanceRepository.shared(sessionDataStore)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Cheap synchronous pre-filter: most notifications come from apps we never read.
        if (sbn.packageName !in CaptureSources.ALL && sbn.packageName != smsApp) return
        val n = sbn.notification
        // A group summary repeats the messages already posted one by one.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val source = CaptureSources.of(sbn.packageName, smsApp) ?: return
        val (sender, body) = extract(n) ?: return
        val postedAt = sbn.postTime

        serviceScope.launch {
            // A notification must never be able to crash the app.
            runCatching { handle(source, sbn.packageName, sender, body, postedAt) }
        }
    }

    private suspend fun handle(source: CaptureSource, packageName: String, sender: String, body: String, postedAt: Long) {
        if (sessionDataStore.autoCaptureEnabled.firstOrNull() != true) return
        // No session means the request would 401 and sign the user out of the foreground UI.
        if (sessionDataStore.sessionToken.firstOrNull().isNullOrBlank()) return
        val settings = sessionDataStore.captureSettings.first()
        if (source.wire !in settings.sources || settings.blocks(sender)) return

        val zone = ZoneId.systemDefault()
        val receivedDay = Instant.ofEpochMilli(postedAt).atZone(zone).toLocalDate()
        val found = CaptureParser.parse(source, sender, "$sender\n$body", receivedDay) ?: return
        if (found.ignoreReason != null && !settings.keepMessages) return

        // A late SMS is booked on the day it names (noon, as it gives no time).
        val at = found.date?.atTime(12, 0)?.atZone(zone)?.toInstant() ?: Instant.ofEpochMilli(postedAt)
        // ponytail: the same text on the same day is one message (apps re-post notifications); banks add a
        // ref or balance, so two real payments differ. Stable id = a re-post or retry is stored once.
        val id = UUID.nameUUIDFromBytes("$packageName|$sender|$body|$receivedDay".toByteArray()).toString()
        val request = CaptureRequest(
            id = id,
            source = source.wire,
            sender = sender.take(120),
            packageName = packageName,
            body = body.take(4000),
            receivedAt = at.toString(),
            amount = found.amount,
            type = found.direction?.let { if (it == TransactionDirection.DEBIT) "debit" else "credit" },
            payee = found.payee?.take(200),
            refNo = found.refNo?.take(64),
            last4 = found.last4,
            balance = found.balance,
            ignoreReason = found.ignoreReason,
        )
        val result = financeRepository.capture(request, addsTransaction = settings.mode == "auto")
        if (result is AuthResult.Success) CaptureNotifier.onResult(applicationContext, financeRepository, sessionDataStore, result.data)
    }

    // ponytail: read once per service start (it is a system lookup); switching the default SMS app is
    // picked up when the service next starts. Known SMS apps (CaptureSources) are always covered.
    private val smsApp: String? by lazy { runCatching { Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull() }

    /**
     * (sender, text). Chat-style apps (Messages) keep the latest message in MessagingStyle; Gmail puts
     * subject + first lines in the big text. The title is the SMS sender id or the email sender name.
     */
    private fun extract(n: Notification): Pair<String, String>? {
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val latest = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages?.lastOrNull()
        val body = (latest?.text ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.trim().orEmpty()
        if (body.isEmpty()) return null
        val sender = latest?.person?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: title
        return (sender.ifEmpty { "Unknown" }) to body
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
