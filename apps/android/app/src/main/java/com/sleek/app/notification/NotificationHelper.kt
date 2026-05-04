package com.sleek.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sleek.app.MainActivity
import com.sleek.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID   = "sleek_messages"
        const val CHANNEL_NAME = "Messages"

        // Set by ChatViewModel when a chat is opened / cleared when left
        @Volatile var activeChatId: String? = null
        // Set by ChatListViewModel when user is known
        @Volatile var myUserId: String? = null
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,   // HIGH = heads-up popup
        ).apply {
            description    = "New message notifications"
            enableVibration(true)
            enableLights(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun showMessageNotification(
        senderName: String,
        content:    String,
        chatId:     String,
        chatName:   String,
        notifId:    Int,
    ) {
        // Don't notify if the user is currently in that chat
        if (chatId == activeChatId) return

        // Intent: tapping the notification opens MainActivity and lands in that chat
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_CHAT_ID,   chatId)
            putExtra(MainActivity.EXTRA_CHAT_NAME, chatName)
        }
        val pi = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted yet — silently skip
        }
    }
}
