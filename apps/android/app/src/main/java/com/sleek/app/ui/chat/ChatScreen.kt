package com.sleek.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sleek.app.data.model.Message
import com.sleek.app.ui.chat.components.MessageContextMenu
import com.sleek.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId:    String,
    chatName:  String,
    onBack:    () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    remember(chatId) { viewModel.init(chatId) }

    val state     by viewModel.state.collectAsStateWithLifecycle()
    val myId      by viewModel.myUserId.collectAsStateWithLifecycle(initialValue = null)
    val sleepMode by viewModel.sleepModeEnabled.collectAsStateWithLifecycle()

    // ── State declarations (order matters: listState used before UI state) ────
    val listState         = rememberLazyListState()
    val scope             = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic            = androidx.compose.ui.platform.LocalHapticFeedback.current
    val clipboard         = LocalClipboardManager.current
    var highlightId  by remember { mutableStateOf<String?>(null) }
    var inputValue   by remember { mutableStateOf(TextFieldValue()) }
    var replyingTo   by remember { mutableStateOf<Message?>(null) }
    var contextMsg   by remember { mutableStateOf<Message?>(null) }

    // Mark seen when the last peer message changes
    // Fix: only mark seen if message is actually visible in the list
    // Previously fired immediately on message arrival regardless of scroll position
    val lastPeerMsgId = state.messages.lastOrNull { it.senderId != myId }?.id
    LaunchedEffect(lastPeerMsgId) {
        if (lastPeerMsgId == null) return@LaunchedEffect
        // Wait for layout to settle, then check visibility
        delay(300)
        val visibleIds = listState.layoutInfo.visibleItemsInfo.map { it.key }
        if (visibleIds.contains(lastPeerMsgId)) {
            viewModel.markSeen(lastPeerMsgId)
        }
    }

    // Fix: clear draft and reply when switching chats (previously carried to next chat)
    LaunchedEffect(chatId) {
        inputValue = TextFieldValue()
        replyingTo = null
    }

    // Restore scroll position for this chat (LRU memory in ViewModel)
    val savedScroll = remember(chatId) { viewModel.restoreScrollPosition(chatId) }

    // Save scroll position when leaving this chat
    DisposableEffect(chatId) {
        onDispose {
            viewModel.saveScrollPosition(
                chatId,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }

    // Typing debounce — wait 300ms before showing indicator (prevents flicker)
    var showTyping by remember { mutableStateOf(false) }
    LaunchedEffect(state.typingUsers.isNotEmpty()) {
        if (state.typingUsers.isNotEmpty()) {
            delay(300)
            showTyping = true
        } else {
            showTyping = false
        }
    }

    // Pre-computed in ViewModel on Dispatchers.Default — zero UI thread cost
    val grouped        = state.grouped
    val peerHasReplied = state.peerHasReplied
    val lastListIndex  = remember(grouped, showTyping) {
        maxOf(0, chatLazyItemCount(grouped, hasTyping = showTyping) - 1)
    }

    // ── Content sequencing — slide in page first, THEN render messages ────────
    // Without this, the page slide animation and full LazyColumn composition
    // compete for the same frames → both look laggy.
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(290) // wait for MainScreen slide-in animation (280ms) to finish
        contentVisible = true
    }

    // ── Scroll logic ──────────────────────────────────────────────────────────

    // Initial load — restore saved position or jump to bottom
    LaunchedEffect(contentVisible, state.messages.isNotEmpty()) {
        if (contentVisible && state.messages.isNotEmpty()) {
            if (savedScroll != null) {
                listState.scrollToItem(
                    index        = savedScroll.first.coerceAtMost(lastListIndex),
                    scrollOffset = savedScroll.second,
                )
            } else {
                listState.scrollToItem(lastListIndex)
            }
        }
    }

    // New message — only animate if near bottom OR it's the user's own message
    LaunchedEffect(state.messages.size) {
        if (!contentVisible) return@LaunchedEffect
        val msgs = state.messages
        if (msgs.isEmpty()) return@LaunchedEffect
        val lastMsg      = msgs.last()
        val totalItems   = listState.layoutInfo.totalItemsCount
        val lastVisible  = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val isNearBottom = lastVisible >= totalItems - 5
        val isMine       = lastMsg.senderId == myId
        if (isMine || isNearBottom)
            listState.scrollToChatItem(maxOf(0, totalItems - 1))
    }

    // Keyboard open → scroll list to stay at bottom so messages aren't hidden.
    // Fix: the previous version used delay(50) + animateScrollToItem which created a
    // double-movement jump (layout resized AND animated scroll fought each other).
    // Instant scrollToItem with no delay fires in sync with the resize — one clean movement.
    // Guard: only scroll if user is already near the bottom (don't yank them away from history).
    val density   = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && state.messages.isNotEmpty()) {
            val totalItems   = listState.layoutInfo.totalItemsCount
            val lastVisible  = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val isNearBottom = lastVisible >= totalItems - 6
            if (isNearBottom) {
                listState.scrollToItem(lastListIndex)  // instant — no animation, no jump
            }
        }
    }

    // Typing indicator → scroll to show it
    LaunchedEffect(showTyping) {
        if (showTyping) {
            val totalItems   = listState.layoutInfo.totalItemsCount
            val lastVisible  = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val isNearBottom = lastVisible >= totalItems - 5
            if (isNearBottom) listState.scrollToChatItem(lastListIndex)
        }
    }

    // ── Context menu ──────────────────────────────────────────────────────────
    contextMsg?.let { msg ->
        MessageContextMenu(
            message   = msg,
            isOwn     = msg.senderId == myId,
            onDismiss = { contextMsg = null },
            onCopy    = { clipboard.setText(AnnotatedString(msg.content)) },
            onReply   = { replyingTo = msg },
            onEdit    = { viewModel.editMessage(msg.id, it) },
            onPin     = { viewModel.pinMessage(msg.id, pin = true) },
            onUnpin   = { viewModel.pinMessage(msg.id, pin = false) },
            onDelete  = { viewModel.deleteMessage(msg.id) },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val peer = state.peer
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                            if (peer?.avatarUrl != null) {
                                AsyncImage(model = peer.avatarUrl, contentDescription = peer.username, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                    Text(
                                        text  = (peer?.username ?: chatName).take(1).uppercase(),
                                        style = MaterialTheme.typography.labelMedium.copy(color = Accent),
                                    )
                                }
                            }
                        }
                        Column {
                            Text(text = peer?.username ?: chatName, style = MaterialTheme.typography.titleMedium)
                            when {
                                // MY sleep mode badge — I turned it on, show it to me
                                sleepMode -> Surface(
                                    shape    = RoundedCornerShape(4.dp),
                                    color    = Accent.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(top = 2.dp),
                                ) {
                                    Text(
                                        text  = "💤 Sleep Mode",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color         = Accent,
                                            fontSize      = 9.sp,
                                            letterSpacing = 0.5.sp,
                                        ),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    )
                                }
                                // PEER's sleep mode — they turned it on, show it to me
                                state.peerSleeping -> Text(
                                    text  = "💤 Do Not Disturb",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color         = Accent,
                                        fontSize      = 9.sp,
                                        letterSpacing = 0.5.sp,
                                    ),
                                )
                                // Peer is online (not sleeping)
                                state.peerOnline -> Text(
                                    text  = "● Online",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color     = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                        fontSize  = 9.sp,
                                    ),
                                )
                                // Peer is offline
                                else -> {} // no subtitle when offline — less noise
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppTheme.colors.textSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { snackbarHostState.showSnackbar("Search coming soon") } }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = AppTheme.colors.textSecondary)
                    }
                    IconButton(onClick = { scope.launch { snackbarHostState.showSnackbar("Pinned messages coming soon") } }) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pins", tint = AppTheme.colors.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor         = MaterialTheme.colorScheme.surface,
                    titleContentColor      = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        bottomBar = {
            ChatInputBar(
                replyingTo    = replyingTo,
                onCancelReply = { replyingTo = null },
                inputValue    = inputValue,
                onValueChange = { inputValue = it; viewModel.sendTyping(it.text.isNotBlank()) },
                onSend        = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    viewModel.sendMessage(inputValue.text.trim(), replyingTo?.id)
                    inputValue = TextFieldValue()
                    replyingTo = null
                    viewModel.sendTyping(false)
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(
                visible = contentVisible,
                enter   = fadeIn(tween(200, easing = LinearOutSlowInEasing)),
            ) {
                MessageList(
                    grouped              = grouped,
                    myId                 = myId,
                    peerHasReplied       = peerHasReplied,
                    seenUpToId           = state.seenUpToId,
                    isLoading            = state.isLoading,
                    listState            = listState,
                    highlightedMessageId = highlightId,
                    typingUsers          = if (showTyping) state.typingUsers else emptyList(),
                    hasMoreMessages      = state.hasMoreMessages,
                    isLoadingOlder       = state.isLoadingOlder,
                    onLoadOlder          = { viewModel.loadOlderMessages() },
                    onLongPress          = { contextMsg = it },
                    onReplyTap           = { replyMsgId ->
                        scope.launch {
                            val idx = findScrollIndex(grouped, replyMsgId)
                            if (idx >= 0) {
                                listState.scrollToChatItem(idx)
                                highlightId = replyMsgId
                                delay(1600)
                                highlightId = null
                            }
                        }
                    },
                    onSwipeReply         = { replyingTo = it },
                )
            }
        }
    }
}
