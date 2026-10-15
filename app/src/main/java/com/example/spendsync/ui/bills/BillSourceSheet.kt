package com.example.spendsync.ui.bills

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.spendsync.R
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppSheet
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.tr
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.compose.material3.MaterialTheme

data class Picked(val uri: Uri, val mime: String)

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Scan (ML Kit, on-device), photos (system picker) or a PDF. [remaining] = free pages (1..5). */
@Composable
fun BillSourceSheet(remaining: Int, onPicked: (List<Picked>) -> Unit, onDismiss: () -> Unit, autoScan: Boolean = false) {
    val context = LocalContext.current
    var scannerFailed by remember { mutableStateOf(false) }
    val max = remaining.coerceIn(1, 5)

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty()
        if (result.resultCode == Activity.RESULT_OK && pages.isNotEmpty()) onPicked(pages.take(max).map { Picked(it.imageUri, "image/jpeg") }) else if (autoScan) onDismiss()
    }
    val photoLauncher = rememberLauncherForActivityResult(
        if (max > 1) ActivityResultContracts.PickMultipleVisualMedia(max) else ActivityResultContracts.PickMultipleVisualMedia(2),
    ) { uris -> if (uris.isNotEmpty()) onPicked(uris.take(max).map { Picked(it, context.contentResolver.getType(it) ?: "image/jpeg") }) }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onPicked(listOf(Picked(uri, "application/pdf"))) }

    fun scan() {
        val activity = context.activity() ?: run { scannerFailed = true; return }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(max)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { scannerFailed = true } // e.g. < 1.7 GB RAM: UNSUPPORTED
    }

    LaunchedEffect(autoScan) { if (autoScan) scan() }

    AppSheet(onDismiss = onDismiss, title = tr(R.string.bills_attach)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!scannerFailed) AppButton(tr(R.string.bills_scan), onClick = ::scan, leadingIcon = Icons.Default.DocumentScanner, fullWidth = true)
            else Text(tr(R.string.bills_scanner_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppButton(tr(R.string.bills_photos), onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, variant = ButtonVariant.Tonal, leadingIcon = Icons.Default.PhotoLibrary, fullWidth = true)
            AppButton(tr(R.string.bills_pdf), onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }, variant = ButtonVariant.Tonal, leadingIcon = Icons.Default.PictureAsPdf, fullWidth = true)
        }
    }
}
