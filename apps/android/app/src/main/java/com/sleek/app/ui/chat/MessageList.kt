package com.sleek.app.ui.chat

import android.app.ActivityManager
import android.content.Context
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.sleek.app.data.model.Message
import com.sleek.app.ui.chat.components.MessageBubble
import com.sleek.app.ui.chat.components.TypingIndicator
import com.sleek.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Renders the message list area: loading skeleton, date-grouped LazyColumn,
 * floating date pill, rubber band overscroll, and typing indicator.
 * Fully stateless — all data/callbacks flow in.
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

    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    // ── Low-end device detection (disable rubber band on low-RAM devices) ─────
    val isLowRam = remember {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }

    // ── Rubber band overscroll ─────────────────────────────────────────────────
    val maxOverscrollPx = with(androidx.compose.ui.platform.LocalDensity.provides(
        androidx.compose.ui.platform.LocalDensity.current
    ).value) { 40.dp.toPx() }

    val overscrollOffset = remember { Animatable(0f) }
    val overscrollConnection = remember(isLowRam) {
        if (isLowRam) null
        else object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source:   NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    scope.launch {
                        val resistance = available.y * 0.28f
                        val clamped = (overscrollOffset.value + resistance)
                            .coerceIn(-maxOverscrollPx, maxOverscrollPx)
                        overscrollOffset.snapTo(clamped)
                    }
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                overscrollOffset.animateTo(
                    targetValue   = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioHighBouncy,
                        stiffness    = Spring.StiffnessMedium,
                    ),
                )
                return super.onPostFling(consumed, available)
            }
        }
    }

    // ── Floating date pill ─────────────────────────────────────────────────────
    // Build index → date label mapping once per grouped change
    val indexToDate = remember(grouped) {
        buildList {
            grouped.forEach { (label, msgs) ->
                add(label)              // separator item
                repeat(msgs.size) { add(label) }  // message items
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
    Box(
        modifier = Modifier.fillMaxSize().let {
            if (overscrollConnection != null) it.nestedScroll(overscrollConnection) else it
        },
    ) {
        LazyColumn(
            state                = listState,
            modifier             = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = overscrollOffset.value },
            contentPadding       = PaddingValues(vertical = 8.dp),
            beyondBoundsItemCount = 2,  // pre-render 2 items past viewport edges
        ) {
            grouped.forEach { (dateLabel, msgs) ->
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
                    msgs,
                    key         = { _, m -> m.id },
                    contentType = { _, _ -> "message" },
                ) { index, msg ->
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
                        onSwipeReply      = { onSwipeReply(it) },
                        modifier          = Modifier.animateItem(
                            fadeInSpec    = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness    = Spring.StiffnessMediumLow,
                            ),
                            fadeOutSpec   = tween(durationMillis = 100),
                        ),
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
