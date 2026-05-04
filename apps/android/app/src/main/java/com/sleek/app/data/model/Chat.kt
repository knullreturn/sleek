package com.sleek.app.data.model

data class Chat(
    val id:          String,
    val type:        String,
    val members:     List<User>,
    val lastMessage: Message?,
    val createdAt:   String,
    val unreadCount: Int = 0,
)
