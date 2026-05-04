package com.sleek.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleek.app.data.model.ReplyTo
import com.sleek.app.ui.theme.*

/**
 * Shown INSIDE the message bubble (WhatsApp-style).
 * Accent left border + sender name + content preview.
 */
@Composable
fun ReplyChip(
    replyTo:  ReplyTo,
    isOwn:    Boolean,
    onTap:    () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDeleted = replyTo.deletedAt != null

    // Subtle tinted background — contrasts with the bubble without clashing
    val bgColor = if (isOwn)
        Color.White.copy(alpha = 0.13f)
    else
        Color.Black.copy(alpha = 0.12f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(enabled = !isDeleted, onClick = onTap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Accent left border ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(Accent)
        )

        // ── Content ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .widthIn(max = 220.dp),
        ) {
            Text(
                text  = replyTo.sender.username ?: "Unknown",
                style = MaterialTheme.typography.labelMedium.copy(
                    color      = Accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 11.sp,
                ),
                maxLines = 1,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text     = if (isDeleted) "Message deleted" else replyTo.content,
                style    = MaterialTheme.typography.bodySmall.copy(
                    color    = if (isOwn) TextPrimary.copy(alpha = 0.7f) else TextSecondary,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
