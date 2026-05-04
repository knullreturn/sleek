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

    fun init(chatId: String) {
        if (currentChatId == chatId) return   // already initialised
        currentChatId = chatId
        socketManager.joinChat(chatId)
        observeSocket()

        // ── Synchronous cache check ────────────────────────────────────────
        // Runs on the main thread BEFORE the first frame renders → no skeleton
        val cached = messageRepository.get(chatId)
        if (cached != null) {
            _state.value = ChatUiState(messages = cached, isLoading = false)
            // Resolve peer + silent network refresh in background
            viewModelScope.launch {
                val myId = tokenDataStore.userId.first()
                val peer = cached.firstOrNull { it.senderId != myId }?.sender
                _state.update { it.copy(peer = peer) }
                fetchAndUpdate(chatId, myId, silent = true)
            }
        } else {
            // No cache → show skeleton, then fetch
            viewModelScope.launch {
                val myId = tokenDataStore.userId.first()
                fetchAndUpdate(chatId, myId, silent = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketManager.leaveChat(currentChatId)
    }

    private fun loadMessages(chatId: String) {
        viewModelScope.launch {
            val myId   = tokenDataStore.userId.first()
            val cached = messageRepository.get(chatId)

            if (cached != null) {
                // ── Cache hit: show immediately, no skeleton ──────────────────
                val peer = cached.firstOrNull { it.senderId != myId }?.sender
                _state.update { it.copy(messages = cached, isLoading = false, peer = peer) }
                // Silent background refresh — don't touch isLoading
                fetchAndUpdate(chatId, myId, silent = true)
            } else {
                // ── No cache: show skeleton then load ─────────────────────────
                _state.update { it.copy(isLoading = true) }
                fetchAndUpdate(chatId, myId, silent = false)
            }
        }
    }

    private suspend fun fetchAndUpdate(chatId: String, myId: String?, silent: Boolean) {
        try {
            val res = apiService.getMessages(chatId)
            if (res.isSuccessful) {
                val msgs = res.body()?.messages ?: emptyList()
                val peer = msgs.firstOrNull { it.senderId != myId }?.sender
                messageRepository.set(chatId, msgs)
                _state.update { it.copy(messages = msgs, isLoading = false, peer = peer) }
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
                            _state.update { s ->
                                val msgs = s.messages + event.message
                                messageRepository.set(currentChatId, msgs)
                                s.copy(
                                    messages   = msgs,
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
                            else -> return@collect
                        }
                        if (msg.chatId == currentChatId) {
                            messageRepository.upsert(currentChatId, msg)
                            updateMessage(msg)
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

    private fun updateMessage(updated: Message) {
        if (updated.chatId != currentChatId) return
        _state.update { s ->
            s.copy(messages = s.messages.map { if (it.id == updated.id) updated else it })
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
