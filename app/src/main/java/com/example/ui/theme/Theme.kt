package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VuDarkColorScheme = darkColorScheme(
    primary = VuInfoCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1B2A4A),
    onPrimaryContainer = Color(0xFFD6E3FF),

    secondary = VuSuccessMint,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0F3E32),
    onSecondaryContainer = Color(0xFFA3F3DC),

    tertiary = VuWarningAmber,
    onTertiary = Color(0xFF2E1A00),

    background = DarkPageBackground,
    onBackground = DarkTextMain,
    surface = DarkCardSurface,
    onSurface = DarkTextMain,
    surfaceVariant = Color(0xFF252837),
    onSurfaceVariant = DarkTextMuted,
    outline = DarkBorder,
    error = VuDangerRed,
    onError = Color.White
)

private val VuLightColorScheme = lightColorScheme(
    primary = VuInfoCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1F0FF),
    onPrimaryContainer = Color(0xFF003258),

    secondary = VuGreenPrimary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F4E5),
    onSecondaryContainer = Color(0xFF00391E),

    tertiary = VuWarningAmber,
    onTertiary = Color.White,

    background = VuPageBackground,
    onBackground = VuTextMain,
    surface = VuSurfaceWhite,
    onSurface = VuTextMain,
    surfaceVariant = Color(0xFFF7F8FB),
    onSurfaceVariant = VuTextMuted,
    outline = VuBorderLight,
    error = VuDangerRed,
    onError = Color.White
)

@Composable
fun VuLmsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VuDarkColorScheme else VuLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backward-compat aliases
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    VuLmsTheme(darkTheme = darkTheme, content = content)
}
