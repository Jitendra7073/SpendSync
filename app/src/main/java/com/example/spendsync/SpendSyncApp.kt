package com.example.spendsync

import android.app.Application
import com.example.spendsync.crash.CrashHandler

/**
 * Application entry point.
 *
 * Installs the process-wide [CrashHandler] as early as possible so that any
 * uncaught exception — on any thread, from app start onward — is turned into a
 * friendly error screen instead of the system "app keeps stopping" dialog.
 */
class SpendSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
