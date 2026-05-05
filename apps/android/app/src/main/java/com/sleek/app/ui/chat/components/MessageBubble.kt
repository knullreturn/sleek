package com.sleek.app.ui.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleek.app.data.model.Message
import com.sleek.app.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// File-level shape cache — allocated once, shared across all bubbles.
private val shapeOwn   = BubbleShapeOwn
private val shapeOther = BubbleShapeOther

// ── Public entry point ────────────────────────────────────────────────────────
@Composable
fun MessageBubble(
    message:       Message,
    timeText:      String,
    isOwn:         Boolean,
    showAvatar:    Boolean,
    isSeen:        Boolean,
    isHighlighted: Boolean = false,
    onLongPress:   (Message) -> Unit,
    onReplyTap:    (String) -> Unit,
    onSwipeReply:  (Message) -> Unit,
    modifier:      Modifier = Modifier,
) {
    // Swipe state lives here so it doesn't rebuild the inner composables
    val density   = LocalDensity.current
    val haptic    = LocalHapticFeedback.current
    val threshold = with(density) { 72.dp.toPx() }
    val scope     = rememberCoroutineScope()
    var swipeOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                    if (drag != null) {
                        var triggered = false
                        horizontalDrag(drag.id) { change ->
                            val delta = change.positionChange().x
                            if (delta > 0 || swipeOffset > 0) {
                                swipeOffset = (swipeOffset + delta).coerceIn(0f, threshold * 1.3f)
                                if (swipeOffset >= threshold && !triggered) {
                                    triggered = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSwipeReply(message)
                                }
                            }
                            change.consume()
                        }
                        val from = swipeOffset
                        scope.launch {
                            Animatable(from).animateTo(
                                0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                            ) { swipeOffset = value }
                        }
                    }
                }
            },
    ) {
        // Reply icon — only composed when visible
        val iconProgress = (swipeOffset / threshold).coerceIn(0f, 1f)
        if (iconProgress > 0f) {
            Box(
                modifier         = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Default.Reply,
                    contentDescription = "Reply",
                    tint               = Accent.copy(alpha = iconProgress),
                    modifier           = Modifier.size(22.dp * iconProgress),
                )
            }
        }

        // Inner bubble — extracted to its own composable to reduce register count
        BubbleRow(
            message       = message,
            timeText      = timeText,
            isOwn         = isOwn,
            showAvatar    = showAvatar,
            isSeen        = isSeen,
            isHighlighted = isHighlighted,
            swipeOffset   = swipeOffset,
            onLongPress   = onLongPress,
            onReplyTap    = onReplyTap,
        )
    }
}

// ── Row + bubble shell ────────────────────────────────────────────────────────
// Split out so MessageBubble's DEX method stays under the register limit.
@Composable
private fun BubbleRow(
    message:       Message,
    timeText:      String,
    isOwn:         Boolean,
    showAvatar:    Boolean,
    isSeen:        Boolean,
    isHighlighted: Boolean,
    swipeOffset:   Float,
    onLongPress:   (Message) -> Unit,
    onReplyTap:    (String) -> Unit,
) {
    val isDeleted = message.deletedAt != null

    val highlightAlpha = remember { Animatable(0f) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            repeat(2) {
                highlightAlpha.animateTo(0.28f, tween(180))
                highlightAlpha.animateTo(0f, tween(300))
            }
        } else {
            highlightAlpha.snapTo(0f)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(swipeOffset.roundToInt(), 0) }
            .padding(
                start  = if (isOwn) 64.dp else 8.dp,
                end    = if (isOwn) 8.dp  else 64.dp,
                top    = if (showAvatar) 8.dp else 2.dp,
                bottom = 1.dp,
            ),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom,
    ) {
        Column(
            modifier            = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = if (isOwn) BubbleOwn else AppTheme.colors.bubbleOther,
                        shape = if (isOwn) shapeOwn  else shapeOther,
                    )
                    .pointerInput(message.id) {
                        detectTapGestures(onLongPress = { onLongPress(message) })
                    },
            ) {
                if (highlightAlpha.value > 0f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.White.copy(alpha = highlightAlpha.value))
                    )
                }

                // Content extracted to keep this method's register count low
                BubbleContent(
                    message    = message,
                    timeText   = timeText,
                    isOwn      = isOwn,
                    isSeen     = isSeen,
                    isDeleted  = isDeleted,
                    onReplyTap = onReplyTap,
                )
            }
        }
    }
}

// ── Bubble interior ───────────────────────────────────────────────────────────
@Composable
private fun BubbleContent(
    message:    Message,
    timeText:   String,
    isOwn:      Boolean,
    isSeen:     Boolean,
    isDeleted:  Boolean,
    onReplyTap: (String) -> Unit,
) {
    val canPeek = message.edited && message.originalContent != null
    var peekOriginal by remember { mutableStateOf(false) }
    val displayText  = if (peekOriginal && canPeek) message.originalContent!! else message.content

    Column {
        if (message.replyTo != null) {
            ReplyChip(
                replyTo  = message.replyTo,
                isOwn    = isOwn,
                onTap    = { onReplyTap(message.replyTo.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 6.dp, end = 6.dp, bottom = 0.dp),
            )
            Spacer(Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier.padding(
                start  = 12.dp,
                end    = 12.dp,
                top    = if (message.replyTo != null) 2.dp else if (isDeleted) 10.dp else 8.dp,
                bottom = if (isDeleted) 10.dp else 8.dp,
            ),
        ) {
            if (message.pinned) {
                Icon(
                    imageVector        = Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint               = if (isOwn) Color.White.copy(alpha = 0.6f) else AppTheme.colors.textSecondary,
                    modifier           = Modifier.size(11.dp).align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp),
                )
            }

            if (isDeleted) {
                Text(
                    text  = "This message was deleted",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color     = if (isOwn) Color.White.copy(alpha = 0.5f) else AppTheme.colors.textSecondary,
                        fontStyle = FontStyle.Italic,
                        fontSize  = 13.sp,
                    ),
                )
            } else {
                // Extracted further to keep BubbleContent under register limit
                MessageTextWithTimestamp(
                    text         = displayText,
                    timeText     = timeText,
                    isOwn        = isOwn,
                    isSeen       = isSeen,
                    canPeek      = canPeek,
                    peekOriginal = peekOriginal,
                    onPeekChange = { peekOriginal = it },
                )
            }
        }
    }
}

// ── Text + timestamp overlay ──────────────────────────────────────────────────
@Composable
private fun MessageTextWithTimestamp(
    text:         String,
    timeText:     String,
    isOwn:        Boolean,
    isSeen:       Boolean,
    canPeek:      Boolean,
    peekOriginal: Boolean,
    onPeekChange: (Boolean) -> Unit,
) {
    val textMuted = AppTheme.colors.textMuted
    val timeColor = remember(isSeen, isOwn, textMuted) {
        when {
            isSeen -> SeenGreen
            isOwn  -> Color.White.copy(alpha = 0.45f)
            else   -> textMuted
        }
    }

    Box {
        Text(
            text     = text,
            style    = MaterialTheme.typography.bodyLarge.copy(
                color = if (isOwn) Color.White else AppTheme.colors.textPrimary,
            ),
            modifier = Modifier.padding(bottom = 18.dp),
        )

        Row(
            modifier              = Modifier.align(Alignment.BottomEnd).padding(start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            if (canPeek) {
                Text(
                    text  = if (peekOriginal) "original" else "edited",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color          = if (isOwn) Color.White.copy(alpha = 0.6f) else AppTheme.colors.textSecondary,
                        fontStyle      = FontStyle.Italic,
                        textDecoration = TextDecoration.Underline,
                    ),
                    modifier = Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            onPeekChange(true)
                            do {
                                val evt = awaitPointerEvent()
                            } while (evt.changes.any { it.pressed })
                            onPeekChange(false)
                        }
                    },
                )
            }
            Text(
                text  = timeText,
                style = MaterialTheme.typography.labelSmall.copy(color = timeColor),
            )
        }
    }
}
