package com.example.spendsync.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.spendsync.data.assistant.AssistantFastPath
import com.example.spendsync.data.assistant.AssistantRepository
import com.example.spendsync.data.assistant.ChatStore
import com.example.spendsync.data.assistant.ChatTurn
import com.example.spendsync.data.assistant.FailureKind
import com.example.spendsync.data.assistant.FastReply
import com.example.spendsync.data.assistant.ReplyState
import com.example.spendsync.data.assistant.reduceReply
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.ui.i18n.LanguageManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** A message on screen. Negative ids are in-flight messages not saved yet. */
data class UiMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val actions: List<String> = emptyList(),
    /** An instant local reply, kept as a type so it is shown in the language active right now. */
    val fast: FastReply? = null,
    /** Who wrote it, shown under the answer. Not saved with the chat. */
    val source: String? = null,
    val offline: Boolean = false,
)

data class AssistantUiState(
    val loaded: Boolean = false,
    val messages: List<UiMessage> = emptyList(),
    /** The reply being streamed right now, if any. */
    val live: ReplyState? = null,
    val suggestions: List<String> = emptyList(),
    val failure: FailureKind? = null,
    val lastQuestion: String? = null,
) {
    val busy: Boolean get() = live != null
}

/**
 * Holds the conversation. History lives in the on-device [ChatStore]; the server is stateless, so each
 * question is sent with the last few turns. A reply streams into [AssistantUiState.live] and is saved
 * once it is complete.
 */
class AssistantViewModel(
    private val repository: AssistantRepository,
    private val store: ChatStore,
    private val session: SessionDataStore,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    private var userId = ""
    private var conversationId = UUID.randomUUID().toString()
    private var job: Job? = null
    private var temp = -1L

    fun load() {
        if (_state.value.loaded) return
        viewModelScope.launch {
            userId = session.userId.first().orEmpty()
            val saved = store.recent(userId).map {
                UiMessage(it.id, it.role == "user", it.text, it.actions)
            }
            _state.update { it.copy(loaded = true, messages = saved) }
        }
    }

    fun send(text: String, screen: String?) = ask(text, screen, resend = false)

    private fun ask(text: String, screen: String?, resend: Boolean) {
        val question = text.trim()
        if (question.isEmpty() || _state.value.busy) return
        _state.update { it.copy(failure = null, suggestions = emptyList(), lastQuestion = question) }

        job = viewModelScope.launch {
            if (!resend) {
                val userMsg = UiMessage(store.add(userId, "user", question), fromUser = true, text = question)
                _state.update { it.copy(messages = it.messages + userMsg) }
            }

            // Greetings, thanks and "what can you do" are answered on the phone: instant, free, offline.
            AssistantFastPath.match(question)?.let { fast ->
                val msg = UiMessage(temp--, fromUser = false, text = "", fast = fast)
                _state.update { it.copy(messages = it.messages + msg, suggestions = emptyList()) }
                return@launch
            }

            val turns = _state.value.messages
                .filter { it.fast == null && it.text.isNotBlank() }
                .takeLast(HISTORY_TURNS)
                .map { ChatTurn(if (it.fromUser) "user" else "assistant", it.text.take(MAX_TURN_CHARS)) }

            _state.update { it.copy(live = ReplyState()) }
            try {
                repository
                    .chat(turns, LanguageManager.current.storedName, screen, conversationId, session.assistantPrefs.first())
                    .collect { event -> _state.update { s -> s.copy(live = reduceReply(s.live ?: ReplyState(), event)) } }
            } finally {
                // Runs even when "Stop" cancelled us, so the partial answer is still kept.
                withContext(NonCancellable) { finishReply() }
            }
        }
    }

    /** Stops the model mid-answer (cancelling the network call) and keeps what was already written. */
    fun stop() {
        job?.cancel()
    }

    fun retry(screen: String?) {
        val q = _state.value.lastQuestion ?: return
        // The question is already in history; ask again without adding it a second time.
        ask(q, screen, resend = true)
    }

    fun clear() {
        job?.cancel()
        viewModelScope.launch {
            store.clear(userId)
            conversationId = UUID.randomUUID().toString()
            _state.update { AssistantUiState(loaded = true) }
        }
    }

    private suspend fun finishReply() {
        val reply = _state.value.live ?: return
        val kept = reply.text.isNotBlank()
        val saved = if (kept) {
            UiMessage(store.add(userId, "assistant", reply.text, reply.actions), false, reply.text, reply.actions, source = reply.source, offline = reply.offline)
        } else null
        _state.update {
            it.copy(
                live = null,
                messages = if (saved != null) it.messages + saved else it.messages,
                suggestions = reply.followups,
                failure = reply.failure,
            )
        }
    }

    companion object {
        private const val HISTORY_TURNS = 12
        private const val MAX_TURN_CHARS = 3500

        fun factory(repository: AssistantRepository, store: ChatStore, session: SessionDataStore) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AssistantViewModel(repository, store, session) as T
            }
    }
}
