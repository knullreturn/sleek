package com.sleek.app.ui.chat

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    onLongPress:          (Message) -> Unit,
    onReplyTap:           (String) -> Unit,
    onSwipeReply:         (Message) -> Unit,
) {
    if (isLoading) {
        // Skeleton — only shown on absolute first open (no Room cache yet)
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

    val context = LocalContext.current

    // ── Low-end device detection (gentler fling on low-RAM devices) ─────
    val isLowRam = remember {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }

    // ── Capped fling — chat should feel controlled on weaker devices ─────
    val density = LocalDensity.current
    val maxFlingVelocityPx = with(density) { if (isLowRam) 1800.dp.toPx() else 2600.dp.toPx() }
    val defaultFlingBehavior = ScrollableDefaults.flingBehavior()
    val cappedFlingBehavior = remember(defaultFlingBehavior, maxFlingVelocityPx) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val cappedVelocity = initialVelocity.coerceIn(
                    minimumValue = -maxFlingVelocityPx,
                    maximumValue = maxFlingVelocityPx,
                )
                return with(defaultFlingBehavior) { performFling(cappedVelocity) }
            }
        }
    }

    // ── Floating date pill ─────────────────────────────────────────────────────
    // Build index → date label mapping once per grouped change
    val indexToDate = remember(grouped) {
        buildList {
            grouped.groups.forEach { group ->
                val label = group.label
                add(label)              // separator item
                repeat(group.rows.size) { add(label) }  // message items
            }
        }
    }
    val currentDate by remember(indexToDate) {
        derivedStateOf { indexToDate.getOrNull(listState.firstVisibleItemIndex) ?: "" }
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

    // ── Layout ─────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            flingBehavior  = cappedFlingBehavior,
        ) {
            grouped.groups.forEach { group ->
                val dateLabel = group.label

                // Date separator
                item(key = "sep_$dateLabel", contentType = "date_sep") {
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
                                text  = dateLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AppTheme.colors.textSecondary,
                                ),
                            )
                        }
                    }
                }

                // Messages
                itemsIndexed(
                    group.rows,
                    key         = { _, row -> row.message.id },
                    contentType = { _, row ->
                        when {
                            row.message.deletedAt != null -> "message_deleted"
                            row.message.replyTo != null   -> "message_reply"
                            else                          -> "message_text"
                        }
                    },
                ) { index, row ->
                    val msg        = row.message
                    val isOwn      = msg.senderId == myId
                    val prev       = if (index > 0) group.rows[index - 1].message else null
                    val showAvatar = prev == null || prev.senderId != msg.senderId
                    val isSeen     = isOwn && !peerHasReplied && msg.id == seenUpToId

                    MessageBubble(
                        message           = msg,
                        timeText          = row.timeText,
                        isOwn             = isOwn,
                        showAvatar        = showAvatar,
                        isSeen            = isSeen,
                        isHighlighted     = msg.id == highlightedMessageId,
                        onLongPress       = { onLongPress(msg) },
                        onReplyTap        = { onReplyTap(it) },
                        onSwipeReply      = { onSwipeReply(it) },
                    )
                }
            }

            // Typing indicator
            if (typingUsers.isNotEmpty()) {
                item(key = "typing", contentType = "typing") {
                    TypingIndicator(names = typingUsers)
                }
            }
        }

        // ── Floating date pill (overlay, top-center) ──────────────────────────
        if (pillAlpha.value > 0f && currentDate.isNotEmpty()) {
            Box(
                modifier         = Modifier
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
