package com.sleek.app.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.sleek.app.BuildConfig
import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.model.GoogleAuthRequest
import com.sleek.app.data.model.OnboardRequest
import com.sleek.app.data.remote.ApiService
import com.sleek.app.data.remote.SocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    object Idle         : AuthUiState()
    object Loading      : AuthUiState()
    object NeedsOnboard : AuthUiState()   // new user — must pick username
    object Success      : AuthUiState()   // fully authed → go to ChatList
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

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            try {
                // Build the Google Sign-In request
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.GOOGLE_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)   // show all accounts
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val credentialManager = CredentialManager.create(context)
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential

                // Extract idToken
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleCredential.idToken

                // Send to backend
                val res = apiService.googleAuth(GoogleAuthRequest(idToken))
                if (res.isSuccessful) {
                    val body = res.body()!!
                    tokenDataStore.save(body.token, body.user.id, body.user.username)
                    socketManager.connect(body.token)
                    _state.value = if (body.user.needsOnboarding) AuthUiState.NeedsOnboard
                                   else AuthUiState.Success
                } else {
                    val errBody = res.errorBody()?.string() ?: "no body"
                    android.util.Log.e("AUTH", "Sign-in failed ${res.code()}: $errBody")
                    _state.value = AuthUiState.Error("Sign-in failed. Try again.")
                }
            } catch (e: GetCredentialException) {
                _state.value = AuthUiState.Error("Google Sign-In cancelled")
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    fun submitUsername(username: String) {
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            try {
                val res = apiService.onboard(OnboardRequest(username.trim()))
                if (res.isSuccessful) {
                    val body = res.body()!!
                    tokenDataStore.save(body.token, body.user.id, body.user.username)
                    _state.value = AuthUiState.Success
                } else {
                    val msg = res.errorBody()?.string()?.let {
                        Regex("\"message\":\"([^\"]+)\"").find(it)?.groupValues?.get(1)
                    } ?: "Username not available"
                    _state.value = AuthUiState.Error(msg)
                }
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun resetState() { _state.value = AuthUiState.Idle }
}
