package com.sleek.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sleek.app.data.model.Message
import com.sleek.app.ui.theme.*

/**
 * Reply preview bar + message text field + animated send button.
 * All compose state (inputValue, replyingTo) is owned by ChatScreen
 * and passed in via lambdas so this composable is fully stateless.
 */
@Composable
internal fun ChatInputBar(
    replyingTo:    Message?,
    onCancelReply: () -> Unit,
    inputValue:    TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend:        () -> Unit,
) {
    val focusReq = remember { FocusRequester() }

    Column {
        // ── Animated reply preview ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = replyingTo != null,
            enter   = slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
            exit    = slideOutVertically(tween(160)) { it } + fadeOut(tween(160)),
        ) {
            replyingTo?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppTheme.colors.surface)
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Reply, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppTheme.colors.surfaceHigh),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.width(3.dp).height(44.dp).background(Accent))
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text  = msg.sender.username ?: "Unknown",
                                style = MaterialTheme.typography.labelMedium.copy(color = Accent, fontWeight = FontWeight.SemiBold),
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text     = msg.content,
                                style    = MaterialTheme.typography.bodySmall.copy(color = AppTheme.colors.textSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    IconButton(onClick = onCancelReply) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply", tint = AppTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // ── Input row ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            TextField(
                value         = inputValue,
                onValueChange = onValueChange,
                modifier      = Modifier.weight(1f).focusRequester(focusReq),
                placeholder   = { Text("Message…", style = MaterialTheme.typography.bodyMedium) },
                trailingIcon  = {
                    AnimatedVisibility(
                        visible = inputValue.text.isNotBlank(),
                        enter   = scaleIn(tween(150)) + fadeIn(tween(150)),
                        exit    = scaleOut(tween(100)) + fadeOut(tween(100)),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Accent)
                                .clickable(onClick = onSend),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = AppTheme.colors.textPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = false,
                maxLines   = 5,
                colors     = TextFieldDefaults.colors(
                    focusedContainerColor   = AppTheme.colors.surfaceHigh,
                    unfocusedContainerColor = AppTheme.colors.surfaceHigh,
                    focusedTextColor        = TextPrimary,
                    unfocusedTextColor      = TextPrimary,
                    cursorColor             = Accent,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(28.dp),
            )
        }
    }
}

