package com.example.spendsync.data.billai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.util.Base64
import com.example.spendsync.data.bills.BillImages
import com.example.spendsync.data.remote.model.BillImageDto
import com.example.spendsync.ui.bills.Picked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Small JPEGs (≤ 1600 px, ≤ 1 MB) of up to 3 pages for the pre-fill read. PDFs are rendered on the phone. */
object AiImages {
    private const val SIDE = 1600
    private const val MAX = 1_000_000

    suspend fun prepare(context: Context, picked: List<Picked>): List<BillImageDto> = withContext(Dispatchers.IO) {
        val out = mutableListOf<BillImageDto>()
        for (p in picked) {
            if (out.size >= 3) break
            if (p.mime == "application/pdf") {
                context.contentResolver.openFileDescriptor(p.uri, "r")?.use { fd ->
                    PdfRenderer(fd).use { pdf ->
                        for (i in 0 until minOf(pdf.pageCount, 3 - out.size)) pdf.openPage(i).use { page ->
                            val (w, h) = BillImages.targetSize(page.width * 2, page.height * 2, SIDE)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            jpeg(bmp)?.let { out += it }
                        }
                    }
                }
            } else {
                BillImages.prepare(context, p.uri, p.mime).getOrNull()?.let { prepared ->
                    val bmp = android.graphics.BitmapFactory.decodeFile(prepared.file.absolutePath)
                    prepared.file.delete()
                    if (bmp != null) {
                        val (w, h) = BillImages.targetSize(bmp.width, bmp.height, SIDE)
                        jpeg(if (w == bmp.width) bmp else Bitmap.createScaledBitmap(bmp, w, h, true))?.let { out += it }
                    }
                }
            }
        }
        out
    }

    private fun jpeg(bmp: Bitmap): BillImageDto? {
        for (q in intArrayOf(70, 55, 40)) {
            val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()
            if (bytes.size <= MAX) return BillImageDto("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        return null
    }
}
