package com.sleek.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand ─────────────────────────────────────────────────────────────────────
val Accent       = Color(0xFF7C5CFC)   // SLEEK purple
val AccentLight  = Color(0xFF9B7FF8)
val AccentDim    = Color(0xFF7C5CFC).copy(alpha = 0.15f)

// ── Dark Theme Backgrounds (OLED-first) ───────────────────────────────────────
val Black        = Color(0xFF000000)
val Surface      = Color(0xFF0D0D0D)
val SurfaceHigh  = Color(0xFF1A1A1A)
val SurfaceMid   = Color(0xFF141414)

// ── Dark Theme Bubbles ────────────────────────────────────────────────────────
val BubbleOwn    = Color(0xFF7C5CFC)
val BubbleOther  = Color(0xFF1C1C1E)

// ── Dark Theme Text ───────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0x8CFFFFFF)
val TextMuted     = Color(0x47FFFFFF)

// ── Light Theme Backgrounds ───────────────────────────────────────────────────
val LightBackground  = Color(0xFFF8F8FA)
val LightSurface     = Color(0xFFFFFFFF)
val LightSurfaceHigh = Color(0xFFEEEEF4)
val LightSurfaceMid  = Color(0xFFF2F2F7)

// ── Light Theme Bubbles ───────────────────────────────────────────────────────
val LightBubbleOther = Color(0xFFECECF5)

// ── Light Theme Text ──────────────────────────────────────────────────────────
val LightTextPrimary   = Color(0xFF0F0F10)
val LightTextSecondary = Color(0xFF636370)
val LightTextMuted     = Color(0xFF9A9AB0)

// ── Status ────────────────────────────────────────────────────────────────────
val SeenGreen     = Color(0xFF4ADE80)
val ErrorRed      = Color(0xFFFF4444)
val OnlineGreen   = Color(0xFF22C55E)

// ── Borders ───────────────────────────────────────────────────────────────────
val BorderSubtle  = Color(0x1AFFFFFF)  // dark mode — 10% white
val BorderMid     = Color(0x33FFFFFF)  // dark mode — 20% white
val LightBorderSubtle = Color(0x1A000000)  // light mode — 10% black
val LightBorderMid    = Color(0x33000000)  // light mode — 20% black
