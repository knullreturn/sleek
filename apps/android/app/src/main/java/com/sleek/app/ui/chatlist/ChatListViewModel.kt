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

    private val _chats     = MutableStateFlow<List<Chat>>(emptyList())
    val chats = _chats.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error     = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    val userId: Flow<String?> = tokenDataStore.userId

    init {
        loadChats()
        observeSocket()
    }

    fun loadChats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                val res = apiService.getChats()
                if (res.isSuccessful) {
                    // Backend returns a plain array — body() is directly List<Chat>
                    _chats.value = res.body() ?: emptyList()
                } else {
                    _error.value = "Failed to load chats (${res.code()})"
                }
            } catch (e: Exception) {
                _error.value = "Network error: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    private fun observeSocket() {
        viewModelScope.launch {
            socketManager.events.collect { event ->
                when (event) {
                    is SocketEvent.MessageReceived -> {
                        _chats.update { list ->
                            list.map { chat ->
                                if (chat.id == event.message.chatId)
                                    chat.copy(lastMessage = event.message)
                                else chat
                            }.sortedByDescending { it.lastMessage?.createdAt ?: it.createdAt }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun getDmPeer(chat: Chat, myId: String): User? =
        chat.members.firstOrNull { it.id != myId }
}
