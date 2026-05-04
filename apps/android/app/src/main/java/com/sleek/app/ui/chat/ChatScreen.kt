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
    val state  by viewModel.state.collectAsStateWithLifecycle()
    val myId   by viewModel.myUserId.collectAsStateWithLifecycle(initialValue = null)

    // init synchronously during composition — cache hit sets state before first frame
    remember(chatId) { viewModel.init(chatId) }

    // Mark seen on last peer message when screen opens / messages update
    LaunchedEffect(state.messages) {
        val lastPeer = state.messages.lastOrNull { it.senderId != myId }
        lastPeer?.let { viewModel.markSeen(it.id) }
    }

    val listState    = rememberLazyListState()
    var replyingTo   by remember { mutableStateOf<Message?>(null) }
    var inputValue   by remember { mutableStateOf(TextFieldValue()) }
    val focusReq     = remember { FocusRequester() }

    // Derived: if last message is from peer, green timestamps reset
    val peerHasReplied = remember(state.messages) {
        state.messages.lastOrNull()?.senderId != myId
    }

    // Auto-scroll to bottom — only on initial load + new messages, not every recompose
    val initialScrollDone = remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && !initialScrollDone.value && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.size - 1)  // instant, no animation on load
            initialScrollDone.value = true
        }
    }
    LaunchedEffect(state.messages.size) {
        if (initialScrollDone.value && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)  // animate only new messages
        }
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
                                        .background(AccentDim),
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        bottomBar = {
            Column {
                // Reply bar
                AnimatedVisibility(visible = replyingTo != null) {
                    replyingTo?.let { msg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(36.dp)
                                    .background(Accent)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text  = "Replying to ${msg.sender.username}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    text  = msg.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                            }
                            IconButton(onClick = { replyingTo = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel reply", tint = TextSecondary)
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
            if (state.isLoading) {
                // Skeleton bubbles (only on very first load — cache skips this)
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
                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                        val grouped = groupByDate(state.messages)
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
                                    message     = msg,
                                    isOwn       = isOwn,
                                    showAvatar  = showAvatar,
                                    isSeen      = isSeen,
                                    onLongPress = { /* TODO: context menu */ },
                                    onReplyTap  = { /* TODO: scroll to */ },
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
            }
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
