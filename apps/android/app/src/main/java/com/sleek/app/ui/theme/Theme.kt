package com.sleek.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SleekColorScheme = darkColorScheme(
    primary          = Accent,
    onPrimary        = TextPrimary,
    primaryContainer = AccentDim,
    secondary        = AccentLight,
    onSecondary      = TextPrimary,
    background       = Black,
    onBackground     = TextPrimary,
    surface          = Surface,
    onSurface        = TextPrimary,
    surfaceVariant   = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline          = BorderSubtle,
    error            = ErrorRed,
    onError          = TextPrimary,
)

@Composable
fun SleekTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SleekColorScheme,
        typography  = SleekTypography,
        shapes      = SleekShapes,
        content     = content,
    )
}
