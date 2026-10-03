package com.example.spendsync.notifications

import com.example.spendsync.ui.i18n.tr
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.spendsync.R
import com.example.spendsync.utils.formatInr

object HoldReminderNotifier {

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                HoldReminderIds.CHANNEL_ID,
                tr(R.string.money_hold_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    fun postReminder(context: Context, holdId: String, personName: String, amount: Double, direction: String) {
        ensureChannel(context)

        val message = if (direction == "owed_to_me") {
            tr(R.string.s_1_was_expected_to_return_2, personName, formatInr(amount))
        } else {
            tr(R.string.you_were_expected_to_pay_back, formatInr(amount), personName)
        }

        val notification = NotificationCompat.Builder(context, HoldReminderIds.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(tr(R.string.money_hold_reminder))
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(holdId.hashCode(), notification)
    }
}
