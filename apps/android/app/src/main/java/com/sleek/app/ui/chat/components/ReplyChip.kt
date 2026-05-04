package com.sleek.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sleek.app.data.model.ReplyTo
import com.sleek.app.ui.theme.*

@Composable
fun ReplyChip(
    replyTo:  ReplyTo,
    isOwn:    Boolean,
    onTap:    () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDeleted = replyTo.deletedAt != null

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isOwn) BubbleOwn.copy(alpha = 0.5f) else SurfaceHigh)
            .clickable(enabled = !isDeleted, onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(32.dp)
                .background(Accent, CircleShape)
        )

        // Avatar
        if (replyTo.sender.avatarUrl != null) {
            AsyncImage(
                model             = replyTo.sender.avatarUrl,
                contentDescription = null,
                contentScale      = ContentScale.Crop,
                modifier          = Modifier.size(20.dp).clip(CircleShape),
            )
        }

        Column {
            Text(
                text  = replyTo.sender.username ?: "Unknown",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            )
            Text(
                text     = if (isDeleted) "Message deleted" else replyTo.content,
                style    = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
