package com.example.spendsync.notifications

/**
 * Which apps' notifications Smart capture reads, grouped the way people think about them (bank SMS,
 * bank emails, UPI apps) instead of by package. The phone's default SMS app always counts as SMS.
 */
object CaptureSources {
    private val UPI_APPS = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp", // BHIM
    )
    private val EMAIL_APPS = setOf("com.google.android.gm", "com.microsoft.office.outlook")
    private val SMS_APPS = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.android.messaging",
    )

    /** Packages the listener has to look at; everything else is skipped before any work. */
    val ALL = UPI_APPS + EMAIL_APPS + SMS_APPS

    fun of(packageName: String, defaultSmsPackage: String?): CaptureSource? = when (packageName) {
        in UPI_APPS -> CaptureSource.UPI
        in EMAIL_APPS -> CaptureSource.EMAIL
        in SMS_APPS, defaultSmsPackage -> CaptureSource.SMS
        else -> null
    }
}
