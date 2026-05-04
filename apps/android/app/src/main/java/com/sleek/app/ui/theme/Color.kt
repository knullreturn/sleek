package com.sleek.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand ─────────────────────────────────────────────────────────────────────
val Accent       = Color(0xFF7C5CFC)   // SLEEK purple
val AccentLight  = Color(0xFF9B7FF8)
val AccentDim    = Color(0xFF7C5CFC).copy(alpha = 0.15f)

// ── Backgrounds (OLED-first) ──────────────────────────────────────────────────
val Black        = Color(0xFF000000)   // canvas background
val Surface      = Color(0xFF0D0D0D)   // card / panel
val SurfaceHigh  = Color(0xFF1A1A1A)   // other bubble, input bg
val SurfaceMid   = Color(0xFF141414)

// ── Bubbles ───────────────────────────────────────────────────────────────────
val BubbleOwn    = Color(0xFF7C5CFC)   // own message bubble
val BubbleOther  = Color(0xFF1C1C1E)   // friend's message bubble

// ── Text ──────────────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0x8CFFFFFF)  // 55% white
val TextMuted     = Color(0x47FFFFFF)  // 28% white — timestamps

// ── Status ────────────────────────────────────────────────────────────────────
val SeenGreen     = Color(0xFF4ADE80)
val ErrorRed      = Color(0xFFFF4444)
val OnlineGreen   = Color(0xFF22C55E)

// ── Borders ───────────────────────────────────────────────────────────────────
val BorderSubtle  = Color(0x1AFFFFFF)  // 10% white
val BorderMid     = Color(0x33FFFFFF)  // 20% white
