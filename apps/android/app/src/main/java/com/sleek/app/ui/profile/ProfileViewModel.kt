package com.sleek.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleek.app.data.local.SettingsDataStore
import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.model.User
import com.sleek.app.data.remote.ApiService
import com.sleek.app.data.remote.SocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val apiService:      ApiService,
    private val tokenDataStore:  TokenDataStore,
    private val settingsStore:   SettingsDataStore,
    private val socketManager:   SocketManager,
) : ViewModel() {

    private val _me        = MutableStateFlow<User?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _loggedOut = MutableStateFlow(false)
    private val _email     = MutableStateFlow<String?>(null)

    val me        = _me.asStateFlow()
    val isLoading = _isLoading.asStateFlow()
    val loggedOut = _loggedOut.asStateFlow()
    val email     = _email.asStateFlow()

    // Settings — backed by DataStore
    val isDarkTheme = settingsStore.isDarkTheme.stateIn(
        viewModelScope, SharingStarted.Eagerly, true,
    )
    val notificationsEnabled = settingsStore.notificationsEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, true,
    )
    val sleepModeEnabled = settingsStore.sleepModeEnabled.stateIn(
        viewModelScope, SharingStarted.Eagerly, false,
    )

    init {
        viewModelScope.launch {
            // Show cached data instantly
            val cachedUsername = tokenDataStore.username.first()
            val cachedUserId   = tokenDataStore.userId.first()
            val cachedEmail    = tokenDataStore.email.first()
            _email.value = cachedEmail
            if (cachedUsername != null && cachedUserId != null) {
                _me.value = User(
                    id = cachedUserId, username = cachedUsername,
                    tag = "", avatarUrl = null, needsOnboarding = false,
                )
                _isLoading.value = false
            }
            // Refresh from API
            try {
                val res = apiService.getMe()
                if (res.isSuccessful) {
                    _me.value      = res.body()
                    _isLoading.value = false
                }
            } catch (_: Exception) { _isLoading.value = false }
        }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { settingsStore.setDarkTheme(dark) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setNotificationsEnabled(enabled) }
    }

    fun setSleepMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setSleepModeEnabled(enabled) }
    }

    fun logout() {
        viewModelScope.launch {
            socketManager.disconnect()
            tokenDataStore.clear()
            _loggedOut.value = true
        }
    }
}
