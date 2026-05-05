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

/** Groups messages and precomputes row display text off the UI thread. */
internal fun groupByDate(messages: List<Message>): MessageGroups {
    val result   = mutableListOf<Pair<String, MutableList<MessageRow>>>()
    val isoFmt   = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).also { it.timeZone = TimeZone.getTimeZone("UTC") }
    val dayFmt   = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    val keyFmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val timeFmt  = SimpleDateFormat("h:mm a", Locale.getDefault())
    val today    = keyFmt.format(Date())

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

/** Finds the LazyColumn index (accounting for date-separator items) for a given message id. */
internal fun findScrollIndex(grouped: MessageGroups, targetId: String): Int {
    var idx = 0
    for (group in grouped.groups) {
        idx++ // separator item
        val pos = group.rows.indexOfFirst { it.message.id == targetId }
        if (pos >= 0) return idx + pos
        idx += group.rows.size
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
