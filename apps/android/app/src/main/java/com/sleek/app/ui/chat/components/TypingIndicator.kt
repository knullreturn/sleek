package com.sleek.app.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.sleek.app.ui.theme.*

@Composable
fun TypingIndicator(
    names:    List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(start = 48.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Animated dots
        Box(
            modifier = Modifier
                .clip(BubbleShapeOther)
                .background(BubbleOther)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    BouncingDot(delayMillis = index * 160)
                }
            }
        }

        if (names.isNotEmpty()) {
            Text(
                text  = if (names.size == 1) "${names[0]} is typing…"
                        else "${names.joinToString(", ")} are typing…",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun BouncingDot(delayMillis: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMillis),
        ),
        label = "dot_scale",
    )

    Box(
        modifier = Modifier
            .size(6.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(TextSecondary),
    )
}
