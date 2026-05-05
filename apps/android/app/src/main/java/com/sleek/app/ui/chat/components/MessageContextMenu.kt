package com.sleek.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sleek.app.data.model.Message
import com.sleek.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageContextMenu(
    message:    Message,
    isOwn:      Boolean,
    onDismiss:  () -> Unit,
    onCopy:     () -> Unit,
    onReply:    () -> Unit,
    onEdit:     (String) -> Unit,   // new content
    onPin:      () -> Unit,
    onUnpin:    () -> Unit,
    onDelete:   () -> Unit,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(message.content) }

    // ── Edit dialog ───────────────────────────────────────────────────────────
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor   = AppTheme.colors.surface,
            title            = { Text("Edit message", style = MaterialTheme.typography.titleMedium) },
            text             = {
                OutlinedTextField(
                    value         = editText,
                    onValueChange = { editText = it },
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Accent,
                        unfocusedBorderColor = AppTheme.colors.borderMid,
                        focusedTextColor     = AppTheme.colors.textPrimary,
                        unfocusedTextColor   = AppTheme.colors.textPrimary,
                        cursorColor          = Accent,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onEdit(editText.trim())
                    showEditDialog = false
                    onDismiss()
                }) {
                    Text("Save", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
        )
    }

    // ── Delete confirm dialog ─────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = AppTheme.colors.surface,
            title            = { Text("Delete message", style = MaterialTheme.typography.titleMedium) },
            text             = { Text("This message will be deleted for everyone.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton    = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                    onDismiss()
                }) {
                    Text("Delete", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = AppTheme.colors.textSecondary)
                }
            },
        )
    }

    // ── Bottom sheet ──────────────────────────────────────────────────────────
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = AppTheme.colors.surface,
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppTheme.colors.borderMid),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // ── Message preview ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(40.dp)
                        .background(Accent, RoundedCornerShape(2.dp))
                )
                Column {
                    Text(
                        text  = if (isOwn) "You" else (message.sender.username ?: ""),
                        style = MaterialTheme.typography.labelMedium.copy(color = Accent),
                    )
                    Text(
                        text     = message.content,
                        style    = MaterialTheme.typography.bodyMedium.copy(color = AppTheme.colors.textSecondary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(color = AppTheme.colors.borderSubtle, modifier = Modifier.padding(vertical = 8.dp))

            // ── Action rows ───────────────────────────────────────────────────
            ContextMenuItem(
                icon  = Icons.Default.ContentCopy,
                label = "Copy",
                tint  = AppTheme.colors.textPrimary,
                onClick = { onCopy(); onDismiss() },
            )
            ContextMenuItem(
                icon  = Icons.Default.Reply,
                label = "Reply",
                tint  = AppTheme.colors.textPrimary,
                onClick = { onReply(); onDismiss() },
            )
            if (isOwn) {
                ContextMenuItem(
                    icon  = Icons.Default.Edit,
                    label = "Edit",
                    tint  = AppTheme.colors.textPrimary,
                    onClick = { showEditDialog = true },
                )
            }
            ContextMenuItem(
                icon  = if (message.pinned) Icons.Default.PushPin else Icons.Default.PushPin,
                label = if (message.pinned) "Unpin" else "Pin",
                tint  = if (message.pinned) Accent else Color.White,
                onClick = {
                    if (message.pinned) onUnpin() else onPin()
                    onDismiss()
                },
            )
            if (isOwn) {
                ContextMenuItem(
                    icon  = Icons.Default.Delete,
                    label = "Delete",
                    tint  = ErrorRed,
                    onClick = { showDeleteDialog = true },
                )
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon:    ImageVector,
    label:   String,
    tint:    Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector       = icon,
            contentDescription = label,
            tint              = tint,
            modifier          = Modifier.size(22.dp),
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyLarge.copy(color = tint),
        )
    }
}
