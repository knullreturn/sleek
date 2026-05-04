package com.sleek.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.model.Message
import com.sleek.app.data.model.User
import com.sleek.app.data.remote.ApiService
import com.sleek.app.data.remote.SocketEvent
import com.sleek.app.data.remote.SocketManager
import com.sleek.app.data.repository.MessageRepository
import com.sleek.app.notification.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages:    List<Message> = emptyList(),
    val isLoading:   Boolean       = true,
    val typingUsers: List<String>  = emptyList(),
    val seenUpToId:  String?       = null,
    val peer:        User?         = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val apiService:        ApiService,
    private val socketManager:     SocketManager,
    private val tokenDataStore:    TokenDataStore,
    private val messageRepository: MessageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    val myUserId: Flow<String?> = tokenDataStore.userId

    private var currentChatId: String = ""

    // ── Public: expose whether Room likely has data (synchronous) ─────────────
    fun mightHaveData(chatId: String) = messageRepository.mightHaveData(chatId)

    fun init(chatId: String) {
        if (currentChatId == chatId) return
        // Mark this chat as active → suppresses its notifications
        NotificationHelper.activeChatId = chatId
        currentChatId = chatId
        socketManager.joinChat(chatId)
        observeSocket()

        // ── If Room likely has data, skip skeleton immediately ────────────────
        val likelyHasData = messageRepository.mightHaveData(chatId)
        if (likelyHasData) {
            _state.update { it.copy(isLoading = false) }
        }

        // ── 1. Subscribe to Room Flow — UI updates automatically ──────────────
        messageRepository.observeMessages(chatId)
            .onEach { messages ->
                val myId = tokenDataStore.userId.first()
                val peer = messages.firstOrNull { it.senderId != myId }?.sender
                _state.update { s ->
                    s.copy(
                        messages  = messages,
                        // Turn off loading as soon as we get any data from Room
                        isLoading = if (messages.isNotEmpty()) false else s.isLoading,
                        peer      = peer ?: s.peer,
                    )
                }
            }
            .launchIn(viewModelScope)

        // ── 2. Network sync ───────────────────────────────────────────────────
        viewModelScope.launch {
            val myId = tokenDataStore.userId.first()
            if (!messageRepository.hasMessages(chatId)) {
                // Absolute first open — skeleton until fetch completes
                _state.update { it.copy(isLoading = true) }
                fetchAndSave(chatId, myId, silent = false)
            } else {
                // Has local data — sync silently, no UI disruption
                fetchAndSave(chatId, myId, silent = true)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        NotificationHelper.activeChatId = null  // resume notifications when chat is left
        socketManager.leaveChat(currentChatId)
    }

    private suspend fun fetchAndSave(chatId: String, myId: String?, silent: Boolean = false) {
        try {
            val res = apiService.getMessages(chatId)
            if (res.isSuccessful) {
                val msgs = res.body()?.messages ?: emptyList()
                // Write to Room → the Flow above auto-updates the UI
                messageRepository.saveAll(chatId, msgs)
                // isLoading → false (Room Flow will set messages, but we ensure loading stops)
                if (!silent) _state.update { it.copy(isLoading = false) }
            } else if (!silent) {
                _state.update { it.copy(isLoading = false) }
            }
        } catch (_: Exception) {
            if (!silent) _state.update { it.copy(isLoading = false) }
        }
    }

    private fun observeSocket() {
        viewModelScope.launch {
            val myId = tokenDataStore.userId.first()
            socketManager.events.collect { event ->
                when (event) {
                    is SocketEvent.MessageReceived -> {
                        if (event.message.chatId == currentChatId) {
                            // Write to Room → Flow auto-emits new list
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
                        if (msg.chatId == currentChatId) {
                            // Upsert → Room → Flow auto-updates UI
                            messageRepository.upsert(msg)
                        }
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
                    else -> {}
                }
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
