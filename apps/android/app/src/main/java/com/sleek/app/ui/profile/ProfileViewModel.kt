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
    private val apiService:    ApiService,
    private val tokenDataStore: TokenDataStore,
    private val settingsStore:  SettingsDataStore,
    private val socketManager:  SocketManager,
) : ViewModel() {

    private val _me        = MutableStateFlow<User?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _loggedOut = MutableStateFlow(false)
    private val _email     = MutableStateFlow<String?>(null)

    val me        = _me.asStateFlow()
    val isLoading = _isLoading.asStateFlow()
    val loggedOut = _loggedOut.asStateFlow()
    val email     = _email.asStateFlow()

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
            // ── Load ALL cached data instantly — zero network needed for first render ──
            val cachedUsername  = tokenDataStore.username.first()
            val cachedUserId    = tokenDataStore.userId.first()
            val cachedEmail     = tokenDataStore.email.first()
            val cachedAvatarUrl = tokenDataStore.avatarUrl.first()  // ← from cache
            val cachedTag       = tokenDataStore.tag.first()        // ← from cache

            _email.value = cachedEmail

            if (cachedUserId != null) {
                // Show complete profile immediately — avatar included if cached
                _me.value = User(
                    id               = cachedUserId,
                    username         = cachedUsername ?: "",
                    tag              = cachedTag ?: "",
                    avatarUrl        = cachedAvatarUrl,  // ← real avatar, no flash
                    needsOnboarding  = false,
                )
                _isLoading.value = false
            }

            // ── Refresh from API silently in background ────────────────────────
            try {
                val res = apiService.getMe()
                if (res.isSuccessful) {
                    val user = res.body() ?: return@launch
                    _me.value        = user
                    _isLoading.value = false

                    // Persist fresh data so next open is instant
                    tokenDataStore.saveProfile(
                        avatarUrl = user.avatarUrl,
                        tag       = user.tag,
                        username  = user.username,
                        email     = cachedEmail,  // email doesn't come from User model
                    )
                }
            } catch (_: Exception) {
                _isLoading.value = false
            }
        }
    }

    fun setDarkTheme(dark: Boolean) {
        viewModelScope.launch { settingsStore.setDarkTheme(dark) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setNotificationsEnabled(enabled) }
    }

    fun setSleepMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setSleepModeEnabled(enabled)
            // Notify server immediately — it broadcasts to all peers so they
            // see the sleeping/online status change without any delay or polling.
            socketManager.setSleepMode(enabled)
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
