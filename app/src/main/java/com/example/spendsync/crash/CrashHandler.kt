package com.example.spendsync.crash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import kotlin.system.exitProcess

/**
 * Process-wide safety net.
 *
 * Registered from [com.example.spendsync.SpendSyncApp]. When *any* thread throws
 * an exception that nobody catches, Android would normally show the system
 * "SpendSync keeps stopping" dialog and kill the app. Instead we intercept it,
 * launch [ErrorActivity] with a friendly message + a Restart button, and then
 * cleanly tear down the crashed process.
 *
 * This does NOT fix bugs — it only turns a hard crash into a recoverable,
 * user-facing error screen. Real fixes still belong in the code that threw.
 */
class CrashHandler private constructor(
    private val appContext: Context,
    // Keep a reference to the previous handler so we don't swallow the platform's
    // own logging/reporting (e.g. Play Console crash collection still fires).
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "Uncaught exception on thread '${thread.name}'", throwable)

        try {
            // Let the platform's default handler run first so crash reporting
            // still records the stack trace, but guard it — if it itself throws
            // we must not loop.
            runCatching { defaultHandler?.uncaughtException(thread, throwable) }

            launchErrorScreen(throwable)
        } catch (t: Throwable) {
            // Last-ditch: if even the error screen fails, fall back to the
            // platform handler so we never spin forever.
            Log.e(TAG, "CrashHandler failed while handling a crash", t)
        } finally {
            // The process is in an undefined state after an uncaught exception,
            // so we cannot safely keep it alive. Kill it cleanly — ErrorActivity
            // runs in its own task and survives this.
            Process.killProcess(Process.myPid())
            exitProcess(HARD_EXIT_CODE)
        }
    }

    private fun launchErrorScreen(throwable: Throwable) {
        val intent = Intent(appContext, ErrorActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            putExtra(ErrorActivity.EXTRA_MESSAGE, throwable.friendlyMessage())
        }
        appContext.startActivity(intent)
    }

    companion object {
        private const val TAG = "CrashHandler"
        private const val HARD_EXIT_CODE = 10

        /** Call once, early, from [Application.onCreate]. Safe to call multiple times. */
        fun install(app: Application) {
            val existing = Thread.getDefaultUncaughtExceptionHandler()
            if (existing is CrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(app.applicationContext, existing),
            )
        }

        private fun Throwable.friendlyMessage(): String {
            val detail = message?.takeIf { it.isNotBlank() } ?: this::class.simpleName
            return detail ?: "An unexpected error occurred."
        }
    }
}
