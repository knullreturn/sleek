package com.sleek.app.ui.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sleek.app.data.model.Chat
import com.sleek.app.data.model.User
import com.sleek.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
internal fun ChatListItem(
    chat:        Chat,
    peer:        User?,
    myId:        String?,
    unreadCount: Int     = 0,
    onClick:     () -> Unit,
) {
    val isLastMsgOwn = chat.lastMessage?.senderId == myId
    val hasUnread    = unreadCount > 0 && !isLastMsgOwn

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (peer?.avatarUrl != null) {
            AsyncImage(
                model              = peer.avatarUrl,
                contentDescription = peer.username,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.size(50.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape).background(AccentDim),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = (peer?.username ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium.copy(color = Accent, fontSize = 20.sp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text     = peer?.username ?: "Unknown",
                    style    = MaterialTheme.typography.titleMedium.copy(
                        color      = AppTheme.colors.textPrimary,
                        fontWeight = if (hasUnread) androidx.compose.ui.text.font.FontWeight.Bold
                                     else androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text     = chat.lastMessage?.createdAt?.let { formatChatTime(it) } ?: "",
                        style    = MaterialTheme.typography.labelSmall.copy(
                            color = if (hasUnread) Accent else AppTheme.colors.textMuted,
                        ),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    // ── Unread badge ──────────────────────────────────────────
                    if (hasUnread) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text  = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color    = TextPrimary,
                                    fontSize = 10.sp,
                                ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                if (isLastMsgOwn) Text("You: ", style = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textMuted))
                Text(
                    text     = when {
                        chat.lastMessage == null           -> "No messages yet"
                        chat.lastMessage.deletedAt != null -> "Message deleted"
                        else                               -> chat.lastMessage.content
                    },
                    style    = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color    = when {
                        hasUnread                            -> Accent       // purple for unread
                        chat.lastMessage?.deletedAt != null -> AppTheme.colors.textMuted
                        else                                 -> AppTheme.colors.textSecondary
                    },
                )
            }
        }
    }
}

@Composable
internal fun ChatItemSkeleton() {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(AppTheme.colors.surfaceHigh))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.4f).height(14.dp).background(AppTheme.colors.surfaceHigh, RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(0.7f).height(12.dp).background(AppTheme.colors.surfaceHigh, RoundedCornerShape(4.dp)))
        }
    }
}

internal fun formatChatTime(isoString: String): String = try {
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
