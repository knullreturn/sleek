package com.sleek.app.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import com.sleek.app.data.model.Message
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/** Groups a flat message list into [(dateLabel, messages)] for the LazyColumn. */
internal fun groupByDate(messages: List<Message>): List<Pair<String, List<Message>>> {
    val result   = mutableListOf<Pair<String, MutableList<Message>>>()
    val isoFmt   = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).also { it.timeZone = TimeZone.getTimeZone("UTC") }
    val dayFmt   = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    val keyFmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today    = keyFmt.format(Date())

    for (msg in messages) {
        val date   = try { isoFmt.parse(msg.createdAt) } catch (_: Exception) { null } ?: continue
        val dayKey = keyFmt.format(date)
        val label  = if (dayKey == today) "Today" else dayFmt.format(date)
        val last   = result.lastOrNull()
        if (last?.first == label) last.second.add(msg)
        else result.add(label to mutableListOf(msg))
    }
    return result
}

/** Finds the LazyColumn index (accounting for date-separator items) for a given message id. */
internal fun findScrollIndex(grouped: List<Pair<String, List<Message>>>, targetId: String): Int {
    var idx = 0
    for ((_, msgs) in grouped) {
        idx++ // separator item
        val pos = msgs.indexOfFirst { it.id == targetId }
        if (pos >= 0) return idx + pos
        idx += msgs.size
    }
    return -1
}

/** Number of LazyColumn items produced by message groups plus optional typing row. */
internal fun chatLazyItemCount(
    grouped: List<Pair<String, List<Message>>>,
    hasTyping: Boolean = false,
): Int = grouped.sumOf { (_, msgs) -> 1 + msgs.size } + if (hasTyping) 1 else 0

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
