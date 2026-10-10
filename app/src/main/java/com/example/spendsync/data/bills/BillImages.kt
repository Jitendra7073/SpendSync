package com.example.spendsync.data.bills

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

object BillImages {
    const val MAX_SIDE = 2400
    const val MAX_BYTES = 10L * 1024 * 1024
    private const val JPEG_QUALITY = 85

    data class Prepared(val file: File, val mime: String, val bytes: Long)
    sealed interface Problem { data object TooBig : Problem; data object Unreadable : Problem }

    /** Size so the longest side is at most [max]; never 0. */
    fun targetSize(w: Int, h: Int, max: Int = MAX_SIDE): Pair<Int, Int> {
        val longest = maxOf(w, h)
        if (longest <= max) return w to h
        val s = max.toDouble() / longest
        return (w * s).roundToInt().coerceAtLeast(1) to (h * s).roundToInt().coerceAtLeast(1)
    }

    /**
     * Copies [uri] into private storage. Photos are re-encoded as JPEG q85 (longest side 2400 px), which drops EXIF
     * (GPS, device) — after applying the EXIF rotation so the bill isn't sideways. PDFs are copied as they are.
     */
    fun prepare(context: Context, uri: Uri, mime: String): Result<Prepared> = runCatching {
        val out = File(BillQueue.dir(context), "${UUID.randomUUID()}.${if (mime == "application/pdf") "pdf" else "jpg"}")
        if (mime == "application/pdf") {
            context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
            if (out.length() > MAX_BYTES) { out.delete(); throw TooBigException() }
            return@runCatching Prepared(out, mime, out.length())
        }
        val bitmap = decode(context, uri) ?: throw UnreadableException()
        val (w, h) = targetSize(bitmap.width, bitmap.height)
        val scaled = if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (out.length() > MAX_BYTES) { out.delete(); throw TooBigException() }
        Prepared(out, "image/jpeg", out.length())
    }

    class TooBigException : Exception()
    class UnreadableException : Exception()

    private fun decode(context: Context, uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Applies EXIF orientation and decodes HEIC.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (w, h) = targetSize(info.size.width, info.size.height)
                decoder.setTargetSize(w, h)
            }
        } else {
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
            val rotation = context.contentResolver.openInputStream(uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (rotation == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
        }
}
