package com.sleek.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.model.Message
import com.sleek.app.data.remote.ApiService
import com.sleek.app.data.remote.SocketEvent
import com.sleek.app.data.remote.SocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages:    List<Message> = emptyList(),
    val isLoading:   Boolean       = true,
    val typingUsers: List<String>  = emptyList(),  // usernames typing
    val seenUpToId:  String?       = null,          // last message seen by peer
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val apiService:     ApiService,
    private val socketManager:  SocketManager,
    private val tokenDataStore: TokenDataStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    val myUserId: Flow<String?> = tokenDataStore.userId

    private var currentChatId: String = ""

    fun init(chatId: String) {
        currentChatId = chatId
        loadMessages(chatId)
        observeSocket()
        socketManager.joinChat(chatId)
    }

    override fun onCleared() {
        super.onCleared()
        socketManager.leaveChat(currentChatId)
    }

    private fun loadMessages(chatId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val res = apiService.getMessages(chatId)
                if (res.isSuccessful) {
                    _state.update { it.copy(messages = res.body()?.messages ?: emptyList(), isLoading = false) }
                }
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
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
                                s.copy(
                                    messages   = s.messages + event.message,
                                    // Peer replied → clear seen green
                                    seenUpToId = if (event.message.senderId != myId) null else s.seenUpToId,
                                )
                            }
                        }
                    }
                    is SocketEvent.MessageEdited -> updateMessage(event.message)
                    is SocketEvent.MessageDeleted -> updateMessage(event.message)
                    is SocketEvent.MessagePinned -> updateMessage(event.message)
                    is SocketEvent.MessageUnpinned -> updateMessage(event.message)
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

    fun sendMessage(content: String, replyToId: String? = null) {
        socketManager.sendMessage(currentChatId, content, replyToId)
    }

    fun editMessage(messageId: String, newContent: String) {
        socketManager.editMessage(messageId, currentChatId, newContent)
    }

    fun deleteMessage(messageId: String) {
        socketManager.deleteMessage(messageId, currentChatId)
    }

    fun pinMessage(messageId: String, pin: Boolean) {
        socketManager.pinMessage(messageId, currentChatId, pin)
    }

    fun sendTyping(isTyping: Boolean) {
        socketManager.sendTyping(currentChatId, isTyping)
    }

    fun markSeen(messageId: String) {
        socketManager.markSeen(currentChatId, messageId)
    }
}
