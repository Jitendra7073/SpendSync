package com.example.spendsync.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.spendsync.data.remote.model.HoldDto
import com.example.spendsync.data.remote.model.TransactionDto
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

enum class ExportKind { Transactions, Holds, Plan }
enum class ExportFormat(val ext: String, val mime: String) { Csv("csv", "text/csv"), Json("json", "application/json"), Pdf("pdf", "application/pdf") }
enum class ExportTypeFilter { All, Income, Expense }
enum class ExportDelivery { Share, Download, Email }

/** One bucket of one month's plan, as it goes into an export. */
data class ExportPlanLine(val month: String, val bucket: String, val kind: String, val limit: Double, val spent: Double)

data class ExportData(
    val transactions: List<TransactionDto> = emptyList(),
    val holds: List<HoldDto> = emptyList(),
    val plan: List<ExportPlanLine> = emptyList(),
    val kinds: Set<ExportKind> = ExportKind.entries.toSet(),
)

class ExportFile(val name: String, val mime: String, val bytes: ByteArray)

/**
 * Builds the files for an export (CSV: one file per kind; JSON: one file with everything; PDF: one document with a
 * section per kind) and hands them over: shared, saved to Downloads, or (elsewhere) emailed.
 */
object DataExporter {
    private fun q(v: Any?) = "\"${v.toString().replace("\"", "\"\"")}\""
    private fun csv(header: List<String>, rows: List<List<Any?>>) =
        (listOf(header.joinToString(",") { q(it) }) + rows.map { r -> r.joinToString(",") { q(it ?: "") } }).joinToString("\n") + "\n"

    private fun day(iso: String) = iso.take(10)

    fun build(data: ExportData, format: ExportFormat, stamp: String = LocalDate.now().toString()): List<ExportFile> = when (format) {
        ExportFormat.Csv -> buildList {
            if (ExportKind.Transactions in data.kinds) add(text("transactions-$stamp.csv", format, csv(
                listOf("Date", "Type", "Category", "Merchant", "Amount", "Note"),
                data.transactions.map { listOf(day(it.createdAt), if (it.type == "credit") "Income" else "Expense", it.category, it.merchant, it.amount, it.note.orEmpty()) },
            )))
            if (ExportKind.Holds in data.kinds) add(text("holds-$stamp.csv", format, csv(
                listOf("Person", "Direction", "Amount", "Due date", "Status", "Created"),
                data.holds.map { listOf(it.personName, if (it.direction == "owed_to_me") "Owes you" else "You owe", it.amount, day(it.expectedReturnDate), it.status, day(it.createdAt)) },
            )))
            if (ExportKind.Plan in data.kinds) add(text("plan-$stamp.csv", format, csv(
                listOf("Month", "Bucket", "Kind", "Limit", "Spent"),
                data.plan.map { listOf(it.month, it.bucket, it.kind, it.limit, it.spent) },
            )))
        }
        ExportFormat.Json -> listOf(text("spendsync-$stamp.json", format, json(data)))
        ExportFormat.Pdf -> listOf(ExportFile("spendsync-$stamp.pdf", format.mime, pdf(data)))
    }

    private fun text(name: String, f: ExportFormat, body: String) = ExportFile(name, f.mime, body.toByteArray(Charsets.UTF_8))

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

    private fun json(d: ExportData): String {
        val parts = mutableListOf<String>()
        if (ExportKind.Transactions in d.kinds) parts += "  \"transactions\": [\n" + d.transactions.joinToString(",\n") {
            "    {\"date\":\"${day(it.createdAt)}\",\"type\":\"${if (it.type == "credit") "income" else "expense"}\",\"category\":\"${esc(it.category)}\",\"merchant\":\"${esc(it.merchant)}\",\"amount\":${it.amount.toDoubleOrNull() ?: 0.0},\"note\":\"${esc(it.note.orEmpty())}\"}"
        } + "\n  ]"
        if (ExportKind.Holds in d.kinds) parts += "  \"holds\": [\n" + d.holds.joinToString(",\n") {
            "    {\"person\":\"${esc(it.personName)}\",\"direction\":\"${it.direction}\",\"amount\":${it.amount.toDoubleOrNull() ?: 0.0},\"due\":\"${day(it.expectedReturnDate)}\",\"status\":\"${it.status}\"}"
        } + "\n  ]"
        if (ExportKind.Plan in d.kinds) parts += "  \"plan\": [\n" + d.plan.joinToString(",\n") {
            "    {\"month\":\"${it.month}\",\"bucket\":\"${esc(it.bucket)}\",\"kind\":\"${it.kind}\",\"limit\":${it.limit},\"spent\":${it.spent}}"
        } + "\n  ]"
        return "{\n  \"exportedAt\": \"${LocalDate.now()}\",\n" + parts.joinToString(",\n") + "\n}\n"
    }

    private fun pdf(d: ExportData): ByteArray {
        val w = 595; val h = 842; val margin = 40f
        val body = Paint().apply { textSize = 10f }
        val head = Paint().apply { textSize = 13f; isFakeBoldText = true }
        val title = Paint().apply { textSize = 17f; isFakeBoldText = true }
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var y = margin
        fun line(text: String, paint: Paint, gap: Float = 16f) {
            if (y > h - margin) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create()); y = margin
            }
            page.canvas.drawText(text, margin, y, paint); y += gap
        }
        line("SpendSync", title, 26f)
        if (ExportKind.Transactions in d.kinds) {
            line("Transactions (${d.transactions.size})", head, 22f)
            d.transactions.forEach { t ->
                line("${day(t.createdAt)}   ${t.category.take(16).padEnd(16)}   ${t.merchant.take(22).padEnd(22)}   ${if (t.type == "credit") "+" else "-"}${t.amount}", body)
            }
            y += 10f
        }
        if (ExportKind.Holds in d.kinds) {
            line("Holds (${d.holds.size})", head, 22f)
            d.holds.forEach { hd -> line("${hd.personName.take(20).padEnd(20)}   ${if (hd.direction == "owed_to_me") "owes you" else "you owe"}   ${hd.amount}   due ${day(hd.expectedReturnDate)}   ${hd.status}", body) }
            y += 10f
        }
        if (ExportKind.Plan in d.kinds) {
            line("Plan (${d.plan.size})", head, 22f)
            d.plan.forEach { p -> line("${p.month}   ${p.bucket.take(22).padEnd(22)}   limit ${p.limit.toLong()}   spent ${p.spent.toLong()}", body) }
        }
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out); doc.close()
        return out.toByteArray()
    }

    /** Opens the share sheet with every file (one file or several). */
    fun shareIntent(context: Context, files: List<ExportFile>, title: String): Intent {
        val uris = ArrayList<Uri>(files.map { f ->
            val file = File(context.cacheDir, f.name).apply { writeBytes(f.bytes) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        })
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        intent.type = if (files.map { it.mime }.distinct().size == 1) files[0].mime else "*/*"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, title)
    }

    /**
     * Saves the files into the phone's Downloads folder (Downloads/SpendSync) with no permission, on Android 10+.
     * Returns false on older phones, where the caller falls back to the share sheet.
     */
    fun saveToDownloads(context: Context, files: List<ExportFile>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            files.forEach { f ->
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, f.name)
                    put(MediaStore.Downloads.MIME_TYPE, f.mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SpendSync")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { it.write(f.bytes) } ?: return false
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
