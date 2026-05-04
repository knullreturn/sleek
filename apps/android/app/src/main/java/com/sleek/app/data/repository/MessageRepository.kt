package com.sleek.app.data.repository

import com.sleek.app.data.model.Message
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory message cache — survives navigation (Singleton scope).
 * On first open: no cache → show skeleton → cache result.
 * On re-open:    show cache instantly → silent background refresh.
 */
@Singleton
class MessageRepository @Inject constructor() {

    // chatId → ordered message list
    private val cache = mutableMapOf<String, List<Message>>()

    fun get(chatId: String): List<Message>? = cache[chatId]

    fun set(chatId: String, messages: List<Message>) {
        cache[chatId] = messages
    }

    /** Merge a single new/updated message into the cache */
    fun upsert(chatId: String, message: Message) {
        val current = cache[chatId] ?: return
        val updated = current.map { if (it.id == message.id) message else it }
        cache[chatId] = if (updated.none { it.id == message.id }) current + message else updated
    }

    fun clear(chatId: String) = cache.remove(chatId)
}
