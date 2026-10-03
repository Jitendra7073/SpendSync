package com.example.spendsync.utils

import com.example.spendsync.R
import com.example.spendsync.ui.i18n.tr
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.spendsync.data.remote.model.TransactionDto
import java.io.File
import java.io.FileOutputStream

/**
 * Writes transactions to a CSV or PDF file in the app's private cache dir,
 * then returns a share Intent (via FileProvider) so the user can save/send
 * it — cache dir needs no storage permission.
 */
object TransactionExporter {

    fun exportCsv(context: Context, transactions: List<TransactionDto>): Intent {
        val file = File(context.cacheDir, "spendsync_transactions.csv")
        file.bufferedWriter().use { writer ->
            writer.appendLine(tr(R.string.csv_header))
            transactions.forEach { tx ->
                val row = listOf(tx.createdAt, if (tx.type == "credit") tr(R.string.money_in) else tr(R.string.money_out), tx.category, tx.merchant, tx.amount, tx.note.orEmpty())
                writer.appendLine(row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" })
            }
        }
        return shareIntent(context, file, "text/csv")
    }

    fun exportPdf(context: Context, transactions: List<TransactionDto>): Intent {
        val pageWidth = 595 // A4 @ 72dpi
        val pageHeight = 842
        val margin = 40f
        val bodyPaint = Paint().apply { textSize = 10f }
        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true }

        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin
        canvas.drawText(tr(R.string.pdf_title), margin, y, titlePaint)
        y += 28f

        transactions.forEach { tx ->
            if (y > pageHeight - margin) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = margin
            }
            val sign = if (tx.type == "credit") "+" else "-"
            val line = "${tx.createdAt.take(10)}   ${tx.category.take(16).padEnd(16)}   ${tx.merchant.take(22).padEnd(22)}   $sign${tx.amount}"
            canvas.drawText(line, margin, y, bodyPaint)
            y += 16f
        }
        document.finishPage(page)

        val file = File(context.cacheDir, "spendsync_transactions.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        return shareIntent(context, file, "application/pdf")
    }

    private fun shareIntent(context: Context, file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
