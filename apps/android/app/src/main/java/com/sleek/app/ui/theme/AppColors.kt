package com.sleek.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color set for SLEEK. Swap between dark/light instances in SleekTheme.
 */
data class AppColors(
    val background:    Color,
    val surface:       Color,
    val surfaceHigh:   Color,
    val surfaceMid:    Color,
    val bubbleOther:   Color,
    val textPrimary:   Color,
    val textSecondary: Color,
    val textMuted:     Color,
    val borderSubtle:  Color,
    val borderMid:     Color,
    val isDark:        Boolean,
)

val darkAppColors = AppColors(
    background    = Black,
    surface       = Surface,
    surfaceHigh   = SurfaceHigh,
    surfaceMid    = SurfaceMid,
    bubbleOther   = BubbleOther,
    textPrimary   = TextPrimary,
    textSecondary = TextSecondary,
    textMuted     = TextMuted,
    borderSubtle  = BorderSubtle,
    borderMid     = BorderMid,
    isDark        = true,
)

val lightAppColors = AppColors(
    background    = LightBackground,
    surface       = LightSurface,
    surfaceHigh   = LightSurfaceHigh,
    surfaceMid    = LightSurfaceMid,
    bubbleOther   = LightBubbleOther,
    textPrimary   = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted     = LightTextMuted,
    borderSubtle  = LightBorderSubtle,
    borderMid     = LightBorderMid,
    isDark        = false,
)

val LocalAppColors = staticCompositionLocalOf { darkAppColors }

/** Access current theme colors: `AppTheme.colors.background` */
object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
