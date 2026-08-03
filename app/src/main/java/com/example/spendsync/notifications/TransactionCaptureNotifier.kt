package com.example.spendsync.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.spendsync.R
import com.example.spendsync.data.remote.model.TransactionDto

/**
 * Posts the "add a description" follow-up notification after a transaction
 * is auto-captured. Notifications never carry a user-authored note, so this
 * fires for every successful auto-capture, not just low-confidence ones.
 */
object TransactionCaptureNotifier {

    private const val ACTION_ADD_DESCRIPTION = "com.example.spendsync.ACTION_ADD_DESCRIPTION"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationCaptureIds.CHANNEL_ID,
                "Auto-captured transactions",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    fun postDescriptionRequest(context: Context, transaction: TransactionDto) {
        ensureChannel(context)

        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = ACTION_ADD_DESCRIPTION
            putExtra(NotificationCaptureIds.EXTRA_TRANSACTION_ID, transaction.id)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            transaction.id.hashCode(),
            replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit,
            "Add description",
            replyPendingIntent,
        ).addRemoteInput(
            RemoteInput.Builder(NotificationCaptureIds.KEY_TEXT_REPLY)
                .setLabel("Add a description")
                .build()
        ).build()

        val directionSign = if (transaction.type == "credit") "+" else "-"
        val notification = NotificationCompat.Builder(context, NotificationCaptureIds.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$directionSign₹${transaction.amount} to ${transaction.merchant}")
            .setContentText("Tap reply to add a description")
            .addAction(replyAction)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(transaction.id.hashCode(), notification)
    }
}
