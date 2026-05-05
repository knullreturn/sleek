package com.sleek.app.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import com.sleek.app.data.model.Message
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Immutable
class MessageGroups(
    val groups: List<MessageGroup> = emptyList(),
)

@Immutable
data class MessageGroup(
    val label: String,
    val rows:  List<MessageRow>,
)

@Immutable
data class MessageRow(
    val message:  Message,
    val timeText: String,
)

/**
 * Groups messages and precomputes row display text off the UI thread.
 *
 * P1 change: input is now in DESC order (newest first) because the DAO query
 * returns DESC for reverseLayout=true. Groups are also built in DESC order:
 *   groups[0] = today's messages, groups[1] = yesterday's messages, ...
 *
 * With reverseLayout=true, LazyColumn renders item(0) at the bottom, so:
 *   - groups[0] (today) renders at the bottom ✓
 *   - groups[last] (oldest day) renders at the top ✓
 *   - Date separator for each group is placed AFTER the group's messages in the
 *     item list, which means it renders ABOVE the group visually (reversed) ✓
 */
internal fun groupByDate(messages: List<Message>): MessageGroups {
    val result   = mutableListOf<Pair<String, MutableList<MessageRow>>>()
    val isoFmt   = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).also { it.timeZone = TimeZone.getTimeZone("UTC") }
    val dayFmt   = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    val keyFmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val timeFmt  = SimpleDateFormat("h:mm a", Locale.getDefault())
    val today    = keyFmt.format(Date())

    // messages arrive newest-first (DESC); iterate and group in that order
    for (msg in messages) {
        val date   = try { isoFmt.parse(msg.createdAt) } catch (_: Exception) { null } ?: continue
        val dayKey = keyFmt.format(date)
        val label  = if (dayKey == today) "Today" else dayFmt.format(date)
        val row    = MessageRow(message = msg, timeText = timeFmt.format(date))
        val last   = result.lastOrNull()
        if (last?.first == label) last.second.add(row)
        else result.add(label to mutableListOf(row))
    }
    return MessageGroups(result.map { (label, rows) -> MessageGroup(label, rows) })
}

/**
 * Finds the LazyColumn index for a given message id.
 *
 * P1: with reverseLayout, items are laid out in this order per group:
 *   [msg_0, msg_1, ..., msg_n, SEPARATOR]   ← separator is last in the group
 * Groups are in DESC order (newest first), so group 0 starts at index 0.
 */
internal fun findScrollIndex(grouped: MessageGroups, targetId: String): Int {
    var idx = 0
    for (group in grouped.groups) {
        // Messages come first, separator last (reversed rendering order)
        val pos = group.rows.indexOfFirst { it.message.id == targetId }
        if (pos >= 0) return idx + pos
        idx += group.rows.size + 1  // +1 for the separator item after messages
    }
    return -1
}

/** Number of LazyColumn items produced by message groups plus optional typing row. */
internal fun chatLazyItemCount(
    grouped: MessageGroups,
    hasTyping: Boolean = false,
): Int = grouped.groups.sumOf { group -> 1 + group.rows.size } + if (hasTyping) 1 else 0

/** Avoid animating across a long chat; Compose composes too much too quickly on weaker devices. */
internal suspend fun LazyListState.scrollToChatItem(
    targetIndex: Int,
    animateNearby: Boolean = true,
    nearbyThreshold: Int = 4,
) {
    val safeTarget = targetIndex.coerceAtLeast(0)
    val distance = abs(safeTarget - firstVisibleItemIndex)

    if (animateNearby && distance <= nearbyThreshold) {
        animateScrollToItem(safeTarget)
    } else {
        scrollToItem(safeTarget)
    }
}
