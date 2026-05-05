package com.sleek.app.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** Live query — emits a new list whenever any row in this chat changes.
     *  Used only for full export / search — NOT for the chat screen. */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    /**
     * Windowed live query — only the latest [limit] messages in DESC order (newest first).
     *
     * With reverseLayout=true in the LazyColumn, item(0) renders at the bottom,
     * so DESC data = newest at bottom visually. Benefit over the previous outer-ASC
     * approach: single sort pass, and new messages prepend at index 0 (O(1)),
     * rather than appending to the end which forced LazyColumn to re-anchor.
     */
    @Query("""
        SELECT * FROM messages
        WHERE chatId = :chatId
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    fun observeLatestMessages(chatId: String, limit: Int = 60): Flow<List<MessageEntity>>

    /**
     * Load the page BEFORE a given timestamp (scroll-up pagination).
     * Returns the previous [limit] messages older than [beforeCreatedAt].
     */
    @Query("""
        SELECT * FROM messages
        WHERE chatId = :chatId AND createdAt < :beforeCreatedAt
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    suspend fun getMessagesBefore(chatId: String, beforeCreatedAt: String, limit: Int = 40): List<MessageEntity>

    /** One-shot query for checking if data exists */
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun countMessages(chatId: String): Int

    /** All chat IDs that have at least one message — used to pre-populate knownChatIds on startup */
    @Query("SELECT DISTINCT chatId FROM messages")
    suspend fun getDistinctChatIds(): List<String>

    /** Replace all messages for a chat (after full network fetch) */
    @Transaction
    suspend fun replaceAll(chatId: String, messages: List<MessageEntity>) {
        deleteByChat(chatId)
        insertAll(messages)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    /** Upsert a single message (socket event / edit / delete / pin) */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteByChat(chatId: String)
}
