package com.sleek.app.data.repository

import com.sleek.app.data.local.db.MessageDao
import com.sleek.app.data.local.db.toEntity
import com.sleek.app.data.local.db.toMessage
import com.sleek.app.data.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for messages — backed by Room (SQLite).
 *
 * WhatsApp-style:
 *  - On app start : pre-populates knownChatIds from Room (< 2ms query)
 *  - Open chat    : mightHaveData() returns true → isLoading=false immediately
 *  - Room Flow    : emits from disk in < 5ms → instant message display
 *  - Network/socket → writes to Room → Flow auto-updates UI
 */
@Singleton
class MessageRepository @Inject constructor(
    private val dao: MessageDao,
) {
    // Synchronous set — tells ChatScreen/ChatViewModel if this chat has local data
    private val knownChatIds = mutableSetOf<String>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Eagerly load all chatIds that have messages in Room — runs once on app start
        // This makes mightHaveData() accurate even after process restart
        scope.launch {
            dao.getDistinctChatIds().forEach { knownChatIds.add(it) }
        }
    }

    // ── Synchronous (main-thread safe) ────────────────────────────────────────

    /** True if Room almost certainly has messages for this chat */
    fun mightHaveData(chatId: String): Boolean = knownChatIds.contains(chatId)

    // ── Room Flow ─────────────────────────────────────────────────────────────

    /** Live stream — emits a new list whenever ANY message in this chat changes */
    fun observeMessages(chatId: String): Flow<List<Message>> =
        dao.observeMessages(chatId).map { it.map { e -> e.toMessage() } }

    // ── Suspend helpers (IO thread) ───────────────────────────────────────────

    /** True if Room has at least one row for this chat */
    suspend fun hasMessages(chatId: String): Boolean =
        dao.countMessages(chatId) > 0

    /** Replace all messages for a chat (full network refresh) */
    suspend fun saveAll(chatId: String, messages: List<Message>) {
        dao.replaceAll(chatId, messages.map { it.toEntity() })
        knownChatIds.add(chatId)
    }

    /** Insert or update a single message (socket event / edit / delete / pin) */
    suspend fun upsert(message: Message) {
        dao.upsert(message.toEntity())
        knownChatIds.add(message.chatId)
    }
}
