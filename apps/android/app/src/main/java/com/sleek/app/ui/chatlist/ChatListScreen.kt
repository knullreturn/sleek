package com.sleek.app.ui.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
    onOpenChat:   (chatId: String, chatName: String) -> Unit,
    viewModel:    ChatListViewModel = hiltViewModel(),
) {
    val chats     by viewModel.chats.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val myId      by viewModel.userId.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = {
                    Text("Messages", style = MaterialTheme.typography.headlineMedium)
                },
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
            if (isLoading) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    repeat(6) { ChatItemSkeleton() }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(chats, key = { it.id }) { chat ->
                        val peer = myId?.let { viewModel.getDmPeer(chat, it) }
                        ChatListItem(
                            chat     = chat,
                            peer     = peer,
                            onClick  = { onOpenChat(chat.id, peer?.username ?: "Chat") },
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
        Box {
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
                text     = chat.lastMessage?.content ?: "No messages yet",
                style    = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

private fun formatChatTime(isoString: String): String {
    return try {
        val sdf   = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date  = sdf.parse(isoString) ?: return ""
        val now   = Date()
        val diff  = now.time - date.time
        when {
            diff < 60_000         -> "now"
            diff < 3_600_000      -> "${diff / 60_000}m"
            diff < 86_400_000     -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
            else                  -> SimpleDateFormat("MMM d",  Locale.getDefault()).format(date)
        }
    } catch (_: Exception) { "" }
}
