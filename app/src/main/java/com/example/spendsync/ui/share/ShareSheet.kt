package com.example.spendsync.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.ui.components.AppSheet
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.i18n.tr

/** Share a piece of text: WhatsApp, email, or any app through the system sheet. Plain text only, no permissions. */
@Composable
fun ShareSheet(text: String, subject: String = tr(R.string.share_subject), onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun done(ok: Boolean) {
        if (!ok) android.widget.Toast.makeText(context, tr(R.string.share_no_app), android.widget.Toast.LENGTH_SHORT).show()
        onDismiss()
    }
    AppSheet(onDismiss = onDismiss, title = tr(R.string.share_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ShareRow(Icons.Default.Chat, tr(R.string.share_whatsapp)) { done(ExternalApps.openWhatsApp(context, null, text)) }
            ShareRow(Icons.Default.Email, tr(R.string.share_email)) { done(ExternalApps.openEmail(context, null, subject, text)) }
            ShareRow(Icons.Default.Share, tr(R.string.share_more)) { done(ExternalApps.openChooser(context, text, tr(R.string.share_title))) }
        }
    }
}

@Composable
private fun ShareRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().glassCard().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, modifier = Modifier.padding(0.dp))
    }
}
