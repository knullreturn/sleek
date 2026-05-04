package com.sleek.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sleek.app.data.model.Message
import com.sleek.app.ui.chat.components.*
import com.sleek.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId:    String,
    chatName:  String,
    onBack:    () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    // ── MUST be first: updates StateFlow with cache before first collection ──
    remember(chatId) { viewModel.init(chatId) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val myId  by viewModel.myUserId.collectAsStateWithLifecycle(initialValue = null)

    // Mark seen only when the last peer message ID changes — not on every recompose
    val lastPeerMsgId = state.messages.lastOrNull { it.senderId != myId }?.id
    LaunchedEffect(lastPeerMsgId) {
        lastPeerMsgId?.let { viewModel.markSeen(it) }
    }

    val listState    = rememberLazyListState()
    var replyingTo   by remember { mutableStateOf<Message?>(null) }
    var inputValue   by remember { mutableStateOf(TextFieldValue()) }
    val focusReq     = remember { FocusRequester() }
    var contextMsg   by remember { mutableStateOf<Message?>(null) }  // long-press target
    val clipboard    = LocalClipboardManager.current
    val coroutineScope        = rememberCoroutineScope()
    var highlightedMessageId  by remember { mutableStateOf<String?>(null) }

    // Hoisted so onReplyTap can compute the scroll index
    val grouped = remember(state.messages) { groupByDate(state.messages) }

    // Derived: if last message is from peer, green timestamps reset
    val peerHasReplied = remember(state.messages) {
        state.messages.lastOrNull()?.senderId != myId
    }

    // ── Scroll to bottom ──────────────────────────────────────────────────────
    // Initial scroll fires when contentVisible=true (LazyColumn just rendered).
    // Subsequent scrolls fire on new incoming messages.
    val initialScrollDone = remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size) {
        // Only animate-scroll after initial scroll is done (new messages arriving)
        if (initialScrollDone.value && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }
    // ── Decouple slide from message render ────────────────────────────────────
    // LazyColumn NEVER renders during the 340ms slide animation.
    // Room collects messages in the background (IO thread) while the slide plays.
    // At 300ms (tail of the deceleration curve), we flip contentVisible → the
    // LazyColumn renders ONCE with all messages already ready. Zero jank.
    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        contentVisible = true
    }

    // ── Initial scroll: jump to bottom the moment LazyColumn first renders ────
    LaunchedEffect(contentVisible) {
        if (contentVisible && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.size - 1)   // instant, before fade-in
            initialScrollDone.value = true
        }
    }

    // ── Keyboard open → scroll to bottom ──────────────────────────────────────
    val density   = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && state.messages.isNotEmpty()) {
            kotlinx.coroutines.delay(50)   // let layout shift settle first
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    // ── Typing indicator appears → scroll to show it ──────────────────────────
    LaunchedEffect(state.typingUsers.isNotEmpty()) {
        if (state.typingUsers.isNotEmpty()) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    // ── Message context menu (long-press) ─────────────────────────────────────
    contextMsg?.let { msg ->
        val isOwnMsg = msg.senderId == myId
        MessageContextMenu(
            message   = msg,
            isOwn     = isOwnMsg,
            onDismiss = { contextMsg = null },
            onCopy    = { clipboard.setText(AnnotatedString(msg.content)) },
            onReply   = { replyingTo = msg },
            onEdit    = { newContent -> viewModel.editMessage(msg.id, newContent) },
            onPin     = { viewModel.pinMessage(msg.id, pin = true) },
            onUnpin   = { viewModel.pinMessage(msg.id, pin = false) },
            onDelete  = { viewModel.deleteMessage(msg.id) },
        )
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    // Avatar + name row
                    val peer = state.peer
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (peer?.avatarUrl != null) {
                                AsyncImage(
                                    model             = peer.avatarUrl,
                                    contentDescription = peer.username,
                                    contentScale      = ContentScale.Crop,
                                    modifier          = Modifier.fillMaxSize(),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(SurfaceHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text  = (peer?.username ?: chatName).take(1).uppercase(),
                                        style = MaterialTheme.typography.labelMedium.copy(color = Accent),
                                    )
                                }
                            }
                        }

                        // Name only — no tag
                        Column {
                            Text(
                                text  = peer?.username ?: chatName,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: in-chat search */ }) {
                        Icon(
                            imageVector        = Icons.Default.Search,
                            contentDescription = "Search messages",
                            tint               = TextSecondary,
                        )
                    }
                    IconButton(onClick = { /* TODO: pinned messages */ }) {
                        Icon(
                            imageVector        = Icons.Default.PushPin,
                            contentDescription = "Pinned messages",
                            tint               = TextSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        bottomBar = {
            Column {
                // Reply bar
                AnimatedVisibility(
                    visible = replyingTo != null,
                    enter   = slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
                    exit    = slideOutVertically(tween(160)) { it } + fadeOut(tween(160)),
                ) {
                    replyingTo?.let { msg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Surface)
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Reply icon
                            Icon(
                                imageVector        = androidx.compose.material.icons.Icons.Default.Reply,
                                contentDescription = null,
                                tint               = Accent,
                                modifier           = Modifier.size(20.dp),
                            )

                            // Accent left border + content
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .background(SurfaceHigh),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Accent left border
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(44.dp)
                                        .background(Accent)
                                )
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text  = msg.sender.username ?: "Unknown",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color      = Accent,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        ),
                                    )
                                    Spacer(Modifier.height(1.dp))
                                    Text(
                                        text     = msg.content,
                                        style    = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            // Close button
                            IconButton(onClick = { replyingTo = null }) {
                                Icon(
                                    imageVector        = Icons.Default.Close,
                                    contentDescription = "Cancel reply",
                                    tint               = TextSecondary,
                                    modifier           = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }   // end AnimatedVisibility

                // ── Input bar ─────────────────────────────────────────────────
                val hasText = inputValue.text.isNotBlank()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    TextField(
                        value         = inputValue,
                        onValueChange = {
                            inputValue = it
                            viewModel.sendTyping(it.text.isNotBlank())
                        },
                        modifier      = Modifier
                            .weight(1f)
                            .focusRequester(focusReq),
                        placeholder   = {
                            Text(
                                "Message…",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        // ── Send button lives INSIDE the field ────────────────
                        trailingIcon  = {
                            AnimatedVisibility(
                                visible = hasText,
                                enter   = scaleIn(tween(150)) + fadeIn(tween(150)),
                                exit    = scaleOut(tween(100)) + fadeOut(tween(100)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Accent)
                                        .clickable {
                                            viewModel.sendMessage(inputValue.text.trim(), replyingTo?.id)
                                            inputValue = TextFieldValue()
                                            replyingTo = null
                                            viewModel.sendTyping(false)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector       = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint              = TextPrimary,
                                        modifier          = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                        singleLine    = false,
                        maxLines      = 5,
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = SurfaceHigh,
                            unfocusedContainerColor = SurfaceHigh,
                            focusedTextColor        = TextPrimary,
                            unfocusedTextColor      = TextPrimary,
                            cursorColor             = Accent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        shape = RoundedCornerShape(28.dp),   // pill shape
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Slide plays clean. At 300ms, content fades in over 180ms (overlaps the slide's deceleration tail)
            androidx.compose.animation.AnimatedVisibility(
                visible = contentVisible,
                enter   = fadeIn(tween(180, easing = LinearOutSlowInEasing)),
            ) {
            if (state.isLoading) {
                // Skeleton (first-time only)
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(5) { i ->
                        val isOwn = i % 2 == 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (isOwn) 0.55f else 0.5f)
                                    .height(42.dp)
                                    .background(SurfaceHigh, if (isOwn) BubbleShapeOwn else BubbleShapeOther)
                            )
                        }
                    }
                }
            } else {
                // grouped is already computed above — reuse it

                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                        grouped.forEach { (dateLabel, msgs) ->
                            // Date separator
                            item(key = "sep_$dateLabel") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(SurfaceMid, MaterialTheme.shapes.small)
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Text(dateLabel, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            // Messages — NO animateItem() to prevent blink on initial load
                            itemsIndexed(msgs, key = { _, m -> m.id }) { index, msg ->
                                val isOwn      = msg.senderId == myId
                                val prev       = if (index > 0) msgs[index - 1] else null
                                val showAvatar = prev == null || prev.senderId != msg.senderId
                                val isSeen     = isOwn && !peerHasReplied && msg.id == state.seenUpToId

                                MessageBubble(
                                    message      = msg,
                                    isOwn        = isOwn,
                                    showAvatar   = showAvatar,
                                    isSeen       = isSeen,
                                    isHighlighted = msg.id == highlightedMessageId,
                                    onLongPress  = { contextMsg = msg },
                                    onReplyTap   = { replyMsgId ->
                                        coroutineScope.launch {
                                            // Calculate LazyColumn index (msgs + separators)
                                            var idx = 0
                                            var found = false
                                            for ((_, grpMsgs) in grouped) {
                                                idx++ // separator
                                                val pos = grpMsgs.indexOfFirst { it.id == replyMsgId }
                                                if (pos >= 0) { idx += pos; found = true; break }
                                                idx += grpMsgs.size
                                            }
                                            if (found) {
                                                listState.animateScrollToItem(idx)
                                                highlightedMessageId = replyMsgId
                                                delay(1600)
                                                highlightedMessageId = null
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        // Typing indicator
                        if (state.typingUsers.isNotEmpty()) {
                            item(key = "typing") {
                                TypingIndicator(names = state.typingUsers)
                            }
                        }
                }
            }   // end else
            }   // end AnimatedVisibility
        }
    }
}

private fun groupByDate(messages: List<Message>): List<Pair<String, List<Message>>> {
    val result  = mutableListOf<Pair<String, MutableList<Message>>>()
    val sdf     = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    val dayFmt  = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    val todayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today   = todayFmt.format(Date())

    for (msg in messages) {
        val date  = try { sdf.parse(msg.createdAt) } catch (_: Exception) { null } ?: continue
        val dayKey = todayFmt.format(date)
        val label  = when (dayKey) {
            today -> "Today"
            else  -> dayFmt.format(date)
        }
        val last = result.lastOrNull()
        if (last?.first == label) last.second.add(msg)
        else result.add(label to mutableListOf(msg))
    }
    return result
}
