package com.sleek.app.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sleek.app.data.model.Message
import com.sleek.app.ui.chat.components.MessageBubble
import com.sleek.app.ui.chat.components.TypingIndicator
import com.sleek.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Renders the message list area: loading skeleton, date-grouped LazyColumn,
 * floating date pill, controlled fling, and typing indicator.
 * Fully stateless — all data/callbacks flow in.
 *
 * P1: reverseLayout = true
 *   - Item 0 = newest message (rendered at BOTTOM)
 *   - Item N = oldest message (rendered at TOP)
 *   - "scroll to bottom" = scrollToItem(0)
 *   - Typing indicator at index 0 so it appears below the latest message
 *   - "Load older" auto-triggers when the list reaches the END (top visually)
 *   - Date separators placed AFTER their group's messages in item order,
 *     so they render ABOVE the group visually (because layout is reversed)
 */
@Composable
internal fun MessageList(
    grouped:              MessageGroups,
    myId:                 String?,
    peerHasReplied:       Boolean,
    seenUpToId:           String?,
    isLoading:            Boolean,
    listState:            LazyListState,
    highlightedMessageId: String?,
    typingUsers:          List<String>,
    hasMoreMessages:      Boolean,
    isLoadingOlder:       Boolean,
    onLoadOlder:          () -> Unit,
    onLongPress:          (Message) -> Unit,
    onReplyTap:           (String) -> Unit,
    onSwipeReply:         (Message) -> Unit,
) {
    if (isLoading) {
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
                            .background(
                                AppTheme.colors.surfaceHigh,
                                if (isOwn) BubbleShapeOwn else BubbleShapeOther,
                            ),
                    )
                }
            }
        }
        return
    }


    LaunchedEffect(listState, hasMoreMessages) {
        snapshotFlow {
            // Read totalItems INSIDE snapshotFlow so it updates reactively
            val total = grouped.groups.sumOf { 1 + it.rows.size } +
                if (typingUsers.isNotEmpty()) 1 else 0
            val last  = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Guard: only trigger when list has items AND we're near the end (top visually)
            total > 0 && last >= total - 5
        }.collect { nearEnd ->
            if (nearEnd && hasMoreMessages && !isLoadingOlder) {
                onLoadOlder()
            }
        }
    }

    // ── Floating date pill ─────────────────────────────────────────────────────
    // P1: with reverseLayout, firstVisibleItemIndex = 0 is the BOTTOM (newest).
    // The pill should still show the DATE of the topmost VISIBLE item.
    // With reverseLayout, "topmost visually" = LAST visible item by index.
    // We use lastVisibleItemIndex for the date label.
    val indexToDate = remember(grouped) {
        buildList {
            grouped.groups.forEach { group ->
                repeat(group.rows.size) { add(group.label) }  // message items
                add(group.label)                               // separator item (comes after)
            }
        }
    }
    val currentDate by remember(indexToDate, listState) {
        derivedStateOf {
            // With reverseLayout, topmost visible item has the HIGHEST index
            val topIdx = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: listState.firstVisibleItemIndex
            indexToDate.getOrNull(topIdx) ?: ""
        }
    }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    val pillAlpha = remember { Animatable(0f) }
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            pillAlpha.animateTo(1f, tween(120))
        } else {
            delay(1500)
            pillAlpha.animateTo(0f, tween(280))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            reverseLayout  = true,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // ── Typing indicator — index 0 = BOTTOM of reversed list ──────────
            // Appears just below the latest message, above the input bar.
            if (typingUsers.isNotEmpty()) {
                item(key = "typing", contentType = "typing") {
                    TypingIndicator(names = typingUsers)
                }
            }

            // ── Message groups (newest group first = index 0 = bottom) ────────
            grouped.groups.forEach { group ->
                // P1: messages first, separator last.
                // With reverseLayout this renders as:
                //   [separator (top of group)] [msg_oldest] ... [msg_newest (bottom of group)]

                // P4: Extended contentTypes — Compose's composition cache needs
                // distinct types to avoid thrashing. 5 types vs previous 3.
                itemsIndexed(
                    group.rows,
                    key         = { _, row -> row.message.id },
                    contentType = { _, row ->
                        when {
                            row.message.deletedAt != null              -> "msg_deleted"
                            row.message.replyTo   != null              -> "msg_reply"
                            row.message.pinned                         -> "msg_pinned"
                            row.message.edited                         -> "msg_edited"
                            else                                       -> "msg_text"
                        }
                    },
                ) { index, row ->
                    val msg        = row.message
                    val isOwn      = msg.senderId == myId
                    // P1: in reversed order, the "previous sender" is index+1 (above in data = below visually)
                    val prev       = if (index + 1 < group.rows.size) group.rows[index + 1].message else null
                    val showAvatar = prev == null || prev.senderId != msg.senderId
                    val isSeen     = isOwn && !peerHasReplied && msg.id == seenUpToId

                    MessageBubble(
                        message      = msg,
                        timeText     = row.timeText,
                        isOwn        = isOwn,
                        showAvatar   = showAvatar,
                        isSeen       = isSeen,
                        isHighlighted = msg.id == highlightedMessageId,
                        onLongPress  = { onLongPress(msg) },
                        onReplyTap   = { onReplyTap(it) },
                        onSwipeReply = { onSwipeReply(it) },
                    )
                }

                // Date separator — comes AFTER messages in item order.
                // With reverseLayout this renders ABOVE the group visually ✓
                item(key = "sep_${group.label}", contentType = "date_sep") {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .background(AppTheme.colors.surfaceMid, MaterialTheme.shapes.small)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text  = group.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AppTheme.colors.textSecondary,
                                ),
                            )
                        }
                    }
                }
            }

            // ── Load-older spinner — at the END of the reversed list (top visually) ──
            if (isLoadingOlder) {
                item(key = "load_older_spinner", contentType = "pagination") {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color       = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // ── Floating date pill ────────────────────────────────────────────────
        if (pillAlpha.value > 0f && currentDate.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .alpha(pillAlpha.value)
                    .background(
                        AppTheme.colors.surfaceMid.copy(alpha = 0.92f),
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(
                    text  = currentDate,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AppTheme.colors.textSecondary,
                    ),
                )
            }
        }
    }
}
