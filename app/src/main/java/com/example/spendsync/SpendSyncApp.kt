package com.example.spendsync

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.example.spendsync.crash.CrashHandler
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.ui.i18n.AppLanguage
import com.example.spendsync.ui.i18n.LanguageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Application entry point.
 *
 * Installs the process-wide [CrashHandler] as early as possible so that any
 * uncaught exception — on any thread, from app start onward — is turned into a
 * friendly error screen instead of the system "app keeps stopping" dialog.
 *
 * Also supplies the app-wide Coil [ImageLoader] with an SVG decoder — every
 * `AsyncImage` in the app (category icons from Iconify) can load remote SVGs
 * without wiring a custom loader at each call site.
 */
class SpendSyncApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Background code (reminder notifications, capture replies, the crash screen) needs the
        // language before any Activity exists.
        LanguageManager.apply(this, AppLanguage.fromStored(runBlocking { SessionDataStore(this@SpendSyncApp).language.first() }))
        CrashHandler.install(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                add(OkHttpNetworkFetcherFactory())
            }
            .build()
    }
}
