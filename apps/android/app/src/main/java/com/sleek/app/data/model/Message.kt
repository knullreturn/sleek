package com.sleek.app.data.model

data class Message(
    val id:              String,
    val chatId:          String,
    val senderId:        String,
    val sender:          User,
    val content:         String,
    val edited:          Boolean        = false,
    val originalContent: String?        = null,
    val deletedAt:       String?        = null,
    val pinned:          Boolean        = false,
    val pinnedAt:        String?        = null,
    val pinnedById:      String?        = null,
    val pinnedBy:        User?          = null,
    val replyTo:         ReplyTo?       = null,
    val createdAt:       String,
    val updatedAt:       String,
)

data class ReplyTo(
    val id:        String,
    val content:   String,
    val deletedAt: String?,
    val sender:    User,
)

data class MessagesResponse(val messages: List<Message>)
