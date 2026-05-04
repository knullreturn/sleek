package com.sleek.app.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sleek.app.data.local.TokenDataStore
import com.sleek.app.data.remote.ApiService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles two things:
 *  1. onNewToken  — whenever FCM rotates the device token, we send the new one to our backend.
 *  2. onMessageReceived — FCM payload arriving when app is killed / in background.
 *     The system auto-shows a notification for "notification" payloads; we handle
 *     "data" payloads here to show a styled notification via NotificationHelper.
 */
@AndroidEntryPoint
class SleekFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var apiService:         ApiService
    @Inject lateinit var tokenDataStore:     TokenDataStore
    @Inject lateinit var notificationHelper: NotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called when FCM issues or rotates the device token. */
    override fun onNewToken(fcmToken: String) {
        super.onNewToken(fcmToken)
        Log.d("FCM", "New token: $fcmToken")
        scope.launch {
            try {
                val authToken = tokenDataStore.token.first() ?: return@launch
                // Send to backend — it will be stored against the user account
                apiService.saveFcmToken(mapOf("token" to fcmToken))
                Log.d("FCM", "Token saved to backend")
            } catch (e: Exception) {
                Log.e("FCM", "Failed to save token: ${e.message}")
            }
        }
    }

    /** Called for data-only FCM payloads when app is in foreground/background. */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        if (data.isEmpty()) return

        val senderName = data["senderName"] ?: "Someone"
        val content    = data["content"]    ?: "New message"
        val chatId     = data["chatId"]     ?: return
        val chatName   = data["chatName"]   ?: senderName

        notificationHelper.showMessageNotification(
            senderName = senderName,
            content    = content,
            chatId     = chatId,
            chatName   = chatName,
            notifId    = chatId.hashCode(),
        )
    }
}
