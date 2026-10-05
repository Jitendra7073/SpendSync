package com.example.spendsync.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.assistant.ComposeProposal
import com.example.spendsync.data.contacts.Outreach
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.local.holdContactKey
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppTextField
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.contacts.rememberContactPicker
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.share.ExternalApps
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A message the assistant prepared for WhatsApp, SMS or email. The assistant never sends: the person checks the text,
 * optionally picks the contact, and "Yes, send" opens the other app with the message ready to send.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComposeCard(
    c: ComposeProposal,
    sessionDataStore: SessionDataStore,
    /** Continue the chat about this message, for example "rewrite it". */
    onAsk: (text: String, reference: String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by sessionDataStore.holdContacts.collectAsState(initial = emptyMap())
    var text by remember(c) { mutableStateOf(c.message) }
    var editing by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<com.example.spendsync.ui.contacts.PickedContact?>(null) }
    val saved = c.toName?.let { contacts[holdContactKey(it)] }
    val email = c.channel == "email"
    val phone = picked?.phone ?: saved?.phone
    val address = picked?.email ?: saved?.email
    val recipient = picked?.name ?: saved?.name ?: c.toName

    val picker = rememberContactPicker { p ->
        picked = p
        c.toName?.let { n -> scope.launch { sessionDataStore.saveHoldContact(n, p.name, p.phone, p.email) } }
    }
    val channelLabel = tr(when (c.channel) { "sms" -> R.string.fu_ch_sms; "email" -> R.string.fu_ch_email; else -> R.string.fu_ch_whatsapp })

    fun send() {
        val digits = phone?.let { Outreach.normalizePhone(it, Outreach.callingCodeFor(Locale.getDefault().country)) }
        val ok = when (c.channel) {
            "sms" -> digits != null && ExternalApps.openSms(context, digits, text)
            "email" -> ExternalApps.openEmail(context, address, c.subject ?: tr(R.string.share_subject), text)
            else -> ExternalApps.openWhatsApp(context, digits, text) // no number: WhatsApp lets the user choose the chat
        }
        if (ok) { done = true; problem = null } else problem = if (c.channel == "sms" && digits == null) tr(R.string.fu_no_phone) else tr(R.string.fu_opened_fail, channelLabel)
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface)
            .border(0.5.dp, scheme.outlineVariant, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (recipient != null) tr(R.string.cm_title_to, channelLabel, recipient) else tr(R.string.cm_title, channelLabel),
            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface,
        )
        if (editing) AppTextField(text, { text = it.take(1500) }, label = channelLabel, singleLine = false)
        else Text(text, fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurface, maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (!email && phone == null && c.channel == "sms") Text(tr(R.string.cm_need_phone), fontSize = 11.sp, color = scheme.onSurfaceVariant)
        problem?.let { Text(it, fontSize = 11.sp, color = scheme.error) }
        if (done) {
            Text(tr(R.string.fu_opened, channelLabel), fontSize = 12.sp, color = scheme.primary)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppButton(tr(R.string.fu_yes_send), onClick = ::send, size = ButtonSize.Small, enabled = text.isNotBlank())
                AppButton(tr(R.string.cm_pick), onClick = { if (email) picker.pickEmail() else picker.pickPhone() }, variant = ButtonVariant.Tonal, size = ButtonSize.Small, leadingIcon = Icons.Default.Contacts)
                AppButton(tr(if (editing) R.string.cm_done_editing else R.string.cm_edit), onClick = { editing = !editing }, variant = ButtonVariant.Outline, size = ButtonSize.Small)
                AppButton(tr(R.string.cm_rewrite), onClick = { onAsk(tr(R.string.cm_rewrite_prompt), text) }, variant = ButtonVariant.Text, size = ButtonSize.Small)
            }
        }
    }
}
