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

    // Mark seen when the last peer message changes
    val lastPeerMsgId = state.messages.lastOrNull { it.senderId != myId }?.id
    LaunchedEffect(lastPeerMsgId) { lastPeerMsgId?.let { viewModel.markSeen(it) } }

    val listState    = rememberLazyListState()
    var replyingTo   by remember { mutableStateOf<Message?>(null) }
    var inputValue   by remember { mutableStateOf(TextFieldValue()) }
    var contextMsg   by remember { mutableStateOf<Message?>(null) }
    val clipboard    = LocalClipboardManager.current
    val scope        = rememberCoroutineScope()
    var highlightId  by remember { mutableStateOf<String?>(null) }

    // Pre-computed in ViewModel on Dispatchers.Default — zero UI thread cost
    val grouped        = state.grouped
    val peerHasReplied = state.peerHasReplied

    // ── Scroll logic ──────────────────────────────────────────────────────────

    // Initial load — jump to bottom instantly, no animation
    LaunchedEffect(state.messages.isNotEmpty()) {
        if (state.messages.isNotEmpty())
            listState.scrollToItem(maxOf(0, state.messages.size - 1))
    }

    // New message — only animate if user is near bottom OR it's their own message
    LaunchedEffect(state.messages.size) {
        val msgs = state.messages
        if (msgs.isEmpty()) return@LaunchedEffect
        val lastMsg       = msgs.last()
        val totalItems    = listState.layoutInfo.totalItemsCount
        val lastVisible   = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val isNearBottom  = lastVisible >= totalItems - 5
        val isMine        = lastMsg.senderId == myId
        if (isMine || isNearBottom)
            listState.animateScrollToItem(totalItems)
    }

    // Keyboard opens → scroll to keep messages visible
    val density   = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && state.messages.isNotEmpty()) {
            delay(50)
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
        }
    }

    // Typing indicator appears → scroll to show it
    LaunchedEffect(state.typingUsers.isNotEmpty()) {
        if (state.typingUsers.isNotEmpty())
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
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
                            if (sleepMode) {
                                Surface(
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
                    IconButton(onClick = { /* TODO: in-chat search */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = AppTheme.colors.textSecondary)
                    }
                    IconButton(onClick = { /* TODO: pinned messages */ }) {
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
                    viewModel.sendMessage(inputValue.text.trim(), replyingTo?.id)
                    inputValue = TextFieldValue()
                    replyingTo = null
                    viewModel.sendTyping(false)
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            MessageList(
                grouped              = grouped,
                myId                 = myId,
                peerHasReplied       = peerHasReplied,
                seenUpToId           = state.seenUpToId,
                isLoading            = state.isLoading,
                listState            = listState,
                highlightedMessageId = highlightId,
                typingUsers          = state.typingUsers,
                onLongPress          = { contextMsg = it },
                onReplyTap           = { replyMsgId ->
                    scope.launch {
                        val idx = findScrollIndex(grouped, replyMsgId)
                        if (idx >= 0) {
                            listState.animateScrollToItem(idx)
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
