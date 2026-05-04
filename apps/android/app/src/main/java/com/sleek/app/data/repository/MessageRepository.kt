package com.sleek.app.data.repository

import com.sleek.app.data.local.db.MessageDao
import com.sleek.app.data.local.db.toEntity
import com.sleek.app.data.local.db.toMessage
import com.sleek.app.data.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for messages — backed by Room (SQLite).
 *
 * WhatsApp-style strategy:
 *  - Open chat  → Room Flow emits from disk instantly (< 5 ms)
 *  - Network    → writes to Room → Flow auto-updates UI
 *  - Socket     → upserts into Room → Flow auto-updates UI
 *  - No manual state; UI always reads from Room
 *
 * knownChatIds: lightweight in-memory set so ChatScreen can decide
 *               synchronously whether to skip the "first open" fade-in.
 */
@Singleton
class MessageRepository @Inject constructor(
    private val dao: MessageDao,
) {
    // Populated whenever we save messages — survives config changes
    private val knownChatIds = mutableSetOf<String>()

    // ── Synchronous helpers (main-thread safe) ────────────────────────────────

    /** True if this chat had messages the last time we saved data for it */
    fun mightHaveData(chatId: String): Boolean = knownChatIds.contains(chatId)

    // ── Room Flow ─────────────────────────────────────────────────────────────

    /** Live stream — emits a new list whenever ANY message in this chat changes */
    fun observeMessages(chatId: String): Flow<List<Message>> =
        dao.observeMessages(chatId).map { entities -> entities.map { it.toMessage() } }

    // ── Suspend helpers (background thread) ───────────────────────────────────

    /** True if Room has at least one row for this chat */
    suspend fun hasMessages(chatId: String): Boolean =
        dao.countMessages(chatId) > 0

    /** Replace all messages for a chat (full network refresh) */
    suspend fun saveAll(chatId: String, messages: List<Message>) {
        dao.replaceAll(chatId, messages.map { it.toEntity() })
        knownChatIds.add(chatId)
    }

    /** Insert or update a single message (socket new/edit/delete/pin) */
    suspend fun upsert(message: Message) {
        dao.upsert(message.toEntity())
        knownChatIds.add(message.chatId)
    }
}
