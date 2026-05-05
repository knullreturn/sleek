package com.sleek.app.ui.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
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

// P2: Cache shapes at file level — remember() inside items still allocates per-item.
// These are stateless singletons used by ALL bubbles without reallocation.
private val shapeOwn   = BubbleShapeOwn
private val shapeOther = BubbleShapeOther

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
    val isDeleted = message.deletedAt != null
    val canPeek   = message.edited && message.originalContent != null

    var peekOriginal by remember { mutableStateOf(false) }
    val displayText  = if (peekOriginal && canPeek) message.originalContent!! else message.content

    // ── Highlight blink ───────────────────────────────────────────────────────
    val highlightAlpha = remember { Animatable(0f) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            repeat(2) {
                highlightAlpha.animateTo(0.28f, tween(180))
                highlightAlpha.animateTo(0f,    tween(300))
            }
        } else {
            highlightAlpha.snapTo(0f)
        }
    }

    // ── Swipe-to-reply ────────────────────────────────────────────────────────
    // P2 fix: was rememberDraggableState (allocates a coroutine per-item even when idle).
    // Now uses awaitHorizontalTouchSlopOrCancellation — ZERO cost until the user
    // actually starts dragging horizontally past slop. No idle coroutines.
    val density   = LocalDensity.current
    val haptic    = LocalHapticFeedback.current
    val threshold = with(density) { 72.dp.toPx() }
    val scope     = rememberCoroutineScope()

    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val iconProgress = (swipeOffset / threshold).coerceIn(0f, 1f)

    // P2 fix: Modifier.offset { } — evaluated on the draw phase, not composition.
    // graphicsLayer also does this but creates an offscreen layer; offset doesn't.
    // For a pure translation that has no alpha/scale, offset is strictly cheaper.
    // P6 fix: fillParentMaxWidth() instead of fillMaxWidth() — avoids an extra
    // measure pass inside the LazyColumn item scope.
    Box(
        modifier = modifier
            .fillParentMaxWidth()
            .pointerInput(message.id) {
                // Only one detector per bubble. No draggable wrapper, no extra modifier chain.
                // awaitEachGesture restarts for each new pointer sequence automatically.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // awaitHorizontalTouchSlopOrCancellation = FREE until user drags
                    // horizontally past ViewConfiguration.touchSlop. Until then, no
                    // work happens — taps and vertical scrolls pass through untouched.
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    }
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
                        // Spring-back after release
                        val from = swipeOffset
                        scope.launch {
                            Animatable(from).animateTo(
                                targetValue   = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness    = Spring.StiffnessMedium,
                                ),
                            ) { swipeOffset = value }
                        }
                    }
                }
            },
    ) {
        // ── Reply reveal icon ─────────────────────────────────────────────────
        if (iconProgress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(32.dp),
                // P2 fix: graphicsLayer removed from idle state — it creates an offscreen
                // layer even when scale=1/alpha=1. Instead gate the whole icon on iconProgress > 0.
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

        // ── Message row ───────────────────────────────────────────────────────
        // P2 fix: Modifier.offset { } instead of graphicsLayer { translationX }
        // offset runs on the draw thread with no layer allocation when value = 0.
        Row(
            modifier = Modifier
                .fillParentMaxWidth()
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
                // ── Bubble ────────────────────────────────────────────────────
                // P2 fix: Modifier.background(color, shape) instead of clip(shape).background(color).
                // clip() forces an offscreen render layer on every frame.
                // background(shape) draws the shape outline directly without a layer.
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isOwn) BubbleOwn else AppTheme.colors.bubbleOther,
                            shape = if (isOwn) shapeOwn  else shapeOther,
                        )
                        .pointerInput(message.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val up   = waitForUpOrCancellation()
                                if (up != null) {
                                    // tap — handled by gesture detector above
                                } else {
                                    // long press
                                    onLongPress(message)
                                }
                            }
                        },
                ) {
                    // Highlight overlay
                    if (highlightAlpha.value > 0f) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.White.copy(alpha = highlightAlpha.value))
                        )
                    }

                    Column {
                        // Reply preview
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

                        // Content area
                        Box(
                            modifier = Modifier.padding(
                                start  = 12.dp,
                                end    = 12.dp,
                                top    = if (message.replyTo != null) 2.dp else if (isDeleted) 10.dp else 8.dp,
                                bottom = if (isDeleted) 10.dp else 8.dp,
                            ),
                        ) {
                            // Pin badge
                            if (message.pinned) {
                                Icon(
                                    imageVector        = Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint               = if (isOwn) Color.White.copy(alpha = 0.6f) else AppTheme.colors.textSecondary,
                                    modifier           = Modifier
                                        .size(11.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-2).dp, y = 2.dp),
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
                                val textMuted = AppTheme.colors.textMuted
                                val timeColor = remember(isSeen, isOwn, textMuted) {
                                    when {
                                        isSeen -> SeenGreen
                                        isOwn  -> Color.White.copy(alpha = 0.45f)
                                        else   -> textMuted
                                    }
                                }

                                // P2 fix: was AnnotatedString with a transparent trailing spacer to
                                // push text above the timestamp — this forces text to re-layout every
                                // recomposition and can't be cached.
                                // Now: plain Text + a Box overlay for the timestamp row.
                                // The timestamp is a separate draw call that doesn't affect text layout.
                                Box {
                                    // Message text — padding-bottom reserves space for timestamp
                                    Text(
                                        text  = displayText,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = if (isOwn) Color.White else AppTheme.colors.textPrimary,
                                        ),
                                        modifier = Modifier.padding(bottom = 18.dp),
                                    )

                                    // Timestamp row — overlaid at bottom-end, no text layout impact
                                    Row(
                                        modifier              = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(start = 4.dp),
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
                                                        peekOriginal = true
                                                        waitForUpOrCancellation()
                                                        peekOriginal = false
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
                        }
                    }
                }
            }
        }
    }
}
