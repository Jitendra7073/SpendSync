package com.example.spendsync.ui.holds

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.contacts.Outreach
import com.example.spendsync.data.holds.Channel
import com.example.spendsync.data.holds.FollowUpTarget
import com.example.spendsync.data.holds.FollowUpTemplates
import com.example.spendsync.data.holds.Tone
import com.example.spendsync.data.holds.canUse
import com.example.spendsync.data.local.HoldContact
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.data.local.holdContactKey
import com.example.spendsync.data.remote.model.HoldMessageRequest
import com.example.spendsync.data.repository.AuthResult
import com.example.spendsync.data.repository.FinanceRepository
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppChip
import com.example.spendsync.ui.components.AppSheet
import com.example.spendsync.ui.components.AppTextField
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.contacts.rememberContactPicker
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.i18n.AppLanguage
import com.example.spendsync.ui.i18n.LanguageManager
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.share.ExternalApps
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

private enum class Step { Setup, Writing, Review, Opened }

/** Follow-up from a hold screen: a sheet around [FollowUpPanel]. */
@Composable
fun FollowUpSheet(
    target: FollowUpTarget,
    financeRepository: FinanceRepository,
    sessionDataStore: SessionDataStore,
    aiAllowed: Boolean,
    onDismiss: () -> Unit,
) {
    AppSheet(onDismiss = onDismiss, title = tr(R.string.fu_title, target.personName)) {
        FollowUpPanel(target, financeRepository, sessionDataStore, aiAllowed, onDone = onDismiss)
    }
}

/**
 * Prepare, check and open a follow-up message. The same panel runs in the Holds screens (manual) and inside the
 * assistant chat. The rule is the same everywhere: a draft is ALWAYS written first and shown, and only "Yes, send"
 * opens WhatsApp, SMS or email, with the message ready for the person to send themselves.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FollowUpPanel(
    target: FollowUpTarget,
    financeRepository: FinanceRepository,
    sessionDataStore: SessionDataStore,
    aiAllowed: Boolean,
    onDone: () -> Unit,
    /** Skip the setup step and write straight away (the assistant already picked the channel and tone). */
    autoStart: Boolean = false,
    initialChannel: Channel = Channel.WhatsApp,
    initialTone: Tone = Tone.Friendly,
    initialContext: String = "",
    /** Smaller buttons, for the card inside the chat. */
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by sessionDataStore.holdContacts.collectAsState(initial = emptyMap())
    val contact: HoldContact? = contacts[holdContactKey(target.personName)]

    var step by remember { mutableStateOf(if (autoStart) Step.Writing else Step.Setup) }
    var channel by remember { mutableStateOf(initialChannel) }
    var tone by remember { mutableStateOf(initialTone) }
    var language by remember { mutableStateOf(LanguageManager.current) }
    var extra by remember { mutableStateOf(initialContext) }
    var draft by remember { mutableStateOf("") }
    var fromAi by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var askMore by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var pendingSend by remember { mutableStateOf(false) }
    var rewrites by remember { mutableStateOf(0) }

    fun channelName() = tr(when (channel) { Channel.WhatsApp -> R.string.fu_ch_whatsapp; Channel.Sms -> R.string.fu_ch_sms; Channel.Email -> R.string.fu_ch_email })

    suspend fun write(previous: String?) {
        step = Step.Writing
        problem = null
        val userName = sessionDataStore.userName.first()
        val reply = if (aiAllowed) {
            (financeRepository.holdMessage(
                HoldMessageRequest(
                    personName = target.personName, direction = target.direction, amount = target.amount, dueDate = target.dueDate,
                    overdueDays = target.overdueDays, channel = channel.id, tone = tone.id, language = language.storedName,
                    context = extra.trim().ifBlank { null }, previous = previous?.ifBlank { null }, userName = userName?.takeIf { it.isNotBlank() },
                ),
            ) as? AuthResult.Success)?.data
        } else null
        val ai = reply?.takeIf { it.source == "ai" && !it.text.isNullOrBlank() }
        draft = ai?.text ?: FollowUpTemplates.text(target, tone, language)
        fromAi = ai != null
        editing = false
        askMore = false
        step = Step.Review
    }

    fun open() {
        val digits = contact?.phone?.let { Outreach.normalizePhone(it, Outreach.callingCodeFor(Locale.getDefault().country)) }
        val ok = when (channel) {
            Channel.WhatsApp -> digits != null && ExternalApps.openWhatsApp(context, digits, draft)
            Channel.Sms -> digits != null && ExternalApps.openSms(context, digits, draft)
            Channel.Email -> ExternalApps.openEmail(context, contact?.email, FollowUpTemplates.subject(target, language), draft)
        }
        if (ok) {
            scope.launch { sessionDataStore.logFollowUp(target.personName, channel.id) }
            step = Step.Opened
        } else problem = tr(R.string.fu_opened_fail, channelName())
    }

    val picker = rememberContactPicker { picked ->
        scope.launch {
            sessionDataStore.saveHoldContact(target.personName, picked.name, picked.phone, picked.email)
            if (pendingSend) { pendingSend = false; problem = null }
        }
    }
    fun pick() = if (channel == Channel.Email) picker.pickEmail() else picker.pickPhone()

    // Once a contact with the right detail exists after "Yes, send" was tapped without one, carry on.
    LaunchedEffect(contact, pendingSend) { if (pendingSend && contact.canUse(channel)) { pendingSend = false; open() } }
    LaunchedEffect(Unit) { if (autoStart) write(null) }

    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Recipient line, always visible
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val detail = when (channel) { Channel.Email -> contact?.email; else -> contact?.phone }
                Text(
                    if (detail != null) tr(R.string.fu_to, "${contact?.name ?: target.personName} · $detail") else tr(R.string.fu_to_none),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (detail != null) scheme.onSurface else scheme.onSurfaceVariant,
                )
                if ((contact?.lastFollowUpAt ?: 0L) > 0L) {
                    val ago = android.text.format.DateUtils.getRelativeTimeSpanString(contact!!.lastFollowUpAt, System.currentTimeMillis(), android.text.format.DateUtils.DAY_IN_MILLIS).toString()
                    Text(tr(R.string.fu_last, ago), fontSize = 11.sp, color = scheme.onSurfaceVariant)
                }
            }
            AppButton(tr(if (detail(contact, channel) != null) R.string.fu_change_contact else R.string.fu_pick_contact), onClick = ::pick, variant = ButtonVariant.Tonal, size = ButtonSize.Small, leadingIcon = Icons.Default.Contacts)
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = com.example.spendsync.ui.theme.motionSpec(com.example.spendsync.ui.theme.LocalMotion.current.enabled(com.example.spendsync.ui.theme.MotionKind.Transitions)) { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "followup_step",
        ) { s ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (s) {
                    Step.Setup -> {
                        Text(tr(R.string.fu_channel), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Channel.entries.forEach { c ->
                                AppChip(tr(when (c) { Channel.WhatsApp -> R.string.fu_ch_whatsapp; Channel.Sms -> R.string.fu_ch_sms; Channel.Email -> R.string.fu_ch_email }), selected = channel == c, onClick = { channel = c })
                            }
                        }
                        Text(tr(R.string.fu_tone), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tone.entries.forEach { t ->
                                AppChip(tr(when (t) { Tone.Gentle -> R.string.fu_tone_gentle; Tone.Friendly -> R.string.fu_tone_friendly; Tone.Firm -> R.string.fu_tone_firm }), selected = tone == t, onClick = { tone = t })
                            }
                        }
                        Box {
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).clickable { menu = true }.padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Translate, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                                Text(language.nativeName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary, modifier = Modifier.padding(start = 6.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                AppLanguage.entries.forEach { l -> DropdownMenuItem(text = { Text(l.nativeName) }, onClick = { language = l; menu = false }) }
                            }
                        }
                        AppTextField(extra, { extra = it.take(300) }, label = tr(R.string.fu_context_hint), singleLine = false)
                        AppButton(tr(R.string.fu_prepare), onClick = { scope.launch { write(null) } }, size = if (compact) ButtonSize.Small else ButtonSize.Large, fullWidth = !compact)
                        Text(tr(R.string.fu_privacy), fontSize = 11.sp, color = scheme.onSurfaceVariant)
                    }
                    Step.Writing -> Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = scheme.primary)
                        Text(tr(R.string.fu_writing), fontSize = 14.sp, color = scheme.onSurfaceVariant)
                    }
                    Step.Review -> {
                        Text(tr(R.string.fu_review_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                        if (editing) {
                            AppTextField(draft, { draft = it.take(1200) }, label = channelName(), singleLine = false)
                        } else {
                            Text(
                                draft, fontSize = 15.sp, lineHeight = 22.sp, color = scheme.onSurface,
                                modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp),
                            )
                        }
                        Text(tr(if (fromAi) R.string.fu_ai_note else R.string.fu_template_note), fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        problem?.let { Text(it, fontSize = 12.sp, color = scheme.error) }
                        if (askMore) {
                            AppTextField(extra, { extra = it.take(300) }, label = tr(R.string.fu_context_hint), singleLine = false)
                            AppButton(tr(R.string.fu_rewrite_with), onClick = { rewrites++; scope.launch { write(draft) } }, variant = ButtonVariant.Tonal, fullWidth = !compact, enabled = extra.isNotBlank())
                        }
                        AppButton(
                            tr(R.string.fu_yes_send),
                            onClick = {
                                if (contact.canUse(channel) || channel == Channel.Email) open()
                                else { problem = tr(if (channel == Channel.Email) R.string.fu_no_email else R.string.fu_no_phone); pendingSend = true; pick() }
                            },
                            size = if (compact) ButtonSize.Small else ButtonSize.Large, fullWidth = !compact, enabled = draft.isNotBlank(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton(tr(R.string.fu_no_rewrite), onClick = { rewrites++; scope.launch { write(draft) } }, variant = ButtonVariant.Outline, size = ButtonSize.Small, modifier = Modifier.weight(1f))
                            AppButton(tr(R.string.fu_more_context), onClick = { askMore = !askMore }, variant = ButtonVariant.Outline, size = ButtonSize.Small, modifier = Modifier.weight(1f))
                        }
                        AppButton(tr(R.string.fu_edit), onClick = { editing = !editing }, variant = ButtonVariant.Text, size = ButtonSize.Small)
                    }
                    Step.Opened -> {
                        Text(tr(R.string.fu_opened, channelName()), fontSize = 14.sp, color = scheme.onSurface, modifier = Modifier.padding(vertical = 8.dp))
                        AppButton(tr(R.string.got_it), onClick = onDone, size = if (compact) ButtonSize.Small else ButtonSize.Large, fullWidth = !compact)
                    }
                }
            }
        }
    }
}

private fun detail(contact: HoldContact?, channel: Channel): String? = if (channel == Channel.Email) contact?.email else contact?.phone
