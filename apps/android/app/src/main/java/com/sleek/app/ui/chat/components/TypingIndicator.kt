package com.sleek.app.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.sleek.app.ui.theme.*

/**
 * Typing indicator — three bouncing dots in a bubble,
 * left-aligned exactly like an incoming message bubble.
 * No username text shown.
 */
@Composable
fun TypingIndicator(
    names:    List<String>,   // kept for API compat; content ignored
    modifier: Modifier = Modifier,
) {
    // Outer Row mirrors incoming MessageBubble exactly:
    // MessageBubble uses padding(start = 8.dp) + Arrangement.Start for other messages
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, bottom = 8.dp, end = 72.dp),
        verticalAlignment     = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Bubble — no avatar spacer, matches the message column directly
        Box(
            modifier = Modifier
                .clip(BubbleShapeOther)
                .background(BubbleOther)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                repeat(3) { index -> BouncingDot(delayMillis = index * 160) }
            }
        }
    }
}

@Composable
private fun BouncingDot(delayMillis: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation          = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode         = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMillis),
        ),
        label = "dot_scale",
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(TextSecondary),
    )
}
