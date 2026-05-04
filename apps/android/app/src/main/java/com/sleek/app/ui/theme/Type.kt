package com.sleek.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using system SansSerif until Inter font files are added to res/font/
// To add Inter: download from fonts.google.com/specimen/Inter and place
//   inter_regular.ttf, inter_medium.ttf, inter_semibold.ttf, inter_bold.ttf
//   in app/src/main/res/font/ then switch FontFamily.SansSerif → InterFamily
val AppFont = FontFamily.SansSerif

val SleekTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily  = AppFont,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 20.sp,
        lineHeight  = 28.sp,
        color       = TextPrimary,
    ),
    titleMedium = TextStyle(
        fontFamily  = AppFont,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 15.sp,
        lineHeight  = 20.sp,
        color       = TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontFamily  = AppFont,
        fontWeight  = FontWeight.Normal,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        color       = TextSecondary,
    ),
    bodyLarge = TextStyle(
        fontFamily  = AppFont,
        fontWeight  = FontWeight.Normal,
        fontSize    = 15.sp,
        lineHeight  = 22.sp,
        color       = TextPrimary,
    ),
    labelSmall = TextStyle(
        fontFamily  = AppFont,
        fontWeight  = FontWeight.Normal,
        fontSize    = 11.sp,
        lineHeight  = 14.sp,
        color       = TextMuted,
    ),
    labelMedium = TextStyle(
        fontFamily  = AppFont,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        color       = AccentLight,
    ),
)
