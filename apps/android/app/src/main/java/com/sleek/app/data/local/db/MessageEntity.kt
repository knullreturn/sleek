package com.sleek.app.data.local.db

import androidx.room.*
import com.sleek.app.data.model.*
import com.google.gson.Gson

// ── Entity ────────────────────────────────────────────────────────────────────
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id:             String,
    val chatId:                     String,
    val senderId:                   String,
    val senderJson:                 String,      // JSON(User)
    val content:                    String,
    val edited:                     Boolean = false,
    val originalContent:            String? = null,
    val deletedAt:                  String? = null,
    val pinned:                     Boolean = false,
    val pinnedAt:                   String? = null,
    val pinnedById:                 String? = null,
    val pinnedByJson:               String? = null,  // JSON(User)?
    val replyToJson:                String? = null,  // JSON(ReplyTo)?
    val createdAt:                  String,
    val updatedAt:                  String,
)

private val gson = Gson()

fun MessageEntity.toMessage(): Message = Message(
    id              = id,
    chatId          = chatId,
    senderId        = senderId,
    sender          = gson.fromJson(senderJson,  User::class.java),
    content         = content,
    edited          = edited,
    originalContent = originalContent,
    deletedAt       = deletedAt,
    pinned          = pinned,
    pinnedAt        = pinnedAt,
    pinnedById      = pinnedById,
    pinnedBy        = pinnedByJson?.let { gson.fromJson(it, User::class.java) },
    replyTo         = replyToJson?.let { gson.fromJson(it, ReplyTo::class.java) },
    createdAt       = createdAt,
    updatedAt       = updatedAt,
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id              = id,
    chatId          = chatId,
    senderId        = senderId,
    senderJson      = gson.toJson(sender),
    content         = content,
    edited          = edited,
    originalContent = originalContent,
    deletedAt       = deletedAt,
    pinned          = pinned,
    pinnedAt        = pinnedAt,
    pinnedById      = pinnedById,
    pinnedByJson    = pinnedBy?.let { gson.toJson(it) },
    replyToJson     = replyTo?.let { gson.toJson(it) },
    createdAt       = createdAt,
    updatedAt       = updatedAt,
)
