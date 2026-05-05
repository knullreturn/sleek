package com.sleek.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SleekDarkColorScheme = darkColorScheme(
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

private val SleekLightColorScheme = lightColorScheme(
    primary          = Accent,
    onPrimary        = Color.White,
    primaryContainer = AccentDim,
    secondary        = AccentLight,
    onSecondary      = LightTextPrimary,
    background       = LightBackground,
    onBackground     = LightTextPrimary,
    surface          = LightSurface,
    onSurface        = LightTextPrimary,
    surfaceVariant   = LightSurfaceHigh,
    onSurfaceVariant = LightTextSecondary,
    outline          = LightBorderSubtle,
    error            = ErrorRed,
    onError          = Color.White,
)

@Composable
fun SleekTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) SleekDarkColorScheme else SleekLightColorScheme,
        typography  = SleekTypography,
        shapes      = SleekShapes,
        content     = content,
    )
}
