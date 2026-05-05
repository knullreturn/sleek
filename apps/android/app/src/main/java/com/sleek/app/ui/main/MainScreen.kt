package com.sleek.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sleek.app.ui.chat.ChatScreen
import com.sleek.app.ui.chatlist.ChatListScreen
import com.sleek.app.ui.profile.ProfileScreen

/**
 * Persistent layered navigation — the WhatsApp model.
 *
 * ChatListScreen is ALWAYS in the composition tree.
 * Chat and Profile screens slide in on top — ChatList is never
 * destroyed, so going back is instant with zero recomposition cost.
 *
 * Layout:
 *   [ChatListScreen]          ← always alive
 *   [ChatScreen]  (overlay)   ← slides in/out, kept during exit anim
 *   [ProfileScreen] (overlay) ← same
 */
@Composable
fun MainScreen(
    onLoggedOut:  () -> Unit,
    deepChatId:   String? = null,
    deepChatName: String? = null,
) {
    var activeChatId   by rememberSaveable { mutableStateOf(deepChatId) }
    var activeChatName by rememberSaveable { mutableStateOf(deepChatName) }
    var showProfile    by rememberSaveable { mutableStateOf(false) }

    // Keep last chat populated so content stays alive during the exit slide
    var lastChatId   by rememberSaveable { mutableStateOf(deepChatId) }
    var lastChatName by rememberSaveable { mutableStateOf(deepChatName) }
    if (activeChatId != null) {
        lastChatId   = activeChatId
        lastChatName = activeChatName
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ① ChatList — ALWAYS alive, NEVER recreated ─────────────────────────
        ChatListScreen(
            onOpenChat    = { chatId, chatName ->
                activeChatId   = chatId
                activeChatName = chatName
            },
            onOpenProfile = { showProfile = true },
        )

        // ② Chat screen slides in on top ──────────────────────────────────────
        val chatVisible = activeChatId != null
        AnimatedVisibility(
            visible  = chatVisible,
            enter    = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it } +
                       fadeIn(tween(220)),
            exit     = slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { it } +
                       fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (lastChatId != null) {
                BackHandler(enabled = chatVisible) { activeChatId = null }
                ChatScreen(
                    chatId   = lastChatId!!,
                    chatName = lastChatName ?: "Chat",
                    onBack   = { activeChatId = null },
                )
            }
        }

        // ③ Profile screen slides in on top ───────────────────────────────────
        AnimatedVisibility(
            visible  = showProfile,
            enter    = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it } +
                       fadeIn(tween(220)),
            exit     = slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { it } +
                       fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            BackHandler(enabled = showProfile) { showProfile = false }
            ProfileScreen(
                onBack      = { showProfile = false },
                onLoggedOut = onLoggedOut,
            )
        }
    }
}
