package com.example.spendsync.ui.bills

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.spendsync.R
import com.example.spendsync.data.remote.model.BillDto
import com.example.spendsync.ui.components.AppIconButton
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.tr

/** Full-screen pages of one transaction's bills: swipe, pinch/double-tap zoom, replace, delete, open PDF. */
@Composable
fun BillViewer(bills: List<BillDto>, start: Int, masked: Boolean, onUnlock: () -> Unit, onDismiss: () -> Unit, onReplace: (BillDto) -> Unit, onDelete: (BillDto) -> Unit) {
    // Each PDF contributes one page per rendered page.
    val pages = remember(bills) { bills.flatMap { b -> b.pageUrls.mapIndexed { i, url -> Triple(b, url, i) } } }
    val first = remember(bills, start) { pages.indexOfFirst { it.first.id == bills.getOrNull(start)?.id }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = first) { pages.size }
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(scheme.background)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
                val (bill, url, _) = pages[i]
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                val deviceBlur = masked && Build.VERSION.SDK_INT >= 31
                AsyncImage(
                    model = if (masked && Build.VERSION.SDK_INT < 31) bill.blurred else url,
                    contentDescription = tr(R.string.bills_page, i + 1, pages.size),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .then(if (deviceBlur) Modifier.blur(24.dp) else Modifier)
                        .pointerInput(masked) {
                            if (masked) detectTapGestures { onUnlock() }
                            else detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = androidx.compose.ui.geometry.Offset.Zero })
                        }
                        .pointerInput(masked) {
                            if (!masked) detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                            }
                        }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                )
                if (masked) Text(tr(R.string.bills_tap_unlock), color = scheme.onBackground, modifier = Modifier.align(Alignment.Center))
            }
            val current = pages.getOrNull(pager.currentPage)?.first
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(Icons.Default.Close, tr(R.string.close), onClick = onDismiss)
                Text(tr(R.string.bills_page, pager.currentPage + 1, pages.size), style = MaterialTheme.typography.titleMedium, color = scheme.onBackground, modifier = Modifier.weight(1f))
                if (current?.original != null) AppIconButton(Icons.AutoMirrored.Filled.OpenInNew, tr(R.string.bills_open_pdf), onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.original)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                if (current != null) {
                    AppIconButton(Icons.Default.FindReplace, tr(R.string.bills_replace), onClick = { onReplace(current) })
                    AppIconButton(Icons.Default.Delete, tr(R.string.bills_delete), onClick = { onDelete(current) })
                }
            }
        }
    }
}
