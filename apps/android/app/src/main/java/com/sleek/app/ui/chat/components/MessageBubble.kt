package com.sleek.app.ui.chat.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sleek.app.data.model.Message
import com.sleek.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message:       Message,
    isOwn:         Boolean,
    showAvatar:    Boolean,   // first in group — show avatar
    isSeen:        Boolean,
    onLongPress:   (Message) -> Unit,
    onReplyTap:    (String) -> Unit,   // scroll to reply origin
    modifier:      Modifier = Modifier,
) {
    val isDeleted = message.deletedAt != null

    // Press-and-hold to peek original on edited messages
    var peekOriginal by remember { mutableStateOf(false) }
    val canPeek      = message.edited && message.originalContent != null
    val displayText  = if (peekOriginal && canPeek) message.originalContent!! else message.content

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start  = if (isOwn) 64.dp else 8.dp,
                end    = if (isOwn) 8.dp  else 64.dp,
                top    = 1.dp,
                bottom = 1.dp,
            ),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom,
    ) {
        Column(
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
        ) {
            // Reply chip
            if (message.replyTo != null) {
                ReplyChip(
                    replyTo   = message.replyTo,
                    isOwn     = isOwn,
                    onTap     = { onReplyTap(message.replyTo.id) },
                    modifier  = Modifier.padding(bottom = 2.dp),
                )
            }

            // ── Bubble ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .clip(if (isOwn) BubbleShapeOwn else BubbleShapeOther)
                    .background(if (isOwn) BubbleOwn else BubbleOther)
                    .pointerInput(message.id) {
                        detectTapGestures(
                            onLongPress = { onLongPress(message) },
                            onPress     = {
                                if (canPeek) {
                                    tryAwaitRelease()
                                    // handled via pointerInput — peek via long press state
                                }
                            }
                        )
                    }
                    .padding(
                        horizontal = 12.dp,
                        vertical   = if (isDeleted) 10.dp else 8.dp,
                    ),
            ) {
                // Pin badge
                if (message.pinned) {
                    Icon(
                        imageVector       = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint              = if (isOwn) TextPrimary.copy(alpha = 0.6f) else TextSecondary,
                        modifier          = Modifier
                            .size(11.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 2.dp),
                    )
                }

                if (isDeleted) {
                    // Tombstone
                    Text(
                        text  = "🗑  This message was deleted",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color      = if (isOwn) TextPrimary.copy(alpha = 0.5f) else TextSecondary,
                            fontStyle  = FontStyle.Italic,
                            fontSize   = 13.sp,
                        ),
                    )
                } else {
                    // ── WhatsApp-style inline timestamp ─────────────────────
                    // The timestamp lives at BottomEnd of a Box;
                    // the text has an invisible trailing spacer that reserves
                    // exactly enough room so the time sits on the last line.
                    val timeStr   = formatBubbleTime(message.createdAt)
                    val timeColor by animateColorAsState(
                        targetValue   = if (isSeen) SeenGreen
                                        else if (isOwn) TextPrimary.copy(alpha = 0.45f)
                                        else TextMuted,
                        animationSpec = tween(500),
                        label         = "seen_color",
                    )
                    // Trailing spacer text = timestamp width (+ "edited " if applicable)
                    val trailingSpacer = if (canPeek) "  edited  $timeStr" else "  $timeStr"

                    Box {
                        // Message text with invisible trailing spacer
                        Text(
                            text  = buildAnnotatedString {
                                append(displayText)
                                withStyle(SpanStyle(color = Color.Transparent)) {
                                    append(trailingSpacer)
                                }
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        // Timestamp overlaid at bottom-right
                        Row(
                            modifier              = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(start = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            // Edited tag — hold to peek original
                            if (canPeek) {
                                Text(
                                    text  = if (peekOriginal) "original" else "edited",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color          = if (isOwn) TextPrimary.copy(alpha = 0.6f) else TextSecondary,
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

fun formatBubbleTime(isoString: String): String = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    val date = sdf.parse(isoString) ?: return ""
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
} catch (_: Exception) { "" }
