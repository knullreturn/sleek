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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val apiService:     ApiService,
    private val tokenDataStore: TokenDataStore,
    private val socketManager:  SocketManager,
) : ViewModel() {

    private val _me        = MutableStateFlow<User?>(null)
    private val _loggedOut = MutableStateFlow(false)

    val me        = _me.asStateFlow()
    val loggedOut = _loggedOut.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val res = apiService.getMe()
                if (res.isSuccessful) _me.value = res.body()
            } catch (_: Exception) {}
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
