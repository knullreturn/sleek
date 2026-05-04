package com.sleek.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.model.LoginRequest
import com.sleek.app.data.model.RegisterRequest
import com.sleek.app.data.remote.ApiService
import com.sleek.app.data.remote.SocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    object Idle    : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val apiService:     ApiService,
    private val tokenDataStore: TokenDataStore,
    private val socketManager:  SocketManager,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state = _state.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            try {
                val res = apiService.login(LoginRequest(email.trim(), password))
                if (res.isSuccessful) {
                    val body = res.body()!!
                    tokenDataStore.save(body.token, body.user.id, body.user.username)
                    socketManager.connect(body.token)
                    _state.value = AuthUiState.Success
                } else {
                    _state.value = AuthUiState.Error("Invalid credentials")
                }
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            try {
                val res = apiService.register(RegisterRequest(email.trim(), password))
                if (res.isSuccessful) {
                    val body = res.body()!!
                    tokenDataStore.save(body.token, body.user.id, body.user.username)
                    socketManager.connect(body.token)
                    _state.value = AuthUiState.Success
                } else {
                    _state.value = AuthUiState.Error("Registration failed")
                }
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun resetState() { _state.value = AuthUiState.Idle }
}
