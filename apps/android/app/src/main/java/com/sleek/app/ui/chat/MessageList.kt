package com.sleek.app.ui.chat


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleek.app.data.model.Message
import com.sleek.app.ui.chat.components.MessageBubble
import com.sleek.app.ui.chat.components.TypingIndicator
import com.sleek.app.ui.theme.*

/**
 * Renders the message list area: loading skeleton, date-grouped LazyColumn,
 * and the typing indicator row. Fully stateless — all data/callbacks flow in.
 */
@Composable
internal fun MessageList(
    grouped:              List<Pair<String, List<Message>>>,
    myId:                 String?,
    peerHasReplied:       Boolean,
    seenUpToId:           String?,
    isLoading:            Boolean,
    listState:            LazyListState,
    highlightedMessageId: String?,
    typingUsers:          List<String>,
    onLongPress:          (Message) -> Unit,
    onReplyTap:           (String) -> Unit,
) {
    if (isLoading) {
        // Skeleton (first-time only — Room already has data on subsequent opens)
        Column(
            modifier            = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(5) { i ->
                val isOwn = i % 2 == 0
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isOwn) 0.55f else 0.5f)
                            .height(42.dp)
                            .background(SurfaceHigh, if (isOwn) BubbleShapeOwn else BubbleShapeOther),
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        state          = listState,
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        grouped.forEach { (dateLabel, msgs) ->
            // Date separator
            item(key = "sep_$dateLabel") {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(vertical = 8.dp),
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

            // Messages
            itemsIndexed(msgs, key = { _, m -> m.id }) { index, msg ->
                val isOwn      = msg.senderId == myId
                val prev       = if (index > 0) msgs[index - 1] else null
                val showAvatar = prev == null || prev.senderId != msg.senderId
                val isSeen     = isOwn && !peerHasReplied && msg.id == seenUpToId

                MessageBubble(
                    message           = msg,
                    isOwn             = isOwn,
                    showAvatar        = showAvatar,
                    isSeen            = isSeen,
                    isHighlighted     = msg.id == highlightedMessageId,
                    onLongPress       = { onLongPress(msg) },
                    onReplyTap        = { onReplyTap(it) },
                )
            }
        }

        // Typing indicator
        if (typingUsers.isNotEmpty()) {
            item(key = "typing") { TypingIndicator(names = typingUsers) }
        }
    }
}
