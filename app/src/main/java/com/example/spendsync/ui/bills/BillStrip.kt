package com.example.spendsync.ui.bills

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.spendsync.R
import com.example.spendsync.data.bills.QueuedBill
import com.example.spendsync.ui.components.AppTooltip
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.theme.LocalMotion
import com.example.spendsync.ui.theme.MotionKind
import java.io.File

private val TILE = 72.dp
private val SHAPE = RoundedCornerShape(12.dp)

/** Thumbnails of a transaction's bill pages (server + queued) and an Add tile. */
@Composable
fun BillStrip(
    tiles: List<BillTile>,
    canAdd: Boolean,
    masked: Boolean,
    onAdd: () -> Unit,
    onOpen: (Int) -> Unit,
    onRetry: (QueuedBill) -> Unit,
    onRemove: (QueuedBill) -> Unit,
    localPreview: List<Picked> = emptyList(),
) {
    val total = tiles.size + localPreview.size
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tiles, key = { t -> if (t is BillTile.Remote) t.bill.id else (t as BillTile.Local).item.clientKey }) { t ->
            val index = tiles.indexOf(t)
            AppTooltip(tr(R.string.bills_page, index + 1, total)) {
                when (t) {
                    is BillTile.Remote -> RemoteTile(t, masked, index, onOpen = { onOpen(tiles.filterIsInstance<BillTile.Remote>().indexOf(t)) }, onRetry, onRemove, Modifier.animateItem())
                    is BillTile.Local -> LocalTile(t.item, t.progress, index, masked, onRetry, onRemove, Modifier.animateItem())
                }
            }
        }
        items(localPreview, key = { it.uri.toString() }) { p -> PreviewTile(p) }
        if (canAdd) item(key = "add") { AddTile(onAdd) }
    }
}

@Composable
private fun RemoteTile(t: BillTile.Remote, masked: Boolean, index: Int, onOpen: () -> Unit, onRetry: (QueuedBill) -> Unit, onRemove: (QueuedBill) -> Unit, modifier: Modifier) {
    val deviceBlur = masked && Build.VERSION.SDK_INT >= 31
    Box(modifier.size(TILE).clip(SHAPE).clickable(onClick = onOpen).semantics { contentDescription = tr(R.string.bills_cd_tile, index + 1, tr(R.string.bills_cd_ready)) }) {
        AsyncImage(
            model = billImageUrl(t.bill, masked, Build.VERSION.SDK_INT, thumb = true),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(if (deviceBlur) Modifier.blur(16.dp) else Modifier),
        )
        if (t.bill.format == "pdf") Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp))
        t.replacing?.let { StateOverlay(it, t.progress, onRetry, onRemove) }
    }
}

@Composable
private fun LocalTile(item: QueuedBill, progress: Float?, index: Int, masked: Boolean, onRetry: (QueuedBill) -> Unit, onRemove: (QueuedBill) -> Unit, modifier: Modifier) {
    val status = when (item.state) {
        QueuedBill.State.Failed -> tr(R.string.bills_failed)
        QueuedBill.State.Uploading -> tr(R.string.bills_uploading, ((progress ?: 0f) * 100).toInt())
        else -> tr(R.string.bills_waiting)
    }
    Box(modifier.size(TILE).clip(SHAPE).semantics { contentDescription = tr(R.string.bills_cd_tile, index + 1, status) }) {
        val preview = localPreview(masked, Build.VERSION.SDK_INT)
        if (item.mime == "application/pdf" || preview == LocalPreview.Placeholder) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(if (item.mime == "application/pdf") Icons.Default.PictureAsPdf else Icons.Default.Receipt, null, tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            AsyncImage(
                model = File(item.localPath), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (preview == LocalPreview.DeviceBlur) Modifier.blur(16.dp) else Modifier),
            )
        }
        StateOverlay(item, progress, onRetry, onRemove)
    }
}

/** Uploading = dim + real progress ring; confirming = spinning ring; done = check that fades; waiting/failed = badge. */
@Composable
private fun BoxScope.StateOverlay(item: QueuedBill, progress: Float?, onRetry: (QueuedBill) -> Unit, onRemove: (QueuedBill) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalMotion.current
    val ring by animateFloatAsState(progress ?: 0f, if (motion.enabled(MotionKind.Transitions)) tween(250) else snap(), label = "billProgress")
    var menu by remember { mutableStateOf(false) }
    when (item.state) {
        QueuedBill.State.Uploading -> {
            Box(Modifier.matchParentSize().background(scheme.surface.copy(alpha = 0.4f)))
            CircularProgressIndicator(progress = { ring }, color = scheme.primary, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
        QueuedBill.State.Confirming -> {
            Box(Modifier.matchParentSize().background(scheme.surface.copy(alpha = 0.4f)))
            CircularProgressIndicator(color = scheme.primary, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
        QueuedBill.State.Waiting -> Icon(Icons.Default.CloudOff, tr(R.string.bills_waiting), tint = scheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).background(scheme.surface, CircleShape).padding(2.dp))
        QueuedBill.State.Failed -> Box(Modifier.matchParentSize().clickable { menu = true }) {
            Icon(Icons.Default.ErrorOutline, tr(R.string.bills_failed), tint = scheme.error,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).background(scheme.surface, CircleShape).padding(2.dp))
        }
    }
    if (menu) {
        com.example.spendsync.ui.components.AppDialog(
            onDismiss = { menu = false },
            title = tr(R.string.bills_failed),
            message = item.error,
            primary = com.example.spendsync.ui.components.DialogAction(tr(R.string.bills_retry), { menu = false; onRetry(item) }),
            secondary = com.example.spendsync.ui.components.DialogAction(tr(R.string.bills_remove), { menu = false; onRemove(item) }),
        )
    }
}

@Composable
private fun PreviewTile(p: Picked) {
    Box(Modifier.size(TILE).clip(SHAPE)) {
        if (p.mime == "application/pdf") Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary) }
        else AsyncImage(model = p.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun AddTile(onAdd: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(TILE).clip(SHAPE).background(scheme.surfaceVariant).border(1.dp, scheme.outlineVariant, SHAPE).clickable(onClick = onAdd)
            .semantics { contentDescription = tr(R.string.bills_add) },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.Add, null, tint = scheme.primary) }
}
