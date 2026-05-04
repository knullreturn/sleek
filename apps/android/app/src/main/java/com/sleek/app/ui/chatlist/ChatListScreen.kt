package com.sleek.app.ui.chatlist

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onOpenChat:    (chatId: String, chatName: String) -> Unit,
    onOpenProfile: () -> Unit,
    viewModel:     ChatListViewModel = hiltViewModel(),
) {
    val chats       by viewModel.chats.collectAsStateWithLifecycle()
    val isLoading   by viewModel.isLoading.collectAsStateWithLifecycle()
    val error       by viewModel.error.collectAsStateWithLifecycle()
    val me          by viewModel.me.collectAsStateWithLifecycle()
    val myId        by viewModel.userId.collectAsStateWithLifecycle(initialValue = null)
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showNewDm   by viewModel.showNewDm.collectAsStateWithLifecycle()

    // ── New DM bottom sheet ───────────────────────────────────────────────────
    if (showNewDm) {
        NewDmSheet(
            viewModel  = viewModel,
            onDismiss  = { viewModel.dismissNewDm() },
            onOpenChat = onOpenChat,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // SLEEK logo + name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                // "S" logo badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(Accent, AccentLight))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = "S",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color    = TextPrimary,
                            fontSize = 16.sp,
                        ),
                    )
                }
                Text(
                    text  = "SLEEK",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        brush         = Brush.linearGradient(listOf(Accent, AccentLight)),
                        letterSpacing = 3.sp,
                        fontSize      = 20.sp,
                    ),
                )
            }

            // User avatar → opens settings
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onOpenProfile() },
                contentAlignment = Alignment.Center,
            ) {
                if (me?.avatarUrl != null) {
                    AsyncImage(
                        model             = me!!.avatarUrl,
                        contentDescription = "Profile",
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
                            text  = (me?.username ?: "?").take(1).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(color = Accent),
                        )
                    }
                }
            }
        }

        // ── Search bar + "+" button ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value         = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier      = Modifier.weight(1f),
                placeholder   = {
                    Text(
                        "Search conversations…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingIcon   = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(17.dp))
                },
                trailingIcon  = if (searchQuery.isNotBlank()) {{
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(15.dp))
                    }
                }} else null,
                singleLine    = true,
                colors        = TextFieldDefaults.colors(
                    focusedContainerColor   = SurfaceHigh,
                    unfocusedContainerColor = SurfaceHigh,
                    focusedTextColor        = TextPrimary,
                    unfocusedTextColor      = TextPrimary,
                    cursorColor             = Accent,
                    focusedIndicatorColor   = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                shape             = RoundedCornerShape(12.dp),
            )

            // "+" new DM button — matches natural TextField height
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentDim)
                    .clickable { viewModel.showNewDm() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = "New chat", tint = Accent, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Content ───────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        repeat(7) { ChatItemSkeleton() }
                    }
                }

                error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text      = error ?: "Something went wrong",
                            style     = MaterialTheme.typography.bodyMedium,
                            color     = ErrorRed,
                            textAlign = TextAlign.Center,
                            modifier  = Modifier.padding(horizontal = 32.dp),
                        )
                        FilledTonalButton(onClick = { viewModel.loadChats() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }

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
                            modifier          = Modifier.size(52.dp),
                        )
                        Text(
                            text  = if (searchQuery.isBlank()) "No conversations yet"
                                    else "No results for \"$searchQuery\"",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (searchQuery.isBlank()) {
                            Text(
                                text      = "Tap + to find someone and start chatting",
                                style     = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.padding(horizontal = 40.dp),
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(chats, key = { it.id }) { chat ->
                            val peer = myId?.let { viewModel.getDmPeer(chat, it) }
                            ChatListItem(
                                chat    = chat,
                                peer    = peer,
                                myId    = myId,
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

// ── New DM Bottom Sheet ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewDmSheet(
    viewModel:  ChatListViewModel,
    onDismiss:  () -> Unit,
    onOpenChat: (chatId: String, chatName: String) -> Unit,
) {
    val dmQuery    by viewModel.dmQuery.collectAsStateWithLifecycle()
    val dmResults  by viewModel.dmResults.collectAsStateWithLifecycle()
    val dmSearching by viewModel.dmSearching.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Surface,
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderMid),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("New Message", style = MaterialTheme.typography.titleMedium)

            // Search field
            TextField(
                value         = dmQuery,
                onValueChange = { viewModel.setDmQuery(it) },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Search by username or #tag", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon   = {
                    if (dmSearching)
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
                    else
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                },
                singleLine    = true,
                colors        = TextFieldDefaults.colors(
                    focusedContainerColor   = SurfaceHigh,
                    unfocusedContainerColor = SurfaceHigh,
                    focusedTextColor        = TextPrimary,
                    unfocusedTextColor      = TextPrimary,
                    cursorColor             = Accent,
                    focusedIndicatorColor   = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                shape = RoundedCornerShape(12.dp),
            )

            // Results
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (dmQuery.length >= 2 && dmResults.isEmpty() && !dmSearching) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No users found for \"$dmQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                items(dmResults, key = { it.id }) { user ->
                    UserSearchItem(
                        user    = user,
                        onClick = {
                            viewModel.startDm(user.id) { chatId, chatName ->
                                onOpenChat(chatId, chatName)
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── User search result item ───────────────────────────────────────────────────
@Composable
private fun UserSearchItem(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar
        if (user.avatarUrl != null) {
            AsyncImage(
                model             = user.avatarUrl,
                contentDescription = user.username,
                contentScale      = ContentScale.Crop,
                modifier          = Modifier.size(40.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentDim),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = (user.username ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(color = Accent),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = user.username ?: "Unknown",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text  = "#${user.tag}",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

// ── Chat list item ────────────────────────────────────────────────────────────
@Composable
private fun ChatListItem(
    chat:    Chat,
    peer:    User?,
    myId:    String?,
    onClick: () -> Unit,
) {
    val isLastMsgOwn = chat.lastMessage?.senderId == myId

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
                modifier          = Modifier.size(50.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(AccentDim),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = (peer?.username ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color    = Accent,
                        fontSize = 20.sp,
                    ),
                )
            }
        }

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
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(Modifier.height(2.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                if (isLastMsgOwn) {
                    Text("You: ", style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
                }
                Text(
                    text     = when {
                        chat.lastMessage == null           -> "No messages yet"
                        chat.lastMessage.deletedAt != null -> "Message deleted"
                        else                               -> chat.lastMessage.content
                    },
                    style    = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color    = if (chat.lastMessage?.deletedAt != null) TextMuted else TextSecondary,
                )
            }
        }
    }
}

// ── Skeleton ──────────────────────────────────────────────────────────────────
@Composable
private fun ChatItemSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(SurfaceHigh))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp).background(SurfaceHigh, RoundedCornerShape(4.dp)))
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(12.dp).background(SurfaceHigh, RoundedCornerShape(4.dp)))
        }
    }
}

private fun formatChatTime(isoString: String): String = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    val date = sdf.parse(isoString) ?: return ""
    val diff = Date().time - date.time
    when {
        diff < 60_000L     -> "now"
        diff < 3_600_000L  -> "${diff / 60_000}m"
        diff < 86_400_000L -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        else               -> SimpleDateFormat("MMM d",  Locale.getDefault()).format(date)
    }
} catch (_: Exception) { "" }
