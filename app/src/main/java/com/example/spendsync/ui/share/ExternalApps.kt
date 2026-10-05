package com.example.spendsync.ui.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.spendsync.data.contacts.Outreach

/**
 * Opens other apps with a message already written. We never send anything ourselves: the person looks at it and
 * taps Send in WhatsApp, their SMS app or their email app. Every launcher returns false instead of throwing when
 * nothing on the phone can handle it, so the caller can say so.
 */
object ExternalApps {
    private val whatsAppPackages = listOf("com.whatsapp", "com.whatsapp.w4b")

    private fun installedWhatsApp(context: Context): String? = whatsAppPackages.firstOrNull {
        try { context.packageManager.getPackageInfo(it, 0); true } catch (e: PackageManager.NameNotFoundException) { false }
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    /** Opens a WhatsApp chat with [digits] (international, digits only) and [text]; no number lets WhatsApp ask who. */
    fun openWhatsApp(context: Context, digits: String?, text: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Outreach.whatsAppUrl(digits, text)))
        installedWhatsApp(context)?.let { intent.setPackage(it) } // straight into the app, not a browser chooser
        return start(context, intent)
    }

    fun openSms(context: Context, digits: String, body: String): Boolean =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.parse(Outreach.smsUri(digits))).putExtra("sms_body", body))

    /** Only email apps handle `mailto:`; the mail leaves from the user's own address. */
    fun openEmail(context: Context, email: String?, subject: String, body: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            if (!email.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return start(context, intent)
    }

    /** The system share sheet. */
    fun openChooser(context: Context, text: String, title: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        return start(context, Intent.createChooser(send, title))
    }
}
