package com.sleek.app.ui.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sleek.app.data.model.Chat
import com.sleek.app.data.model.User
import com.sleek.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (chatId: String, chatName: String) -> Unit,
    viewModel:  ChatListViewModel = hiltViewModel(),
) {
    val chats     by viewModel.chats.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error     by viewModel.error.collectAsStateWithLifecycle()
    val myId      by viewModel.userId.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title  = { Text("Messages", style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    IconButton(onClick = { /* TODO: search */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                    }
                    IconButton(onClick = { /* TODO: new DM */ }) {
                        Icon(Icons.Default.Edit, contentDescription = "New chat", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                // ── Loading skeletons ─────────────────────────────────────────
                isLoading -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        repeat(7) { ChatItemSkeleton() }
                    }
                }

                // ── Error state ───────────────────────────────────────────────
                error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text  = error ?: "Something went wrong",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ErrorRed,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        FilledTonalButton(onClick = { viewModel.loadChats() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }

                // ── Empty state ───────────────────────────────────────────────
                chats.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector       = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint              = TextSecondary,
                            modifier          = Modifier.size(48.dp),
                        )
                        Text(
                            text  = "No conversations yet",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text      = "Start a new chat using the ✏ button above",
                            style     = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.padding(horizontal = 40.dp),
                        )
                    }
                }

                // ── Chat list ─────────────────────────────────────────────────
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(chats, key = { it.id }) { chat ->
                            val peer = myId?.let { viewModel.getDmPeer(chat, it) }
                            ChatListItem(
                                chat    = chat,
                                peer    = peer,
                                onClick = { onOpenChat(chat.id, peer?.username ?: "Chat") },
                            )
                            HorizontalDivider(
                                color     = BorderSubtle,
                                thickness = 0.5.dp,
                                modifier  = Modifier.padding(start = 76.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatListItem(
    chat:    Chat,
    peer:    User?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar
        if (peer?.avatarUrl != null) {
            AsyncImage(
                model             = peer.avatarUrl,
                contentDescription = peer.username,
                contentScale      = ContentScale.Crop,
                modifier          = Modifier.size(48.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentDim),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = (peer?.username ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(color = Accent),
                )
            }
        }

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = peer?.username ?: "Unknown",
                    style    = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text  = chat.lastMessage?.createdAt?.let { formatChatTime(it) } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text     = when {
                    chat.lastMessage == null          -> "No messages yet"
                    chat.lastMessage.deletedAt != null -> "Message deleted"
                    else                              -> chat.lastMessage.content
                },
                style    = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color    = if (chat.lastMessage?.deletedAt != null) TextMuted else TextSecondary,
            )
        }
    }
}

@Composable
private fun ChatItemSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(SurfaceHigh))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp).background(SurfaceHigh, MaterialTheme.shapes.small))
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(12.dp).background(SurfaceHigh, MaterialTheme.shapes.small))
        }
    }
}

private fun formatChatTime(isoString: String): String = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    val date = sdf.parse(isoString) ?: return ""
    val diff = Date().time - date.time
    when {
        diff < 60_000L        -> "now"
        diff < 3_600_000L     -> "${diff / 60_000}m"
        diff < 86_400_000L    -> SimpleDateFormat("h:mm a",  Locale.getDefault()).format(date)
        else                  -> SimpleDateFormat("MMM d",   Locale.getDefault()).format(date)
    }
} catch (_: Exception) { "" }
