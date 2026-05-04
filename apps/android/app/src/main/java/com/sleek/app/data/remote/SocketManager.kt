package com.sleek.app.data.remote

import android.util.Log
import com.sleek.app.BuildConfig
import com.sleek.app.data.model.Message
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

sealed class SocketEvent {
    data class MessageReceived(val message: Message)  : SocketEvent()
    data class MessageEdited(val message: Message)    : SocketEvent()
    data class MessageDeleted(val message: Message)   : SocketEvent()
    data class MessagePinned(val message: Message)    : SocketEvent()
    data class MessageUnpinned(val message: Message)  : SocketEvent()
    data class TypingChanged(val chatId: String, val userId: String, val username: String, val isTyping: Boolean) : SocketEvent()
    data class PresenceChanged(val userId: String, val online: Boolean) : SocketEvent()
    data class MessageSeen(val messageId: String, val chatId: String, val userId: String) : SocketEvent()
}

@Singleton
class SocketManager @Inject constructor() {

    private var socket: Socket? = null
    private val gson = Gson()

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun connect(token: String) {
        if (socket?.connected() == true) return
        try {
            val opts = IO.Options.builder()
                .setAuth(mapOf("token" to token))
                .setTransports(arrayOf(WebSocket.NAME))
                .setReconnection(true)
                .setReconnectionAttempts(5)
                .build()

            socket = IO.socket(BuildConfig.SOCKET_URL, opts).also { s ->
                s.on(Socket.EVENT_CONNECT)       { Log.d("Socket", "Connected") }
                s.on(Socket.EVENT_DISCONNECT)    { Log.d("Socket", "Disconnected") }
                s.on(Socket.EVENT_CONNECT_ERROR) { args -> Log.e("Socket", "Error: ${args[0]}") }

                s.on("receive_message")  { args -> parseMessage(args)?.let { _events.tryEmit(SocketEvent.MessageReceived(it)) } }
                s.on("message_edited")   { args -> parseMessage(args)?.let { _events.tryEmit(SocketEvent.MessageEdited(it)) } }
                s.on("message_deleted")  { args -> parseMessage(args)?.let { _events.tryEmit(SocketEvent.MessageDeleted(it)) } }
                s.on("message_pinned")   { args -> parseMessage(args)?.let { _events.tryEmit(SocketEvent.MessagePinned(it)) } }
                s.on("message_unpinned") { args -> parseMessage(args)?.let { _events.tryEmit(SocketEvent.MessageUnpinned(it)) } }

                s.on("typing") { args ->
                    val obj = args[0] as? JSONObject ?: return@on
                    _events.tryEmit(SocketEvent.TypingChanged(
                        chatId    = obj.optString("chatId"),
                        userId    = obj.optString("userId"),
                        username  = obj.optString("username"),
                        isTyping  = obj.optBoolean("isTyping"),
                    ))
                }

                s.on("presence") { args ->
                    val obj = args[0] as? JSONObject ?: return@on
                    _events.tryEmit(SocketEvent.PresenceChanged(
                        userId = obj.optString("userId"),
                        online = obj.optString("status") == "online",
                    ))
                }

                s.on("message_seen") { args ->
                    val obj = args[0] as? JSONObject ?: return@on
                    _events.tryEmit(SocketEvent.MessageSeen(
                        messageId = obj.optString("messageId"),
                        chatId    = obj.optString("chatId"),
                        userId    = obj.optString("userId"),
                    ))
                }
                s.connect()
            }
        } catch (e: Exception) {
            Log.e("Socket", "Init error: ${e.message}")
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }

    fun joinChat(chatId: String)  = socket?.emit("join_chat", chatId)
    fun leaveChat(chatId: String) = socket?.emit("leave_chat", chatId)

    fun sendMessage(chatId: String, content: String, replyToId: String? = null) {
        val obj = JSONObject().apply {
            put("chatId", chatId)
            put("content", content)
            replyToId?.let { put("replyToId", it) }
        }
        socket?.emit("send_message", obj)
    }

    fun editMessage(messageId: String, chatId: String, newContent: String) {
        socket?.emit("edit_message", JSONObject().apply {
            put("messageId", messageId)
            put("chatId", chatId)
            put("newContent", newContent)
        })
    }

    fun deleteMessage(messageId: String, chatId: String) {
        socket?.emit("delete_message", JSONObject().apply {
            put("messageId", messageId)
            put("chatId", chatId)
        })
    }

    fun pinMessage(messageId: String, chatId: String, pin: Boolean) {
        socket?.emit(if (pin) "pin_message" else "unpin_message", JSONObject().apply {
            put("messageId", messageId)
            put("chatId", chatId)
        })
    }

    fun sendTyping(chatId: String, isTyping: Boolean) {
        socket?.emit("typing", JSONObject().apply {
            put("chatId", chatId)
            put("isTyping", isTyping)
        })
    }

    fun markSeen(chatId: String, messageId: String) {
        socket?.emit("mark_seen", JSONObject().apply {
            put("chatId", chatId)
            put("messageId", messageId)
        })
    }

    private fun parseMessage(args: Array<Any>): Message? = try {
        val obj = args[0] as JSONObject
        gson.fromJson(obj.getJSONObject("message").toString(), Message::class.java)
    } catch (e: Exception) {
        Log.e("Socket", "Parse error: ${e.message}")
        null
    }
}
