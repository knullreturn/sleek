package com.sleek.app.ui.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sleek.app.data.model.User
import com.sleek.app.ui.theme.*
import androidx.compose.foundation.text.selection.TextSelectionColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewDmSheet(
    viewModel:  ChatListViewModel,
    onDismiss:  () -> Unit,
    onOpenChat: (chatId: String, chatName: String) -> Unit,
) {
    val dmQuery     by viewModel.dmQuery.collectAsStateWithLifecycle()
    val dmResults   by viewModel.dmResults.collectAsStateWithLifecycle()
    val dmSearching by viewModel.dmSearching.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = AppTheme.colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppTheme.colors.borderMid),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("New Message", style = MaterialTheme.typography.titleMedium)

            TextField(
                value         = dmQuery,
                onValueChange = { viewModel.setDmQuery(it) },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Search by username or #tag", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon   = {
                    if (dmSearching)
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Accent)
                    else
                        Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                },
                singleLine = true,
                colors     = TextFieldDefaults.colors(
                    focusedContainerColor      = AppTheme.colors.surfaceHigh,
                    unfocusedContainerColor    = AppTheme.colors.surfaceHigh,
                    focusedTextColor           = AppTheme.colors.textPrimary,
                    unfocusedTextColor         = AppTheme.colors.textPrimary,
                    cursorColor                = Accent,
                    focusedIndicatorColor      = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor    = androidx.compose.ui.graphics.Color.Transparent,
                    focusedPlaceholderColor    = AppTheme.colors.textMuted,
                    unfocusedPlaceholderColor  = AppTheme.colors.textMuted,
                    selectionColors            = TextSelectionColors(
                        handleColor     = Accent,
                        backgroundColor = Accent.copy(alpha = 0.25f),
                    ),
                ),
                shape = RoundedCornerShape(12.dp),
            )

            LazyColumn(
                modifier            = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (dmQuery.length >= 2 && dmResults.isEmpty() && !dmSearching) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No users found for \"$dmQuery\"",
                                style     = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                items(dmResults, key = { it.id }) { user ->
                    UserSearchItem(
                        user    = user,
                        onClick = { viewModel.startDm(user.id) { chatId, chatName -> onOpenChat(chatId, chatName) } },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun UserSearchItem(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (user.avatarUrl != null) {
            AsyncImage(
                model              = user.avatarUrl,
                contentDescription = user.username,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.size(40.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(AccentDim),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = (user.username ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(color = Accent),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = user.username ?: "Unknown",
                style = MaterialTheme.typography.titleMedium.copy(color = AppTheme.colors.textPrimary),
            )
            Text(
                text  = "#${user.tag}",
                style = MaterialTheme.typography.labelSmall.copy(color = AppTheme.colors.textSecondary),
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppTheme.colors.textSecondary)
    }
}
