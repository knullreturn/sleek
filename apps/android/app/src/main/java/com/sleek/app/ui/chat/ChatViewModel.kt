package com.sleek.app.ui.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleek.app.data.local.SettingsDataStore
import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.model.Message
import com.sleek.app.data.model.User
import com.sleek.app.data.remote.ApiService
import com.sleek.app.data.remote.SocketEvent
import com.sleek.app.data.remote.SocketManager
import com.sleek.app.data.repository.MessageRepository
import com.sleek.app.notification.NotificationHelper
import com.sleek.app.ui.chat.groupByDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class ChatUiState(
    val messages:        List<Message>   = emptyList(),
    val grouped:         MessageGroups   = MessageGroups(),
    val peerHasReplied:  Boolean         = false,
    val isLoading:       Boolean         = true,
    val typingUsers:     List<String>    = emptyList(),
    val seenUpToId:      String?         = null,
    val peer:            User?           = null,
    val peerOnline:      Boolean         = false,
    val peerSleeping:    Boolean         = false,   // peer has sleep mode enabled
    val hasMoreMessages: Boolean         = false,
    val isLoadingOlder:  Boolean         = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val apiService:        ApiService,
    private val socketManager:     SocketManager,
    private val tokenDataStore:    TokenDataStore,
    private val messageRepository: MessageRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    val myUserId: Flow<String?> = tokenDataStore.userId

    /** Cached once — prevents spawning a new coroutine per message emission */
    private var cachedMyId: String? = null

    /** Cancels ALL flows from the previous chat when switching */
    private var chatJob: Job? = null

    /** Single socket observer — started once, filters by currentChatId */
    private var socketJob: Job? = null

    /**
     * Scroll position memory — LRU(20).
     * Stores (firstVisibleItemIndex, firstVisibleItemScrollOffset) per chatId.
     * Saved when user leaves chat, restored on re-open.
     */
    private val scrollPositions = object : LinkedHashMap<String, Pair<Int, Int>>(21, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Int, Int>>?) =
            size > 20
    }

    fun saveScrollPosition(chatId: String, index: Int, offset: Int) {
        scrollPositions[chatId] = index to offset
    }

    fun restoreScrollPosition(chatId: String): Pair<Int, Int>? = scrollPositions[chatId]

    /** Pre-warm: start the Room flow early (called on press-down, 80ms guard in UI) */
    fun preloadChat(chatId: String) {
        if (currentChatId == chatId) return  // already loaded
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.observeLatestMessages(chatId, 60).first()  // prime windowed query
        }
    }

    /** Scroll-up pagination: load the page of messages before the oldest visible one */
    fun loadOlderMessages() {
        val chatId = currentChatId.ifEmpty { return }
        val oldest = _state.value.messages.firstOrNull()?.createdAt ?: return
        if (_state.value.isLoadingOlder) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingOlder = true) }
            val older = withContext(Dispatchers.IO) {
                messageRepository.loadMessagesBefore(chatId, oldest, limit = 40)
            }
            val current = _state.value.messages
            val merged  = (older + current).distinctBy { it.id }
            val (grouped, _, _) = withContext(Dispatchers.Default) {
                val g = groupByDate(merged)
                val p = merged.firstOrNull { it.senderId != cachedMyId }?.sender
                val r = merged.lastOrNull()?.senderId != cachedMyId
                Triple(g, p, r)
            }
            _state.update { s ->
                s.copy(
                    messages       = merged,
                    grouped        = grouped,
                    isLoadingOlder = false,
                    hasMoreMessages = older.size >= 40,  // if we got a full page, there may be more
                )
            }
        }
    }

    val sleepModeEnabled: StateFlow<Boolean> = settingsDataStore.sleepModeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var currentChatId: String = ""

    fun mightHaveData(chatId: String) = messageRepository.mightHaveData(chatId)

    fun init(chatId: String) {
        if (currentChatId == chatId) return

        // ── Cancel ALL previous chat's flows immediately ─────────────────────
        chatJob?.cancel()
        chatJob = null

        // Leave old socket room, join new one
        if (currentChatId.isNotEmpty()) socketManager.leaveChat(currentChatId)
        socketManager.joinChat(chatId)

        NotificationHelper.activeChatId = chatId
        currentChatId = chatId

        // ── Reset state immediately — CLEAR old messages + typing before new chat loads ─
        val hasData = messageRepository.mightHaveData(chatId)
        // Fix: explicitly clear typingUsers — peer's indicator from previous chat would
        // persist until the next socket event if not cleared here.
        _state.value = ChatUiState(isLoading = !hasData, typingUsers = emptyList())

        // ── Start socket observer once (or restart it) ───────────────────────
        socketJob?.cancel()
        socketJob = viewModelScope.launch {
            if (cachedMyId == null) cachedMyId = tokenDataStore.userId.first()
            observeSocket(cachedMyId)
        }

        // ── Start this chat's data flows ─────────────────────────────────────
        chatJob = viewModelScope.launch {
            if (cachedMyId == null) cachedMyId = tokenDataStore.userId.first()
            val myId = cachedMyId

            // 1. Windowed Room Flow — latest 60 messages, scoped to this chat
            // Performance: emits only 60 objects per update instead of full history
            messageRepository.observeLatestMessages(chatId, limit = 60)
                .onEach { messages ->
                    if (currentChatId != chatId) return@onEach
                    val (grouped, peer, peerHasReplied) = withContext(Dispatchers.Default) {
                        val g = groupByDate(messages)
                        val p = messages.firstOrNull { it.senderId != myId }?.sender
                        val r = messages.lastOrNull()?.senderId != myId
                        Triple(g, p, r)
                    }
                    _state.update { s ->
                        s.copy(
                            messages        = messages,
                            grouped         = grouped,
                            peerHasReplied  = peerHasReplied,
                            isLoading       = if (messages.isNotEmpty()) false else s.isLoading,
                            peer            = peer ?: s.peer,
                            // Show "load older" button if DB has more than 60 rows for this chat
                            hasMoreMessages = if (messages.size >= 60) true else s.hasMoreMessages,
                        )
                    }
                }
                .launchIn(this)  // scoped to chatJob — cancelled with it

            // 2. Network sync
            if (!messageRepository.hasMessages(chatId)) {
                fetchAndSave(chatId, myId, silent = false)
            } else {
                fetchAndSave(chatId, myId, silent = true)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        NotificationHelper.activeChatId = null
        socketManager.leaveChat(currentChatId)
    }

    private suspend fun fetchAndSave(chatId: String, myId: String?, silent: Boolean = false) {
        // Guard: don't write stale data if user switched chats while fetching
        if (currentChatId != chatId) return
        try {
            val res = apiService.getMessages(chatId)
            if (res.isSuccessful && currentChatId == chatId) {
                val msgs = res.body()?.messages ?: emptyList()
                messageRepository.saveAll(chatId, msgs)
                if (!silent) _state.update { it.copy(isLoading = false) }
            } else if (!silent) {
                _state.update { it.copy(isLoading = false) }
            }
        } catch (_: Exception) {
            if (!silent) _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun observeSocket(myId: String?) {
        socketManager.events.collect { event ->
            // All events are filtered to currentChatId — no cross-chat bleed
            when (event) {
                is SocketEvent.MessageReceived -> {
                    if (event.message.chatId == currentChatId) {
                        messageRepository.upsert(event.message)
                        _state.update { s ->
                            s.copy(
                                seenUpToId = if (event.message.senderId != myId) null else s.seenUpToId,
                            )
                        }
                    }
                }
                is SocketEvent.MessageEdited,
                is SocketEvent.MessageDeleted,
                is SocketEvent.MessagePinned,
                is SocketEvent.MessageUnpinned -> {
                    val msg = when (event) {
                        is SocketEvent.MessageEdited   -> event.message
                        is SocketEvent.MessageDeleted  -> event.message
                        is SocketEvent.MessagePinned   -> event.message
                        is SocketEvent.MessageUnpinned -> event.message
                        else                           -> return@collect
                    }
                    if (msg.chatId == currentChatId) messageRepository.upsert(msg)
                }
                is SocketEvent.TypingChanged -> {
                    if (event.chatId == currentChatId && event.userId != myId) {
                        _state.update { s ->
                            val names = s.typingUsers.toMutableList()
                            if (event.isTyping) names.add(event.username)
                            else names.remove(event.username)
                            s.copy(typingUsers = names.distinct())
                        }
                    }
                }
                is SocketEvent.MessageSeen -> {
                    if (event.chatId == currentChatId && event.userId != myId) {
                        _state.update { it.copy(seenUpToId = event.messageId) }
                    }
                }
                is SocketEvent.PresenceChanged -> {
                    val peerId = _state.value.peer?.id
                    if (event.userId == peerId) {
                        _state.update { it.copy(
                            peerOnline   = event.online,
                            peerSleeping = event.sleeping,
                        )}
                    }
                }
                is SocketEvent.PresenceSnapshot -> {
                    val peerId = _state.value.peer?.id
                    if (peerId != null) {
                        val isOnline   = event.onlineUserIds.contains(peerId)
                        val isSleeping = event.sleepingUserIds.contains(peerId)
                        _state.update { it.copy(
                            peerOnline   = isOnline,
                            peerSleeping = isSleeping,
                        )}
                    }
                }
                else -> {}
            }
        }
    }

    fun sendMessage(content: String, replyToId: String? = null) =
        socketManager.sendMessage(currentChatId, content, replyToId)

    fun editMessage(messageId: String, newContent: String) =
        socketManager.editMessage(messageId, currentChatId, newContent)

    fun deleteMessage(messageId: String) =
        socketManager.deleteMessage(messageId, currentChatId)

    fun pinMessage(messageId: String, pin: Boolean) =
        socketManager.pinMessage(messageId, currentChatId, pin)

    fun sendTyping(isTyping: Boolean) =
        socketManager.sendTyping(currentChatId, isTyping)

    fun markSeen(messageId: String) =
        socketManager.markSeen(currentChatId, messageId)
}
