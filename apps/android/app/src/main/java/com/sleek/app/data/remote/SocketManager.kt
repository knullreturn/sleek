package com.sleek.app.data.remote

import android.util.Log
import com.sleek.app.BuildConfig
import com.sleek.app.data.model.Message
import com.google.gson.Gson
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
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
    data class PresenceChanged(val userId: String, val online: Boolean, val sleeping: Boolean = false) : SocketEvent()
    data class PresenceSnapshot(val onlineUserIds: List<String>, val sleepingUserIds: List<String> = emptyList()) : SocketEvent()
    data class MessageSeen(val messageId: String, val chatId: String, val userId: String) : SocketEvent()
    data class NewChat(val chatJson: String) : SocketEvent()
    /** Fired when send_message ACK returns an error from the server */
    data class SendFailed(val chatId: String, val tempContent: String) : SocketEvent()
}

@Singleton
class SocketManager @Inject constructor() {

    private var socket: Socket? = null
    private val gson = Gson()

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    // Fix: expose connection state so callers can gate sends and show UI
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    fun connect(token: String) {
        if (socket?.connected() == true) return
        socket?.disconnect()   // clean up any dead socket before creating a new one

        try {
            val opts = IO.Options.builder()
                .setAuth(mapOf("token" to token))
                .setTransports(arrayOf(WebSocket.NAME))
                .setReconnection(true)
                // Fix: was 5 — after 5 failures the socket died permanently.
                // Int.MAX_VALUE = effectively infinite retries.
                .setReconnectionAttempts(Int.MAX_VALUE)
                .setReconnectionDelay(1_000)       // 1s initial delay
                .setReconnectionDelayMax(30_000)   // cap at 30s (exponential back-off)
                .build()

            socket = IO.socket(BuildConfig.SOCKET_URL, opts).also { s ->
                s.on(Socket.EVENT_CONNECT) {
                    Log.d("Socket", "Connected")
                    _isConnected.value = true
                }
                s.on(Socket.EVENT_DISCONNECT) { args ->
                    Log.d("Socket", "Disconnected: ${args.firstOrNull()}")
                    _isConnected.value = false
                }
                s.on(Socket.EVENT_CONNECT_ERROR) { args ->
                    Log.e("Socket", "Error: ${args.firstOrNull()}")
                    _isConnected.value = false
                }

                s.on("receive_message") { args -> parseMessage(args)?.let { _events.tryEmit(SocketEvent.MessageReceived(it)) } }
                s.on("message_edited")  { args -> parseMessage(args)?.let { _events.tryEmit(SocketEvent.MessageEdited(it)) } }
                s.on("message_deleted") { args -> parseMessage(args)?.let { _events.tryEmit(SocketEvent.MessageDeleted(it)) } }
                s.on("message_pinned")  { args -> parseMessage(args)?.let { _events.tryEmit(SocketEvent.MessagePinned(it)) } }
                s.on("message_unpinned"){ args -> parseMessage(args)?.let { _events.tryEmit(SocketEvent.MessageUnpinned(it)) } }

                s.on("new_chat") { args ->
                    val obj = args[0] as? JSONObject ?: return@on
                    val chatObj = obj.optJSONObject("chat") ?: return@on
                    _events.tryEmit(SocketEvent.NewChat(chatObj.toString()))
                }

                s.on("typing") { args ->
                    val obj = args[0] as? JSONObject ?: return@on
                    _events.tryEmit(SocketEvent.TypingChanged(
                        chatId   = obj.optString("chatId"),
                        userId   = obj.optString("userId"),
                        username = obj.optString("username"),
                        isTyping = obj.optBoolean("isTyping"),
                    ))
                }

                s.on("presence") { args ->
                    val obj = args[0] as? JSONObject ?: return@on
                    val status = obj.optString("status")
                    _events.tryEmit(SocketEvent.PresenceChanged(
                        userId   = obj.optString("userId"),
                        online   = status == "online" || status == "sleeping",
                        sleeping = status == "sleeping",
                    ))
                }

                // Fix: consume presence_snapshot with sleep state
                s.on("presence_snapshot") { args ->
                    val obj = args[0] as? JSONObject ?: return@on
                    val onlineArr   = obj.optJSONArray("onlineUserIds")   ?: return@on
                    val sleepArr    = obj.optJSONArray("sleepingUserIds")
                    val onlineIds   = (0 until onlineArr.length()).map { onlineArr.getString(it) }
                    val sleepingIds = sleepArr?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
                    _events.tryEmit(SocketEvent.PresenceSnapshot(onlineIds, sleepingIds))
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

    /** Called on app resume to force reconnect if socket is dead */
    fun reconnectIfNeeded(token: String) {
        if (socket?.connected() == true) return
        Log.d("Socket", "Reconnecting on resume…")
        connect(token)
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        _isConnected.value = false
    }

    fun joinChat(chatId: String)  = socket?.emit("join_chat", chatId)
    fun leaveChat(chatId: String) = socket?.emit("leave_chat", chatId)

    /**
     * Fix: send_message now uses a socket acknowledgement.
     * The server ACKs with { ok: true } on success or { error: "..." } on failure.
     * If the socket is disconnected, emits SendFailed immediately.
     */
    fun sendMessage(
        chatId: String,
        content: String,
        replyToId: String? = null,
        onFail: (() -> Unit)? = null,
    ) {
        val s = socket
        if (s == null || !s.connected()) {
            // Socket is dead — caller should show an error / re-queue
            onFail?.invoke()
            _events.tryEmit(SocketEvent.SendFailed(chatId, content))
            return
        }
        val obj = JSONObject().apply {
            put("chatId", chatId)
            put("content", content)
            replyToId?.let { put("replyToId", it) }
        }
        s.emit("send_message", obj, Ack { ackArgs ->
            val ack = ackArgs.firstOrNull() as? JSONObject
            if (ack?.optBoolean("ok") != true) {
                Log.e("Socket", "send_message ack error: ${ack?.optString("error")}")
                onFail?.invoke()
                _events.tryEmit(SocketEvent.SendFailed(chatId, content))
            }
        })
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
        if (socket?.connected() != true) return  // don't queue typing events
        socket?.emit("typing", JSONObject().apply {
            put("chatId", chatId)
            put("isTyping", isTyping)
        })
    }

    /** Notify server of sleep mode state change — broadcasts to all peers instantly */
    fun setSleepMode(enabled: Boolean) {
        socket?.emit("set_sleep_mode", JSONObject().apply {
            put("enabled", enabled)
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
