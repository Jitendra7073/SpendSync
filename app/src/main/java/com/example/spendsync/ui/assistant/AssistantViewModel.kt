package com.example.spendsync.ui.assistant

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.spendsync.R
import com.example.spendsync.data.assistant.ActivityStep
import com.example.spendsync.data.assistant.AssistantFastPath
import com.example.spendsync.data.assistant.AssistantRepository
import com.example.spendsync.data.assistant.ChatStore
import com.example.spendsync.data.assistant.ChatTurn
import com.example.spendsync.data.assistant.ConversationSummary
import com.example.spendsync.data.assistant.FailureKind
import com.example.spendsync.data.assistant.FastReply
import com.example.spendsync.data.assistant.Proposal
import com.example.spendsync.data.assistant.ProposalExecutor
import com.example.spendsync.data.assistant.ReplyState
import com.example.spendsync.data.assistant.StoredMessage
import com.example.spendsync.data.assistant.SupportContext
import com.example.spendsync.data.assistant.SupportTicketSummary
import com.example.spendsync.data.assistant.reduceReply
import com.example.spendsync.data.local.SessionDataStore
import com.example.spendsync.ui.i18n.LanguageManager
import com.example.spendsync.ui.i18n.tr
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** A message on screen. Negative ids are in-flight messages not saved yet. */
data class UiMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val actions: List<String> = emptyList(),
    /** An instant local reply, kept as a type so it is shown in the language active right now. */
    val fast: FastReply? = null,
    /** Who wrote it, shown under the answer. */
    val source: String? = null,
    val offline: Boolean = false,
    val proposals: List<Proposal> = emptyList(),
    val followUpCards: List<com.example.spendsync.data.assistant.FollowUpProposal> = emptyList(),
    /** The message that was just written (or is being written) types out; older ones show at once. */
    val animate: Boolean = false,
    /** True for the in-flight reply, so the screen can show the "thinking" state until text arrives. */
    val live: Boolean = false,
    val tool: String? = null,
    /** How long the answer took. */
    val elapsedMs: Long = 0,
    /** "up", "down" or "". */
    val feedback: String = "",
    /** What the assistant did to produce this answer. */
    val steps: List<ActivityStep> = emptyList(),
) {
    /** Same key for the live reply and the message it turns into, so the typing animation carries over. */
    val key: Any get() = if (animate) "reply" else id
}

sealed interface ProposalStatus {
    data object Saving : ProposalStatus
    data object Saved : ProposalStatus
    data object Dismissed : ProposalStatus
    data class Failed(val reason: String) : ProposalStatus
}

data class AssistantUiState(
    val loaded: Boolean = false,
    val messages: List<UiMessage> = emptyList(),
    /** The reply being streamed right now, if any. */
    val live: ReplyState? = null,
    val suggestions: List<String> = emptyList(),
    val failure: FailureKind? = null,
    val lastQuestion: String? = null,
    /** Confirm-card state, keyed by "<messageId>:<index>". */
    val proposalStatus: Map<String, ProposalStatus> = emptyMap(),
    /** Saved chats, filled when the history sheet opens. */
    val history: List<ConversationSummary> = emptyList(),
    val conversationId: String = "",
    /** Wall-clock start of the in-flight question, so the screen can show a running timer. */
    val askedAtMs: Long = 0,
) {
    val busy: Boolean get() = live != null
}

/**
 * Holds the conversation. History lives in the on-device [ChatStore] (many chats per user); the server
 * is stateless, so each question is sent with the last few turns. A reply streams into
 * [AssistantUiState.live] and is saved, with how long it took, once it is complete.
 */
class AssistantViewModel(
    private val repository: AssistantRepository,
    private val store: ChatStore,
    private val session: SessionDataStore,
    private val executor: ProposalExecutor,
    private val onEntrySaved: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    private var userId = ""
    private var conversationId = newId()
    private var job: Job? = null
    private var temp = -1L
    private var startedAt = 0L

    fun load() {
        if (_state.value.loaded) return
        viewModelScope.launch {
            userId = session.userId.first().orEmpty()
            conversationId = store.latestConversation(userId) ?: newId()
            val saved = store.messages(userId, conversationId).map { it.toUi() }
            _state.update { it.copy(loaded = true, messages = saved, conversationId = conversationId) }
        }
    }

    fun send(text: String, screen: String?) = ask(text, screen, resend = false)

    private fun ask(text: String, screen: String?, resend: Boolean) {
        val question = text.trim()
        if (question.isEmpty() || _state.value.busy) return
        _state.update {
            it.copy(
                failure = null, suggestions = emptyList(), lastQuestion = question,
                messages = it.messages.map { m -> m.copy(animate = false) },
            )
        }

        job = viewModelScope.launch {
            if (!resend) {
                val userMsg = UiMessage(store.add(userId, conversationId, "user", question), fromUser = true, text = question)
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

            startedAt = SystemClock.elapsedRealtime()
            _state.update { it.copy(live = ReplyState(), askedAtMs = System.currentTimeMillis()) }
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

    fun confirm(key: String, proposal: Proposal) {
        if (_state.value.proposalStatus[key] != null) return
        _state.update { it.copy(proposalStatus = it.proposalStatus + (key to ProposalStatus.Saving)) }
        viewModelScope.launch {
            val error = runCatching { executor.execute(proposal) }.getOrElse { it.message ?: "error" }
            _state.update {
                it.copy(proposalStatus = it.proposalStatus + (key to (if (error == null) ProposalStatus.Saved else ProposalStatus.Failed(error))))
            }
            if (error == null) onEntrySaved()
        }
    }

    /** After a failure the card goes back to asking, so the user can try again. */
    fun retryProposal(key: String) = _state.update { it.copy(proposalStatus = it.proposalStatus - key) }

    fun dismissProposal(key: String) = _state.update { it.copy(proposalStatus = it.proposalStatus + (key to ProposalStatus.Dismissed)) }

    /** Stops the model mid-answer (cancelling the network call) and keeps what was already written. */
    fun stop() {
        job?.cancel()
    }

    fun retry(screen: String?) {
        val q = _state.value.lastQuestion ?: return
        // The question is already in history; ask again without adding it a second time.
        ask(q, screen, resend = true)
    }

    // ── Chats ────────────────────────────────────────────────────────────────

    fun newChat() {
        job?.cancel()
        conversationId = newId()
        _state.update { AssistantUiState(loaded = true, history = it.history, conversationId = conversationId) }
    }

    fun loadHistory() {
        viewModelScope.launch { _state.update { it.copy(history = store.conversations(userId)) } }
    }

    fun openChat(id: String) {
        job?.cancel()
        viewModelScope.launch {
            conversationId = id
            val saved = store.messages(userId, id).map { it.toUi() }
            _state.update { AssistantUiState(loaded = true, messages = saved, history = it.history, conversationId = id) }
        }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch {
            store.deleteConversation(userId, id)
            if (id == conversationId) newChat()
            _state.update { it.copy(history = store.conversations(userId)) }
        }
    }

    fun clearAll() {
        job?.cancel()
        viewModelScope.launch {
            store.clear(userId)
            conversationId = newId()
            _state.update { AssistantUiState(loaded = true, conversationId = conversationId) }
        }
    }

    // ── Edit and feedback ────────────────────────────────────────────────────

    /**
     * Takes the user's question back so it can be changed: it and everything after it are removed from
     * the chat, and [onText] gets the original wording to put back in the input box.
     */
    fun edit(messageId: Long, onText: (String) -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            val list = _state.value.messages
            val i = list.indexOfFirst { it.id == messageId && it.fromUser }
            if (i < 0) return@launch
            val text = list[i].text
            if (messageId > 0) store.deleteFrom(userId, conversationId, messageId)
            _state.update { it.copy(messages = list.take(i), suggestions = emptyList(), failure = null) }
            onText(text)
        }
    }

    /** Thumbs up/down. A thumbs-down comes with reason codes and an optional note. */
    fun rate(message: UiMessage, rating: String, reasons: List<String> = emptyList(), comment: String = "") {
        viewModelScope.launch {
            if (message.id > 0) store.setFeedback(userId, message.id, rating)
            _state.update { s -> s.copy(messages = s.messages.map { if (it.id == message.id) it.copy(feedback = rating) else it }) }
            val list = _state.value.messages
            val at = list.indexOfFirst { it.id == message.id }
            val question = list.take(at.coerceAtLeast(0)).lastOrNull { it.fromUser }?.text.orEmpty()
            repository.sendFeedback(conversationId, message.id.toString(), rating, reasons, comment, question, message.text, message.source.orEmpty())
        }
    }

    // ── Support ──────────────────────────────────────────────────────────────

    suspend fun tickets(): List<SupportTicketSummary>? = repository.tickets()

    /** Sends a report with app and device details, and (if the user agreed) the recent chat. Returns the reference. */
    suspend fun submitTicket(category: String, message: String, includeChat: Boolean, screen: String?): String? {
        val (device, os, version) = deviceSummary()
        val chat = if (includeChat) {
            _state.value.messages.filter { it.text.isNotBlank() && it.fast == null }.takeLast(8)
                .map { ChatTurn(if (it.fromUser) "user" else "assistant", it.text) }
        } else emptyList()
        return repository.submitTicket(category, message, SupportContext(version, device, os, currentLanguageName(), screen, chat))
    }

    private suspend fun finishReply() {
        val reply = _state.value.live ?: return
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val text = reply.text.ifBlank { if (reply.proposals.isNotEmpty() || reply.followUpCards.isNotEmpty()) tr(R.string.asst_proposal_fallback) else "" }
        val saved = if (text.isNotBlank()) {
            UiMessage(
                store.add(userId, conversationId, "assistant", text, reply.actions, elapsed, reply.source.orEmpty()),
                false, text, reply.actions,
                source = reply.source, offline = reply.offline, proposals = reply.proposals, followUpCards = reply.followUpCards, animate = true,
                elapsedMs = elapsed, steps = reply.steps,
            )
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

    private fun StoredMessage.toUi() = UiMessage(
        id = id, fromUser = role == "user", text = text, actions = actions,
        source = model.ifBlank { null }, elapsedMs = elapsedMs, feedback = feedback,
    )

    companion object {
        private const val HISTORY_TURNS = 12
        private const val MAX_TURN_CHARS = 3500

        private fun newId() = UUID.randomUUID().toString()

        fun factory(repository: AssistantRepository, store: ChatStore, session: SessionDataStore, executor: ProposalExecutor, onEntrySaved: () -> Unit = {}) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AssistantViewModel(repository, store, session, executor, onEntrySaved) as T
            }
    }
}
