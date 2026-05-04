package com.sleek.app.ui.chatlist

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sleek.app.ui.theme.*

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

    if (showNewDm) {
        NewDmSheet(
            viewModel  = viewModel,
            onDismiss  = { viewModel.dismissNewDm() },
            onOpenChat = onOpenChat,
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Black)) {

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(Accent, AccentLight))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("S", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary, fontSize = 16.sp))
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
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onOpenProfile() },
                contentAlignment = Alignment.Center,
            ) {
                if (me?.avatarUrl != null) {
                    AsyncImage(model = me!!.avatarUrl, contentDescription = "Profile", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(AccentDim), contentAlignment = Alignment.Center) {
                        Text((me?.username ?: "?").take(1).uppercase(), style = MaterialTheme.typography.labelMedium.copy(color = Accent))
                    }
                }
            }
        }

        // ── Search bar + New DM button ────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value         = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Search conversations…", style = MaterialTheme.typography.bodySmall) },
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(17.dp)) },
                trailingIcon  = if (searchQuery.isNotBlank()) {{
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(15.dp))
                    }
                }} else null,
                singleLine = true,
                colors     = TextFieldDefaults.colors(
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
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(AccentDim).clickable { viewModel.showNewDm() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = "New chat", tint = Accent, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Content ───────────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading  -> Column(modifier = Modifier.fillMaxWidth()) { repeat(7) { ChatItemSkeleton() } }

                error != null -> Column(
                    modifier            = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(error ?: "Something went wrong", style = MaterialTheme.typography.bodyMedium, color = ErrorRed, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    FilledTonalButton(onClick = { viewModel.loadChats() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retry")
                    }
                }

                chats.isEmpty() -> Column(
                    modifier            = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(52.dp))
                    Text(
                        text  = if (searchQuery.isBlank()) "No conversations yet" else "No results for \"$searchQuery\"",
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

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(chats, key = { it.id }) { chat ->
                        val peer = myId?.let { viewModel.getDmPeer(chat, it) }
                        ChatListItem(chat = chat, peer = peer, myId = myId, onClick = { onOpenChat(chat.id, peer?.username ?: "Chat") })
                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(start = 76.dp))
                    }
                }
            }
        }
    }
}
