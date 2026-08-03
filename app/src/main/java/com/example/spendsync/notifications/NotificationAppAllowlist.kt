package com.example.spendsync.notifications

data class AllowlistedApp(val packageName: String, val displayName: String)

/**
 * Curated seed list for the Settings "Auto-detect transactions" per-app
 * toggles. Deliberately not user-extensible in v1 (see design spec's open
 * questions) — an app not on this list simply isn't offered as a toggle.
 */
object NotificationAppAllowlist {
    val APPS = listOf(
        AllowlistedApp("com.google.android.apps.nbu.paisa.user", "Google Pay"),
        AllowlistedApp("com.phonepe.app", "PhonePe"),
        AllowlistedApp("net.one97.paytm", "Paytm"),
        AllowlistedApp("com.google.android.apps.messaging", "Messages"),
        AllowlistedApp("com.samsung.android.messaging", "Samsung Messages"),
        AllowlistedApp("com.android.mms", "Messaging"),
    )
}
