package com.example.spendsync

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.example.spendsync.crash.CrashHandler

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
