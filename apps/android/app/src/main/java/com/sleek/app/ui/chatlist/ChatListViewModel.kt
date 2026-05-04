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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val apiService:     ApiService,
    private val tokenDataStore: TokenDataStore,
    private val socketManager:  SocketManager,
) : ViewModel() {

    private val _chats     = MutableStateFlow<List<Chat>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _error     = MutableStateFlow<String?>(null)
    private val _me        = MutableStateFlow<User?>(null)

    // Search state for filtering existing chats
    private val _searchQuery   = MutableStateFlow("")
    // New DM bottom sheet state
    private val _dmQuery       = MutableStateFlow("")
    private val _dmResults     = MutableStateFlow<List<User>>(emptyList())
    private val _dmSearching   = MutableStateFlow(false)
    private val _showNewDm     = MutableStateFlow(false)

    val isLoading    = _isLoading.asStateFlow()
    val error        = _error.asStateFlow()
    val me           = _me.asStateFlow()
    val searchQuery  = _searchQuery.asStateFlow()
    val dmQuery      = _dmQuery.asStateFlow()
    val dmResults    = _dmResults.asStateFlow()
    val dmSearching  = _dmSearching.asStateFlow()
    val showNewDm    = _showNewDm.asStateFlow()
    val userId: Flow<String?> = tokenDataStore.userId

    // Filtered chats based on search query
    val chats: StateFlow<List<Chat>> = combine(_chats, _searchQuery) { chats, query ->
        if (query.isBlank()) chats
        else chats.filter { chat ->
            chat.members.any { m ->
                m.username?.contains(query, ignoreCase = true) == true
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadChats()
        loadMe()
        observeSocket()
        // Debounce DM search
        viewModelScope.launch {
            _dmQuery.debounce(300).collect { q ->
                if (q.length >= 2) searchUsers(q)
                else _dmResults.value = emptyList()
            }
        }
    }

    private fun loadMe() {
        viewModelScope.launch {
            try {
                val res = apiService.getMe()
                if (res.isSuccessful) _me.value = res.body()
            } catch (_: Exception) {}
        }
    }

    fun loadChats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                val res = apiService.getChats()
                if (res.isSuccessful) {
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
                        val chatExists = _chats.value.any { it.id == event.message.chatId }
                        if (chatExists) {
                            // Known chat — update lastMessage and re-sort
                            _chats.update { list ->
                                list.map { chat ->
                                    if (chat.id == event.message.chatId)
                                        chat.copy(lastMessage = event.message)
                                    else chat
                                }.sortedByDescending { it.lastMessage?.createdAt ?: it.createdAt }
                            }
                        } else {
                            // Brand new chat from someone new — silent refresh
                            loadChats()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun searchUsers(query: String) {
        viewModelScope.launch {
            _dmSearching.value = true
            try {
                val res = apiService.searchUsers(query)
                if (res.isSuccessful) _dmResults.value = res.body() ?: emptyList()
            } catch (_: Exception) {}
            _dmSearching.value = false
        }
    }

    fun startDm(targetUserId: String, onSuccess: (chatId: String, chatName: String) -> Unit) {
        viewModelScope.launch {
            try {
                val res = apiService.createDm(mapOf("targetUserId" to targetUserId))
                if (res.isSuccessful) {
                    val chat = res.body()!!
                    // Add to list if not present
                    if (_chats.value.none { it.id == chat.id }) {
                        _chats.update { listOf(chat) + it }
                    }
                    val me = _me.value
                    val peer = chat.members.firstOrNull { it.id != me?.id }
                    dismissNewDm()
                    onSuccess(chat.id, peer?.username ?: "Chat")
                }
            } catch (_: Exception) {}
        }
    }

    fun setSearchQuery(q: String)  { _searchQuery.value = q }
    fun setDmQuery(q: String)      { _dmQuery.value = q }
    fun showNewDm()                { _showNewDm.value = true; _dmQuery.value = ""; _dmResults.value = emptyList() }
    fun dismissNewDm()             { _showNewDm.value = false }

    fun getDmPeer(chat: Chat, myId: String): User? =
        chat.members.firstOrNull { it.id != myId }
}
