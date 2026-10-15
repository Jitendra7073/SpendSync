package com.example.spendsync.ui.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.spendsync.R
import com.example.spendsync.data.assistant.FastReply
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.ui.components.AppButton
import com.example.spendsync.ui.components.AppDialog
import com.example.spendsync.ui.components.ButtonSize
import com.example.spendsync.ui.components.ButtonVariant
import com.example.spendsync.ui.components.DialogAction
import com.example.spendsync.ui.components.Icon
import com.example.spendsync.ui.components.Text
import com.example.spendsync.ui.i18n.LanguageManager
import com.example.spendsync.ui.i18n.tr
import com.example.spendsync.ui.shared.AmountVisibilityState
import com.example.spendsync.voice.ListenError
import com.example.spendsync.voice.SentenceFeed
import com.example.spendsync.voice.SpeechInput
import com.example.spendsync.voice.SpeechOutput
import com.example.spendsync.voice.VoiceEvent
import com.example.spendsync.voice.VoicePhase
import com.example.spendsync.voice.VoicePrefs
import com.example.spendsync.voice.VoiceState
import com.example.spendsync.voice.reduceVoice
import com.example.spendsync.voice.speechLocaleFor
import com.example.spendsync.voice.toSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Talking to the assistant: dictation into the input box, hands-free voice mode, and "Read aloud".
 * It only calls [AssistantViewModel.send] and reads its state, so the chat itself is unchanged. Speech
 * goes through the same masking as the screen ([toSpeech]). The mic is on only while the UI says so.
 */
class VoiceController(
    private val context: Context,
    private val viewModel: AssistantViewModel,
    private val vis: AmountVisibilityState,
    private val scope: CoroutineScope,
    private val currentScreen: () -> String?,
) {
    private val input = SpeechInput(context)
    var ttsReady by mutableStateOf<Boolean?>(null)
        private set
    private val output = SpeechOutput(context) { ttsReady = it }

    var prefs = VoicePrefs()
    var state by mutableStateOf(VoiceState())
        private set
    /** What the recogniser heard so far (caption under the status). */
    var heard by mutableStateOf("")
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var dictating by mutableStateOf(false)
        private set
    /** Key of the message being read aloud, if any. */
    var reading by mutableStateOf<Any?>(null)
        private set

    val canListen: Boolean = input.available
    val active: Boolean get() = state.phase != VoicePhase.Off

    // The reply being spoken in voice mode.
    private var feed = SentenceFeed()
    private var following = false
    private var sawLive = false
    private var baseCount = 0
    private var replyDone = false
    private var pauseAfter = false

    init {
        output.onIdle = {
            reading = null
            if (replyDone) finishTurn()
        }
        output.onMissingLanguage = { locale ->
            toast(tr(R.string.voice_missing_voice, locale.getDisplayLanguage(LanguageManager.current.locale)))
        }
        scope.launch { viewModel.state.collect { onChat(it) } }
    }

    // ── Voice mode ───────────────────────────────────────────────────────────

    fun open() {
        stopDictation()
        stopReading()
        if (ttsReady == false) toast(tr(R.string.voice_no_tts))
        state = VoiceState(keepListening = prefs.keepListening)
        dispatch(VoiceEvent.Open)
    }

    fun tap() = dispatch(VoiceEvent.Tap)

    /** Thinking: cancel the answer and pause. */
    fun cancel() {
        viewModel.stop()
        dispatch(VoiceEvent.Background)
    }

    fun end() = dispatch(VoiceEvent.End)

    /** The screen or app went away: everything audio stops. */
    fun background() {
        if (active) dispatch(VoiceEvent.Background)
        stopDictation()
        stopReading()
    }

    fun release() {
        input.release()
        output.release()
    }

    private fun dispatch(e: VoiceEvent) {
        val old = state.phase
        state = reduceVoice(state, e)
        val now = state.phase
        if (now != VoicePhase.Speaking && now != VoicePhase.Thinking) {
            following = false
            replyDone = false
            output.stop()
        }
        if (old == VoicePhase.Listening && now != VoicePhase.Listening) input.stop()
        if (now == VoicePhase.Listening && (old != VoicePhase.Listening || e == VoiceEvent.Missed)) listen()
        if (now == VoicePhase.Off) heard = ""
        level = 0f
    }

    private fun listen() {
        heard = ""
        input.start(
            prefs.listenLocale(LanguageManager.current),
            onPartial = { heard = it },
            onFinal = { text -> heard = text; ask(text) },
            onError = { err ->
                if (err == ListenError.Missed) dispatch(VoiceEvent.Missed)
                else { explain(err); dispatch(VoiceEvent.Background) }
            },
            onLevel = { level = it },
        )
    }

    private fun ask(text: String) {
        dispatch(VoiceEvent.Heard)
        scope.launch {
            // Interrupted mid-answer: finish that one first (Stop keeps what was written).
            if (viewModel.state.value.busy) {
                viewModel.stop()
                viewModel.state.first { !it.busy }
            }
            if (state.phase != VoicePhase.Thinking) return@launch
            feed = SentenceFeed()
            sawLive = false
            replyDone = false
            baseCount = viewModel.state.value.messages.size
            following = true
            viewModel.send(text, currentScreen())
        }
    }

    private fun onChat(s: AssistantUiState) {
        if (!following) return
        val live = s.live
        if (live != null) {
            sawLive = true
            val f = feed.update(live.text)
            if (f.reset) output.stop()
            speakAll(f.sentences)
            return
        }
        // Done: either a streamed answer was saved, or an instant on-phone answer was added.
        if (!sawLive && s.messages.size < baseCount + 2) return
        following = false
        val last = s.messages.lastOrNull()?.takeIf { !it.fromUser }
        val text = last?.let { it.fast?.let(::fastText) ?: it.text }.orEmpty()
        speakAll(feed.finish(text).sentences)
        val card = last != null && (last.proposals.isNotEmpty() || last.followUpCards.isNotEmpty() ||
            last.composeCards.isNotEmpty() || last.billCards.isNotEmpty() || last.emailCards.isNotEmpty())
        if (card) speakAll(listOf(tr(R.string.voice_card_on_screen)))
        pauseAfter = card || last == null || s.failure != null
        replyDone = true
        if (!output.speaking) finishTurn()
    }

    private fun finishTurn() {
        replyDone = false
        dispatch(VoiceEvent.Finished(pauseAfter))
    }

    private fun speakAll(sentences: List<String>) {
        sentences.forEach { sentence ->
            val spoken = toSpeech(sentence, vis.isMaskingEnabled, vis.isVisible, tr(R.string.voice_amount_hidden))
            val locale = speechLocaleFor(spoken, prefs.listenLocale(LanguageManager.current))
            if (output.speak(spoken, locale, prefs) && state.phase == VoicePhase.Thinking) dispatch(VoiceEvent.Speaking)
        }
    }

    // ── Dictation and read aloud ─────────────────────────────────────────────

    /** Fills the input box live: [current] text plus what is heard. */
    fun dictate(current: String, onText: (String) -> Unit) {
        if (active) return
        if (dictating) return stopDictation()
        stopReading()
        dictating = true
        val base = current.trimEnd().let { if (it.isEmpty()) it else "$it " }
        input.start(
            prefs.listenLocale(LanguageManager.current),
            onPartial = { onText(base + it) },
            onFinal = { onText(base + it); dictating = false },
            onError = { err -> dictating = false; if (err != ListenError.Missed) explain(err) },
        )
    }

    fun stopDictation() {
        if (!dictating) return
        dictating = false
        input.stop()
    }

    /** Reads one answer; tapping again (or another answer) stops it. */
    fun readAloud(key: Any, text: String) {
        if (active) return
        val again = reading == key
        stopReading()
        if (again) return
        if (ttsReady != true) return toast(tr(R.string.voice_no_tts))
        reading = key
        speakAll(SentenceFeed().finish(text).sentences)
        if (!output.speaking) reading = null
    }

    private fun stopReading() {
        if (reading == null) return
        reading = null
        output.stop()
    }

    private fun explain(err: ListenError) = toast(
        tr(
            when (err) {
                ListenError.Permission -> R.string.voice_perm_denied
                ListenError.Language -> R.string.voice_err_language
                ListenError.Network -> R.string.voice_err_network
                ListenError.Missed, ListenError.Other -> R.string.voice_err_other
            },
        ),
    )

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

@Composable
fun rememberVoiceController(
    viewModel: AssistantViewModel,
    sessionDataStore: SessionDataStore,
    vis: AmountVisibilityState,
    currentScreen: String?,
): VoiceController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screen by rememberUpdatedState(currentScreen)
    val controller = remember(viewModel) { VoiceController(context, viewModel, vis, scope) { screen } }
    DisposableEffect(controller) { onDispose { controller.release() } }
    val prefs by sessionDataStore.voicePrefs.collectAsState(initial = VoicePrefs())
    LaunchedEffect(prefs) { controller.prefs = prefs }

    // Leaving the screen or the app turns the mic off and stops speaking.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.background() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return controller
}

/**
 * Runs [action] once the microphone may be used. First time: our own explanation, then the system
 * prompt. Turned off for good: a dialog that opens app settings.
 */
@Composable
fun rememberMicGate(): (action: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var explain by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        if (granted) action?.invoke()
        else if (context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false) blocked = true
    }

    if (explain) {
        AppDialog(
            onDismiss = { explain = false; pending = null },
            title = tr(R.string.voice_perm_title),
            message = tr(R.string.voice_perm_body),
            icon = Icons.Default.Mic,
            primary = DialogAction(tr(R.string.voice_perm_allow), { explain = false; launcher.launch(Manifest.permission.RECORD_AUDIO) }),
            secondary = DialogAction(tr(R.string.cancel), { explain = false; pending = null }),
        )
    }
    if (blocked) {
        AppDialog(
            onDismiss = { blocked = false },
            title = tr(R.string.voice_perm_title),
            message = tr(R.string.voice_perm_denied),
            icon = Icons.Default.Mic,
            primary = DialogAction(tr(R.string.voice_open_settings), {
                blocked = false
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }),
            secondary = DialogAction(tr(R.string.cancel), { blocked = false }),
        )
    }
    return { action ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action()
        else { pending = action; explain = true }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Voice mode: takes the input bar's place, the chat (and its cards) stays visible above. */
@Composable
fun VoicePanel(voice: VoiceController) {
    val scheme = MaterialTheme.colorScheme
    val phase = voice.state.phase
    val status = tr(
        when (phase) {
            VoicePhase.Listening -> R.string.voice_listening
            VoicePhase.Thinking -> R.string.voice_thinking
            VoicePhase.Speaking -> R.string.voice_speaking
            else -> R.string.voice_paused
        },
    )
    val pulse by animateFloatAsState(if (phase == VoicePhase.Listening) 1f + voice.level * 0.45f else 1f, label = "level")
    Column(
        Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .navigationBarsPadding()
            .clickable(role = Role.Button, onClickLabel = status, onClick = voice::tap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).scale(pulse).clip(CircleShape)
                    .background(if (phase == VoicePhase.Paused) scheme.surfaceVariant else scheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (phase == VoicePhase.Speaking) Icons.Default.GraphicEq else Icons.Default.Mic,
                    contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp),
                )
            }
            Text(
                status, fontSize = 15.sp, color = scheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (voice.heard.isNotBlank() && phase != VoicePhase.Paused) {
            Text("“${voice.heard}”", fontSize = 14.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (phase) {
                VoicePhase.Thinking -> AppButton(tr(R.string.assistant_stop), onClick = voice::cancel, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
                VoicePhase.Listening -> AppButton(tr(R.string.voice_pause), onClick = voice::tap, variant = ButtonVariant.Tonal, size = ButtonSize.Small)
                else -> AppButton(tr(R.string.voice_talk), onClick = voice::tap, variant = ButtonVariant.Tonal, size = ButtonSize.Small, leadingIcon = Icons.Default.Mic)
            }
            AppButton(tr(R.string.voice_end), onClick = voice::end, variant = ButtonVariant.Outline, size = ButtonSize.Small)
        }
    }
}
