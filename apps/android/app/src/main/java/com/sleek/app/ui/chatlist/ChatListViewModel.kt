package com.sleek.app.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.model.Chat
import com.sleek.app.data.model.User
import com.sleek.app.data.remote.ApiService
import com.sleek.app.data.remote.SocketEvent
import com.sleek.app.data.remote.SocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val apiService:     ApiService,
    private val tokenDataStore: TokenDataStore,
    private val socketManager:  SocketManager,
) : ViewModel() {

    private val _chats   = MutableStateFlow<List<Chat>>(emptyList())
    val chats = _chats.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    val userId: Flow<String?> = tokenDataStore.userId

    init {
        loadChats()
        observeSocket()
    }

    private fun loadChats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val res = apiService.getChats()
                if (res.isSuccessful) {
                    _chats.value = res.body()?.chats ?: emptyList()
                }
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    private fun observeSocket() {
        viewModelScope.launch {
            socketManager.events.collect { event ->
                when (event) {
                    is SocketEvent.MessageReceived -> updateLastMessage(event.message.chatId, event.message.content)
                    else -> {}
                }
            }
        }
    }

    private fun updateLastMessage(chatId: String, content: String) {
        _chats.update { list ->
            list.map { chat ->
                if (chat.id == chatId) chat.copy(lastMessage = chat.lastMessage?.copy(content = content))
                else chat
            }.sortedByDescending { it.lastMessage?.createdAt ?: it.createdAt }
        }
    }

    // Get the other person in a DM
    fun getDmPeer(chat: Chat, myId: String): User? =
        chat.members.firstOrNull { it.id != myId }

    fun refresh() = loadChats()
}
