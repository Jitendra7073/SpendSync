package com.example.spendsync.ui.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spendsync.R
import com.example.spendsync.data.assistant.FailureKind
import com.example.spendsync.data.assistant.FastReply
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppChip
import com.example.spendsync.ui.components.AppConfirmDialog
import com.example.spendsync.ui.components.AppDialog
import com.example.spendsync.ui.components.AppIconButton
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.DialogAction
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Skeleton
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.home.glassCard
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.settings.SettingsBackdrop
import com.example.spendsync.ui.settings.SettingsContentWidth
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.utils.maskAmountsInText
import kotlinx.coroutines.launch

/**
 * The assistant chat. Streams answers word by word, shows what it is checking, offers "Open …"
 * buttons and follow-up chips, and hides large amounts exactly like every other screen.
 */
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    sessionDataStore: SessionDataStore,
    amountVisibility: AmountVisibilityState,
    currentScreen: String?,
    onBack: () -> Unit,
    onOpenScreen: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val state by viewModel.state.collectAsState()
    val consent by sessionDataStore.assistantConsent.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    // Keep the newest text in view while it streams.
    val liveLength = state.live?.text?.length ?: 0
    LaunchedEffect(state.messages.size, liveLength, state.live != null, state.suggestions.size) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun submit(text: String) {
        if (text.isBlank()) return
        viewModel.send(text, currentScreen)
        input = ""
    }

    SettingsBackdrop {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                onBack = onBack,
                onClear = { confirmClear = true },
                canClear = state.messages.isNotEmpty() && !state.busy,
            )

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.loaded && state.messages.isEmpty() && state.live == null) {
                        item(key = "welcome") { Welcome(currentScreen) { submit(it) } }
                    }
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(msg, amountVisibility, onOpenScreen)
                    }
                    state.live?.let { live ->
                        item(key = "live") { LiveBubble(live.text, live.tool, amountVisibility) }
                    }
                    state.failure?.let { failure ->
                        item(key = "failure") {
                            FailureNote(failure, onRetry = { viewModel.retry(currentScreen) })
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = state.suggestions.isNotEmpty() && !state.busy,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut(),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.suggestions) { s ->
                        val shown = maskAmountsInText(s, amountVisibility.isMaskingEnabled, amountVisibility.isVisible).text
                        AppChip(shown, selected = false, onClick = { submit(s) }, role = androidx.compose.ui.semantics.Role.Button)
                    }
                }
            }

            InputBar(
                value = input,
                onValueChange = { input = it },
                busy = state.busy,
                onSend = { submit(input) },
                onStop = viewModel::stop,
            )
        }
    }

    if (consent == false) {
        AppDialog(
            onDismiss = onBack,
            title = tr(R.string.assistant_consent_title),
            message = tr(R.string.assistant_consent_body),
            icon = Icons.Default.AutoAwesome,
            primary = DialogAction(tr(R.string.assistant_consent_agree), { scope.launch { sessionDataStore.updateAssistantConsent(true) } }),
            secondary = DialogAction(tr(R.string.assistant_consent_decline), onBack),
            dismissOnOutside = false,
        )
    }

    if (confirmClear) {
        AppConfirmDialog(
            title = tr(R.string.assistant_clear_title),
            message = tr(R.string.assistant_clear_body),
            confirmLabel = tr(R.string.assistant_clear_confirm),
            cancelLabel = tr(R.string.cancel),
            destructive = true,
            onConfirm = { viewModel.clear(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
}

// ── Pieces ───────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(onBack: () -> Unit, onClear: () -> Unit, canClear: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.back), onClick = onBack)
        Box(Modifier.size(34.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(19.dp))
        }
        Text(
            tr(R.string.assistant_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        AppIconButton(Icons.Default.DeleteSweep, tr(R.string.assistant_clear), onClick = onClear, enabled = canClear)
    }
}

@Composable
private fun Welcome(screen: String?, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val starters = when (screen) {
        "analytics" -> listOf(R.string.assistant_starter_categories, R.string.assistant_starter_month, R.string.assistant_starter_balance)
        "budget" -> listOf(R.string.assistant_starter_budget, R.string.assistant_starter_month, R.string.assistant_starter_howto)
        "holds" -> listOf(R.string.assistant_starter_holds, R.string.assistant_starter_balance, R.string.assistant_starter_howto)
        else -> listOf(R.string.assistant_starter_balance, R.string.assistant_starter_month, R.string.assistant_starter_budget, R.string.assistant_starter_holds, R.string.assistant_starter_howto)
    }
    Column(
        Modifier.fillMaxWidth().glassCard().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(28.dp))
        }
        Text(tr(R.string.assistant_welcome_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(tr(R.string.assistant_welcome_body), fontSize = 14.sp, lineHeight = 20.sp, color = scheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        starters.forEach { res ->
            val q = tr(res)
            AppButton(q, onClick = { onPick(q) }, variant = ButtonVariant.Tonal, size = ButtonSize.Medium, fullWidth = true)
        }
    }
}

@Composable
private fun MessageBubble(msg: UiMessage, vis: AmountVisibilityState, onOpenScreen: (String) -> Unit) {
    val text = msg.fast?.let { fastText(it) } ?: msg.text
    if (msg.fromUser) {
        UserBubble(text)
    } else {
        AssistantBubble(text, msg.actions, vis, onOpenScreen, source = msg.source, offline = msg.offline)
    }
}

@Composable
private fun UserBubble(text: String) {
    val scheme = MaterialTheme.colorScheme
    val who = tr(R.string.assistant_a11y_you)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            color = scheme.onPrimary,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .semantics { contentDescription = "$who: $text" }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(scheme.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AssistantBubble(
    text: String,
    actions: List<String>,
    vis: AmountVisibilityState,
    onOpenScreen: (String) -> Unit,
    source: String? = null,
    offline: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val masked = maskAmountsInText(text, vis.isMaskingEnabled, vis.isVisible)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
                .background(scheme.surface)
                .border(0.5.dp, scheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(masked.text, color = scheme.onSurface, fontSize = 15.sp, lineHeight = 22.sp)
            if (masked.hidSomething) {
                AppButton(tr(R.string.show_amounts), onClick = { vis.requestUnlock() }, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
            }
            val caption = if (offline) tr(R.string.asst_answered_offline) else source?.let { tr(R.string.asst_answered_by, it) }
            if (caption != null) Text(caption, fontSize = 11.sp, color = scheme.onSurfaceVariant)
            if (actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { screen ->
                        val label = screenLabel(screen) ?: return@forEach
                        AppButton(label, onClick = { onOpenScreen(screen) }, variant = ButtonVariant.Outline, size = ButtonSize.Small)
                    }
                }
            }
        }
    }
}

/** The reply being written: placeholder bones until the first word, then the live text plus what it is checking. */
@Composable
private fun LiveBubble(text: String, tool: String?, vis: AmountVisibilityState) {
    val scheme = MaterialTheme.colorScheme
    if (text.isBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Skeleton(loading = true) {
                AssistantBubble("Placeholder answer line one two three four five six seven eight nine ten eleven.", emptyList(), vis, onOpenScreen = {})
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = scheme.primary)
                Text(
                    tool?.let { toolLabel(it) } ?: tr(R.string.assistant_thinking),
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistantBubble(text, emptyList(), vis, onOpenScreen = {})
            if (tool != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = scheme.primary)
                    Text(toolLabel(tool), fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun FailureNote(kind: FailureKind, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val retriable = kind == FailureKind.Offline || kind == FailureKind.Busy || kind == FailureKind.Unavailable
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.errorContainer.copy(alpha = 0.55f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(failureText(kind), color = scheme.onErrorContainer, fontSize = 14.sp, lineHeight = 20.sp)
        if (retriable) AppButton(tr(R.string.assistant_retry), onClick = onRetry, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= 1000) onValueChange(it) },
            modifier = Modifier.weight(1f),
            placeholder = { Text(tr(R.string.assistant_input_hint), fontSize = 14.sp, color = scheme.onSurfaceVariant) },
            maxLines = 4,
            shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = scheme.onSurface,
                unfocusedTextColor = scheme.onSurface,
                focusedBorderColor = scheme.primary,
                unfocusedBorderColor = scheme.outline,
                cursorColor = scheme.primary,
            ),
        )
        if (busy) {
            AppIconButton(Icons.Default.Stop, tr(R.string.assistant_stop), onClick = onStop, variant = ButtonVariant.Tonal)
        } else {
            AppIconButton(
                Icons.AutoMirrored.Filled.Send,
                tr(R.string.assistant_send),
                onClick = onSend,
                variant = ButtonVariant.Primary,
                enabled = value.isNotBlank(),
            )
        }
    }
}

// ── Label lookups (read at draw time so they follow the app language) ────────

private fun fastText(reply: FastReply): String = when (reply) {
    FastReply.Greeting -> tr(R.string.assistant_fast_greeting)
    FastReply.Thanks -> tr(R.string.assistant_fast_thanks)
    FastReply.Capabilities -> tr(R.string.assistant_fast_capabilities)
}

private fun toolLabel(name: String): String = tr(
    when (name) {
        "search_help" -> R.string.assistant_tool_help
        "get_balance" -> R.string.assistant_tool_balance
        "get_spending_summary" -> R.string.assistant_tool_spending
        "search_transactions" -> R.string.assistant_tool_transactions
        "get_budget_status" -> R.string.assistant_tool_budget
        "list_holds" -> R.string.assistant_tool_holds
        "get_top_merchants" -> R.string.assistant_tool_merchants
        "get_settings" -> R.string.assistant_tool_settings
        else -> R.string.assistant_tool_generic
    },
)

internal fun screenLabel(screen: String): String? = when (screen) {
    "home" -> tr(R.string.assistant_open_home)
    "analytics" -> tr(R.string.assistant_open_analytics)
    "budget" -> tr(R.string.assistant_open_budget)
    "profile" -> tr(R.string.assistant_open_profile)
    "holds" -> tr(R.string.assistant_open_holds)
    "add_transaction" -> tr(R.string.assistant_open_add)
    else -> null
}

private fun failureText(kind: FailureKind): String = tr(
    when (kind) {
        FailureKind.Offline -> R.string.assistant_err_offline
        FailureKind.Busy -> R.string.assistant_err_busy
        FailureKind.Unavailable -> R.string.assistant_err_unavailable
        FailureKind.NotConfigured -> R.string.assistant_err_not_configured
        FailureKind.Refused -> R.string.assistant_err_refused
        FailureKind.SignedOut -> R.string.assistant_err_signin
    },
)
