package com.sleek.app.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** Live query — emits a new list whenever any row in this chat changes */
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun observeMessages(chatId: String): Flow<List<MessageEntity>>

    /** One-shot query for checking if data exists */
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun countMessages(chatId: String): Int

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
