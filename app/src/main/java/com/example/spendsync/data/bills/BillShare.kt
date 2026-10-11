package com.example.spendsync.data.bills

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.spendsync.data.remote.model.BillDto
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/** Bill files to the system share sheet (WhatsApp, Gmail…). Copies live in cache/share and are cleared on start/sign-out. */
object BillShare {
    private fun dir(context: Context) = File(context.cacheDir, "share").apply { mkdirs() }

    /** PDFs as the original, photos as the full image. */
    fun fileFor(bill: BillDto): Pair<String, String> =
        if (bill.format == "pdf" && bill.original != null) bill.original to "pdf" else bill.full to "jpg"

    suspend fun prepare(context: Context, repo: FinanceRepository, txIds: List<String>): List<Uri> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Uri>()
        for (tx in txIds) {
            val bills = (repo.listBills(tx) as? AuthResult.Success)?.data.orEmpty()
            bills.forEachIndexed { i, b ->
                runCatching {
                    val (url, ext) = fileFor(b)
                    val f = File(dir(context), "bill-${tx.take(8)}-${i + 1}.$ext")
                    val conn = (URL(url).openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 20_000 }
                    try { conn.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } } } finally { conn.disconnect() }
                    out += FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                }
            }
        }
        out
    }

    fun open(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val send = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0]) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun clear(context: Context) { File(context.cacheDir, "share").deleteRecursively() }
}
