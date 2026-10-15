package com.example.spendsync.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.spendsync.MainActivity
import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Where a Planify notification should take the user. The notification carries a short code in its intent;
 * MainActivity drops it here and the main screen picks it up (and clears it) once it is on screen.
 */
object PlanifyLinks {
    const val EXTRA = "planify_link"
    const val OPEN = "open"   // go to the Planify page
    const val BUILD = "build" // go to Planify and start the plan builder

    val pending = MutableStateFlow<String?>(null)

    /** True while a Planify page with its own bottom buttons (builder, bucket detail) is open: the nav bar steps aside. */
    val coversBottomBar = MutableStateFlow(false)
}

/** "Attach bill" on a capture notification: carries the transaction id to the app. */
object BillLinks {
    const val EXTRA = "bill_link"
    val pending = MutableStateFlow<String?>(null)
}

/** Posts Planify's own notifications (limit alerts, salary prompt, daily summary, month wrap-up). */
object PlanNotifier {
    private const val CHANNEL_ID = "planify"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, tr(R.string.pl_channel_name), NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** False when the user (or Android 13+) has not allowed notifications: then we simply stay quiet. */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** [key] makes the notification id stable, so a newer alert about the same thing replaces the older one. */
    fun post(context: Context, key: String, title: String, text: String, link: String): Boolean {
        if (!canPost(context)) return false
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(PlanifyLinks.EXTRA, link)
        }
        val tap = PendingIntent.getActivity(context, key.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
        return true
    }
}
