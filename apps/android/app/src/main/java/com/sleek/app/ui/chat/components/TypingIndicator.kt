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
    // Outer row mirrors incoming MessageBubble layout:
    //   12.dp start padding  +  avatar placeholder (40dp)  +  8.dp gap
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 2.dp, bottom = 8.dp, end = 72.dp),
        verticalAlignment     = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Avatar-width spacer so bubble sits in the same column as incoming text
        Spacer(Modifier.size(40.dp))
        Spacer(Modifier.width(8.dp))

        // Bubble
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
