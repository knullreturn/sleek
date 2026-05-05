package com.sleek.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Chat(
    val id:          String,
    val type:        String,
    val members:     List<User>,
    val lastMessage: Message?,
    val createdAt:   String,
    val unreadCount: Int = 0,
)
