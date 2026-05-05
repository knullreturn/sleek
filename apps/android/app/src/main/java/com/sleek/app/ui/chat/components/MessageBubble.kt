package com.sleek.app.ui.chat.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sleek.app.data.model.Message
import com.sleek.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MessageBubble(
    message:       Message,
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

    // Press-and-hold to peek original on edited messages
    var peekOriginal by remember { mutableStateOf(false) }
    val canPeek      = message.edited && message.originalContent != null
    val displayText  = if (peekOriginal && canPeek) message.originalContent!! else message.content

    // ── Blink highlight (reply-tap) ───────────────────────────────────────────
    val highlightAlpha = remember { Animatable(0f) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            repeat(2) {
                highlightAlpha.animateTo(0.30f, tween(180))
                highlightAlpha.animateTo(0f,    tween(300))
            }
        } else {
            highlightAlpha.snapTo(0f)
        }
    }

    // ── Swipe-to-reply gesture ────────────────────────────────────────────────
    val density   = LocalDensity.current
    val haptic    = LocalHapticFeedback.current
    val scope     = rememberCoroutineScope()
    val threshold = with(density) { 72.dp.toPx() }
    val swipeOffset = remember(message.id) { Animatable(0f) }
    var triggered by remember(message.id) { mutableStateOf(false) }

    // Icon scales from 0 → 1 as swipe reaches threshold
    val iconProgress = (swipeOffset.value / threshold).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    // Only right swipes (positive delta), cap at 1.3× threshold
                    if (delta > 0 || swipeOffset.value > 0) {
                        scope.launch {
                            val next = (swipeOffset.value + delta).coerceIn(0f, threshold * 1.3f)
                            swipeOffset.snapTo(next)
                            if (next >= threshold && !triggered) {
                                triggered = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSwipeReply(message)
                            }
                        }
                    }
                },
                onDragStopped = {
                    scope.launch {
                        triggered = false
                        swipeOffset.animateTo(
                            targetValue   = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness    = Spring.StiffnessMedium,
                            ),
                        )
                    }
                },
            ),
    ) {
        // ── Reply icon — reveals behind the sliding bubble ────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .size(32.dp)
                .graphicsLayer {
                    scaleX = iconProgress
                    scaleY = iconProgress
                    alpha  = iconProgress
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Default.Reply,
                contentDescription = "Reply",
                tint               = Accent,
                modifier           = Modifier.size(22.dp),
            )
        }

        // ── Message row — slides right as user drags ──────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
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
                horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            ) {
                // ── Bubble ────────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .clip(if (isOwn) BubbleShapeOwn else BubbleShapeOther)
                        .background(if (isOwn) BubbleOwn else AppTheme.colors.bubbleOther)
                        .pointerInput(message.id) {
                            detectTapGestures(onLongPress = { onLongPress(message) })
                        },
                ) {
                    // ── Blink highlight overlay ───────────────────────────────
                    if (highlightAlpha.value > 0f) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.White.copy(alpha = highlightAlpha.value))
                        )
                    }
                    Column {
                        // ── Reply preview (inside bubble, WhatsApp-style) ─────
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

                        // ── Message content ───────────────────────────────────
                        Box(
                            modifier = Modifier.padding(
                                start    = 12.dp,
                                end      = 12.dp,
                                top      = if (message.replyTo != null) 2.dp else if (isDeleted) 10.dp else 8.dp,
                                bottom   = if (isDeleted) 10.dp else 8.dp,
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
                                val timeStr   = remember(message.createdAt) { formatBubbleTime(message.createdAt) }
                                val textMuted = AppTheme.colors.textMuted
                                val timeColor = remember(isSeen, isOwn, textMuted) {
                                    when {
                                        isSeen -> SeenGreen
                                        isOwn  -> Color.White.copy(alpha = 0.45f)
                                        else   -> textMuted
                                    }
                                }
                                val trailingSpacer = if (canPeek) "  edited  $timeStr" else "  $timeStr"

                                Box {
                                    Text(
                                        text  = buildAnnotatedString {
                                            append(displayText)
                                            withStyle(SpanStyle(color = Color.Transparent)) {
                                                append(trailingSpacer)
                                            }
                                        },
                                        style    = MaterialTheme.typography.bodyLarge.copy(
                                            color = if (isOwn) Color.White else AppTheme.colors.textPrimary,
                                        ),
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
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
                                                    detectTapGestures(
                                                        onPress = {
                                                            peekOriginal = true
                                                            tryAwaitRelease()
                                                            peekOriginal = false
                                                        }
                                                    )
                                                },
                                            )
                                        }
                                        Text(
                                            text  = timeStr,
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

fun formatBubbleTime(isoString: String): String = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    val date = sdf.parse(isoString) ?: return ""
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
} catch (_: Exception) { "" }
