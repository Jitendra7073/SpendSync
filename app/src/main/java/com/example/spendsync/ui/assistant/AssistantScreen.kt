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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.foundation.clickable
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import com.example.spendsync.data.assistant.Proposal
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    // An earlier answer chosen with "Use as reference": the next message is a reply to it.
    var replyTo by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var feedbackFor by remember { mutableStateOf<UiMessage?>(null) }
    var sharing by remember { mutableStateOf<String?>(null) }
    val financeRepository = remember(sessionDataStore) { com.example.spendsync.data.repository.FinanceRepository(sessionDataStore) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    fun copy(text: String) {
        // Copying follows the same privacy rule as the screen: hidden amounts stay hidden.
        val safe = maskAmountsInText(text, amountVisibility.isMaskingEnabled, amountVisibility.isVisible).text
        clipboard.setText(androidx.compose.ui.text.AnnotatedString(safe))
        android.widget.Toast.makeText(context, tr(R.string.asst_copied), android.widget.Toast.LENGTH_SHORT).show()
    }

    // Sharing follows the same privacy rule as copying: hidden amounts stay hidden.
    fun share(text: String) {
        val safe = maskAmountsInText(text, amountVisibility.isMaskingEnabled, amountVisibility.isVisible).text
        sharing = markdownToPlain(safe)
    }

    LaunchedEffect(Unit) { viewModel.load() }

    // Keep the newest text in view while it streams.
    val liveLength = state.live?.text?.length ?: 0
    LaunchedEffect(state.messages.size, liveLength, state.live != null, state.suggestions.size) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    val displayed = state.messages + listOfNotNull(
        state.live?.let {
            UiMessage(Long.MIN_VALUE, false, it.text, source = it.source, offline = it.offline, animate = true, live = true, tool = it.tool, steps = it.steps)
        },
    )

    fun submit(text: String) {
        if (text.isBlank()) return
        viewModel.send(text, currentScreen, replyTo)
        input = ""
        replyTo = null
    }

    SettingsBackdrop {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                onBack = onBack,
                onNewChat = { viewModel.newChat(); input = "" },
                onHistory = { viewModel.loadHistory(); showHistory = true },
                onSupport = { showSupport = true },
                canNew = state.messages.isNotEmpty() || state.busy,
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
                    items(displayed, key = { it.key }) { msg ->
                        MessageBubble(
                            msg = msg,
                            financeRepository = financeRepository,
                            sessionDataStore = sessionDataStore,
                            status = state.proposalStatus,
                            vis = amountVisibility,
                            onOpenScreen = { screen -> if (screen == "support") showSupport = true else onOpenScreen(screen) },
                            askedAt = state.askedAtMs,
                            canEdit = !state.busy,
                            onEdit = { m -> viewModel.edit(m.id) { text -> input = text } },
                            onCopy = ::copy,
                            onShare = ::share,
                            onReply = { text -> replyTo = text },
                            onUp = { m -> viewModel.rate(m, "up") },
                            onDown = { m -> feedbackFor = m },
                            onConfirm = viewModel::confirm,
                            onRetry = viewModel::retryProposal,
                            onDismiss = viewModel::dismissProposal,
                            onProgress = { scope.launch { listState.scrollBy(10_000f) } },
                        )
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

            replyTo?.let { ref ->
                ReferenceBar(
                    reference = maskAmountsInText(ref, amountVisibility.isMaskingEnabled, amountVisibility.isVisible).text,
                    busy = state.busy,
                    onClear = { replyTo = null },
                    onQuick = { prompt -> submit(prompt) },
                )
            }
            InputBar(
                hint = if (replyTo != null) tr(R.string.asst_ref_hint) else tr(R.string.assistant_input_hint),
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

    if (showHistory) {
        HistorySheet(
            chats = state.history,
            currentId = state.conversationId,
            onOpen = { viewModel.openChat(it); showHistory = false },
            onDelete = viewModel::deleteChat,
            onClearAll = { confirmClear = true },
            onDismiss = { showHistory = false },
        )
    }
    if (showSupport) {
        SupportSheet(
            hasChat = state.messages.any { it.text.isNotBlank() },
            submit = { category, message, includeChat -> viewModel.submitTicket(category, message, includeChat, currentScreen) },
            loadTickets = { viewModel.tickets() },
            onDismiss = { showSupport = false },
        )
    }
    sharing?.let { text -> com.example.spendsync.ui.share.ShareSheet(text = text, onDismiss = { sharing = null }) }
    feedbackFor?.let { target ->
        FeedbackDialog(
            onDismiss = { feedbackFor = null },
            onSend = { reasons, comment ->
                viewModel.rate(target, "down", reasons, comment)
                feedbackFor = null
                android.widget.Toast.makeText(context, tr(R.string.asst_fb_thanks), android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    }

    if (confirmClear) {
        AppConfirmDialog(
            title = tr(R.string.assistant_clear_title),
            message = tr(R.string.assistant_clear_body),
            confirmLabel = tr(R.string.assistant_clear_confirm),
            cancelLabel = tr(R.string.cancel),
            destructive = true,
            onConfirm = { viewModel.clearAll(); confirmClear = false; showHistory = false },
            onDismiss = { confirmClear = false },
        )
    }
}

// ── Pieces ───────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(onBack: () -> Unit, onNewChat: () -> Unit, onHistory: () -> Unit, onSupport: () -> Unit, canNew: Boolean) {
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
        AppIconButton(Icons.Default.SupportAgent, tr(R.string.asst_sup_title), onClick = onSupport)
        AppIconButton(Icons.Default.History, tr(R.string.asst_history), onClick = onHistory)
        AppIconButton(Icons.Default.AddComment, tr(R.string.asst_new_chat), onClick = onNewChat, enabled = canNew)
    }
}

@Composable
private fun Welcome(screen: String?, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val starters = when (screen) {
        "analytics" -> listOf(R.string.assistant_starter_categories, R.string.assistant_starter_month, R.string.assistant_starter_balance)
        "budget", "planify" -> listOf(R.string.assistant_starter_budget, R.string.assistant_starter_month, R.string.assistant_starter_howto)
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
private fun MessageBubble(
    msg: UiMessage,
    financeRepository: com.example.spendsync.data.repository.FinanceRepository,
    sessionDataStore: com.example.spendsync.data.local.SessionDataStore,
    status: Map<String, ProposalStatus>,
    vis: AmountVisibilityState,
    onOpenScreen: (String) -> Unit,
    askedAt: Long,
    canEdit: Boolean,
    onEdit: (UiMessage) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onReply: (String) -> Unit,
    onUp: (UiMessage) -> Unit,
    onDown: (UiMessage) -> Unit,
    onConfirm: (String, Proposal) -> Unit,
    onRetry: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onProgress: () -> Unit,
) {
    val full = msg.fast?.let { fastText(it) } ?: msg.text
    if (msg.fromUser) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            UserBubble(full, msg.quote)
            UserMessageActions(onEdit = { onEdit(msg) }, onCopy = { onCopy(full) }, enabled = canEdit)
        }
        return
    }
    if (msg.live && msg.text.isBlank()) {
        ActivityPanel(msg.steps, footer = tr(R.string.asst_working_for, rememberElapsed(askedAt)))
        return
    }
    val shown = rememberTyped(full, msg.animate, onProgress)
    val done = shown.length >= full.length
    var showSteps by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AssistantBubble(
            text = shown,
            actions = if (done) msg.actions else emptyList(),
            vis = vis,
            onOpenScreen = onOpenScreen,
            source = if (done) msg.source else null,
            offline = done && msg.offline,
            elapsedMs = if (done) msg.elapsedMs else 0,
        )
        if (msg.live) {
            // Still working (e.g. checking a tool after the first words): show only what is running.
            val running = msg.steps.filter { it.state == com.example.spendsync.data.assistant.StepState.Running && it.kind != com.example.spendsync.data.assistant.StepKind.Write }
            if (running.isNotEmpty()) ActivityPanel(running)
        }
        if (done && !msg.live) {
            msg.proposals.forEachIndexed { i, proposal ->
                val key = "${msg.id}:$i"
                ProposalCard(proposal, status[key], vis, onConfirm = { onConfirm(key, proposal) }, onRetry = { onRetry(key) }, onDismiss = { onDismiss(key) })
            }
            msg.followUpCards.forEach { f ->
                FollowUpCard(f, financeRepository, sessionDataStore)
            }
            if (msg.fast == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnswerActions(msg.feedback, onCopy = { onCopy(full) }, onUp = { onUp(msg) }, onDown = { onDown(msg) }, onShare = { onShare(full) }, onReply = { onReply(full) })
                    if (msg.steps.size > 1) {
                        Text(
                            tr(if (showSteps) R.string.asst_steps_hide else R.string.asst_steps_show),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showSteps = !showSteps }.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                }
                if (showSteps) ActivityPanel(msg.steps)
            }
        }
    }
}

/**
 * Reveals [full] a few characters at a time, faster when far behind, so every answer types out the
 * same way whether the model streamed it or sent it in one piece. Old messages show at once.
 */
@Composable
private fun rememberTyped(full: String, animate: Boolean, onProgress: () -> Unit): String {
    var count by remember { mutableIntStateOf(if (animate) 0 else full.length) }
    val latest by rememberUpdatedState(full)
    val progress by rememberUpdatedState(onProgress)
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var reported = 0
        while (isActive) {
            val target = latest.length
            if (count > target) count = target
            if (count < target) {
                count = (count + maxOf(1, (target - count) / 14)).coerceAtMost(target)
                if (count - reported >= 24) { reported = count; progress() }
                delay(16)
            } else {
                if (reported != count) { reported = count; progress() }
                delay(50)
            }
        }
    }
    return full.take(count.coerceAtMost(full.length))
}

/** What the assistant prepared. Nothing is saved until Confirm. */
@Composable
private fun ProposalCard(
    p: Proposal,
    status: ProposalStatus?,
    vis: AmountVisibilityState,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val amount = "₹" + java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN")).apply { maximumFractionDigits = 2 }.format(p.amount)
    val shownAmount = maskAmountsInText(amount, vis.isMaskingEnabled, vis.isVisible).text
    val lent = p.kind == "expense"

    @Composable
    fun line(label: String, value: String) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = scheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        }
    }

    Column(
        Modifier
            .widthIn(max = 520.dp)
            .clip(shape)
            .background(scheme.primary.copy(alpha = 0.07f))
            .border(1.dp, scheme.primary.copy(alpha = 0.35f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            tr(if (p.kind == "income") R.string.asst_card_income else R.string.asst_card_expense),
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.primary,
        )
        line(tr(R.string.asst_card_amount), shownAmount)
        line(tr(R.string.asst_card_category), p.category)
        p.note?.takeIf { it.isNotBlank() }?.let { line(tr(R.string.asst_card_note), it) }
        p.person?.let { line(tr(if (lent) R.string.asst_card_gave_to else R.string.asst_card_took_from), it) }
        p.returnDate?.let { line(tr(R.string.asst_card_return_on), it) }
        line(tr(R.string.asst_card_date), p.date)

        when (status) {
            null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(tr(R.string.asst_card_confirm), onClick = onConfirm, size = ButtonSize.Small)
                AppButton(tr(R.string.asst_card_dismiss), onClick = onDismiss, variant = ButtonVariant.Outline, size = ButtonSize.Small)
            }
            ProposalStatus.Saving -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = scheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(tr(R.string.asst_card_saving), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            }
            ProposalStatus.Saved -> Text(tr(R.string.asst_card_saved), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = scheme.primary)
            ProposalStatus.Dismissed -> Text(tr(R.string.asst_card_skipped), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            is ProposalStatus.Failed -> {
                Text(tr(R.string.asst_card_failed, status.reason), fontSize = 13.sp, color = scheme.error)
                AppButton(tr(R.string.asst_card_retry), onClick = onRetry, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String, quote: String = "") {
    val scheme = MaterialTheme.colorScheme
    val who = tr(R.string.assistant_a11y_you)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .semantics { contentDescription = "$who: $text" }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(scheme.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (quote.isNotBlank()) {
                Row(Modifier.clip(RoundedCornerShape(10.dp)).background(scheme.onPrimary.copy(alpha = 0.16f)).padding(8.dp)) {
                    Box(Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(scheme.onPrimary.copy(alpha = 0.7f)))
                    Text(
                        markdownToPlain(quote), color = scheme.onPrimary.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp,
                        maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Text(text, color = scheme.onPrimary, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

/** The answer being replied to, shown above the input like a quoted message, with a few one-tap follow-ups. */
@Composable
private fun ReferenceBar(reference: String, busy: Boolean, onClear: () -> Unit, onQuick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(scheme.surface).padding(horizontal = 12.dp).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.primary.copy(alpha = 0.08f)).padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(3.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(scheme.primary))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(tr(R.string.asst_replying_to), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                Text(markdownToPlain(reference), fontSize = 12.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            AppIconButton(Icons.Default.Close, tr(R.string.asst_reply_remove), onClick = onClear)
        }
        if (!busy) {
            val prompts = listOf(tr(R.string.asst_ref_explain), tr(R.string.asst_ref_short), tr(R.string.asst_ref_compare))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(prompts) { p -> AppChip(p, selected = false, onClick = { onQuick(p) }, role = androidx.compose.ui.semantics.Role.Button) }
            }
        }
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
    elapsedMs: Long = 0,
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
            MarkdownText(masked.text, color = scheme.onSurface)
            if (masked.hidSomething) {
                AppButton(tr(R.string.show_amounts), onClick = { vis.requestUnlock() }, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
            }
            val who = if (offline) tr(R.string.asst_answered_offline) else source?.let { tr(R.string.asst_answered_by, it) }
            val caption = listOfNotNull(who, if (elapsedMs > 0) com.example.spendsync.data.assistant.formatElapsed(elapsedMs) else null).joinToString(" · ")
            if (caption.isNotEmpty()) Text(caption, fontSize = 11.sp, color = scheme.onSurfaceVariant)
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
    hint: String,
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
            placeholder = { Text(hint, fontSize = 14.sp, color = scheme.onSurfaceVariant) },
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

internal fun toolLabel(name: String): String = tr(
    when (name) {
        "search_help" -> R.string.assistant_tool_help
        "get_balance" -> R.string.assistant_tool_balance
        "get_spending_summary" -> R.string.assistant_tool_spending
        "search_transactions" -> R.string.assistant_tool_transactions
        "get_budget_status" -> R.string.assistant_tool_budget
        "get_plan_status" -> R.string.assistant_tool_plan
        "prepare_followup" -> R.string.assistant_tool_followup
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
    "planify" -> tr(R.string.assistant_open_planify)
    "profile" -> tr(R.string.assistant_open_profile)
    "holds" -> tr(R.string.assistant_open_holds)
    "add_transaction" -> tr(R.string.assistant_open_add)
    "support" -> tr(R.string.asst_sup_title)
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

/**
 * A follow-up the assistant started. The assistant never sees phone numbers and never sends: this card writes the
 * message on the phone, shows it, and only opens WhatsApp / SMS / email after "Yes, send".
 */
@Composable
private fun FollowUpCard(
    f: com.example.spendsync.data.assistant.FollowUpProposal,
    financeRepository: com.example.spendsync.data.repository.FinanceRepository,
    sessionDataStore: com.example.spendsync.data.local.SessionDataStore,
) {
    val scheme = MaterialTheme.colorScheme
    var closed by remember { mutableStateOf(false) }
    if (closed) return
    val target = remember(f) { com.example.spendsync.data.holds.FollowUpTarget(f.person, f.direction, f.amount, f.dueDate, f.overdueDays) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface)
            .border(0.5.dp, scheme.outlineVariant, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tr(R.string.fu_title, f.person), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        com.example.spendsync.ui.holds.FollowUpPanel(
            target = target,
            financeRepository = financeRepository,
            sessionDataStore = sessionDataStore,
            aiAllowed = true, // the assistant itself only runs with consent
            onDone = { closed = true },
            autoStart = true,
            initialChannel = com.example.spendsync.data.holds.Channel.entries.firstOrNull { it.id == f.channel } ?: com.example.spendsync.data.holds.Channel.WhatsApp,
            initialTone = com.example.spendsync.data.holds.Tone.entries.firstOrNull { it.id == f.tone } ?: com.example.spendsync.data.holds.Tone.Friendly,
            initialContext = f.context.orEmpty(),
        )
    }
}
