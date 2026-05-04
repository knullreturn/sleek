package com.sleek.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.model.User
import com.sleek.app.data.remote.ApiService
import com.sleek.app.data.remote.SocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val apiService:     ApiService,
    private val tokenDataStore: TokenDataStore,
    private val socketManager:  SocketManager,
) : ViewModel() {

    private val _me        = MutableStateFlow<User?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _loggedOut = MutableStateFlow(false)

    val me        = _me.asStateFlow()
    val isLoading = _isLoading.asStateFlow()
    val loggedOut = _loggedOut.asStateFlow()

    init {
        viewModelScope.launch {
            // Immediately show cached username so screen never looks blank
            val cachedUsername = tokenDataStore.username.first()
            val cachedUserId   = tokenDataStore.userId.first()
            if (cachedUsername != null && cachedUserId != null) {
                _me.value = User(id = cachedUserId, username = cachedUsername,
                                 email = "", tag = "", avatarUrl = null, needsOnboarding = false)
                _isLoading.value = false   // show cached data instantly
            }

            // Then refresh from API in background
            try {
                val res = apiService.getMe()
                if (res.isSuccessful) {
                    _me.value      = res.body()
                    _isLoading.value = false
                }
            } catch (_: Exception) {
                _isLoading.value = false   // stop loading even on error
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            socketManager.disconnect()
            tokenDataStore.clear()
            _loggedOut.value = true
        }
    }
}
