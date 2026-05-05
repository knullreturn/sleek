package com.sleek.app.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

// @Immutable = all fields are val and never mutate after creation.
// This lets the Compose compiler SKIP recomposition when the object reference
// hasn't changed — the single biggest recomposition optimization available.

@Immutable
data class Message(
    val id:              String,
    val chatId:          String,
    val senderId:        String,
    val sender:          User,
    val content:         String,
    val edited:          Boolean  = false,
    val originalContent: String?  = null,
    val deletedAt:       String?  = null,
    val pinned:          Boolean  = false,
    val pinnedAt:        String?  = null,
    val pinnedById:      String?  = null,
    val pinnedBy:        User?    = null,
    val replyTo:         ReplyTo? = null,
    val createdAt:       String,
    val updatedAt:       String,
)

@Immutable
data class ReplyTo(
    val id:        String,
    val content:   String,
    val deletedAt: String?,
    val sender:    User,
)

@Stable
data class MessagesResponse(
    val messages:   List<Message>,
    val hasMore:    Boolean = false,
    val nextCursor: String? = null,
)
