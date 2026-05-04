package com.sleek.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val SleekShapes = Shapes(
    // Small: chips, tags, badges
    small  = RoundedCornerShape(6.dp),
    // Medium: input fields, cards
    medium = RoundedCornerShape(12.dp),
    // Large: bottom sheets, panels
    large  = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
)

// Message bubble shapes — asymmetric corners like Telegram
val BubbleShapeOwn   = RoundedCornerShape(
    topStart    = 18.dp,
    topEnd      = 18.dp,
    bottomStart = 18.dp,
    bottomEnd   = 4.dp,   // tail side
)
val BubbleShapeOther = RoundedCornerShape(
    topStart    = 18.dp,
    topEnd      = 18.dp,
    bottomStart = 4.dp,   // tail side
    bottomEnd   = 18.dp,
)
val BubbleShapeFirst = RoundedCornerShape(18.dp)  // standalone/first message
