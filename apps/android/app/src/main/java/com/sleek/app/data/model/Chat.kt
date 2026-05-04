package com.sleek.app.data.model

data class Chat(
    val id:          String,
    val type:        String,          // "DM" | "GROUP"
    val members:     List<User>,
    val lastMessage: Message?,
    val createdAt:   String,
)

data class ChatsResponse(val chats: List<Chat>)
