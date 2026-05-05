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
     * Windowed live query — only the latest [limit] messages.
     * Inner SELECT is sorted DESC (newest first) then the outer re-sorts ASC so
     * the list displays oldest→newest as expected.
     *
     * Benefit: Room only materialises [limit] objects per emission instead of
     * the full history. Cold-open of a chat with 500 messages drops from
     * ~500 object allocations to ~60.
     */
    @Query("""
        SELECT * FROM (
            SELECT * FROM messages
            WHERE chatId = :chatId
            ORDER BY createdAt DESC
            LIMIT :limit
        ) ORDER BY createdAt ASC
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
