package com.codex.android.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF66C7F4),
    onPrimary = Color(0xFF06263A),
    secondary = Color(0xFFF3A45D),
    background = Color(0xFF09131C),
    onBackground = Color(0xFFF4F7FB),
    surface = Color(0xFF10202E),
    onSurface = Color(0xFFF4F7FB),
    surfaceContainer = Color(0xFF132738),
    surfaceContainerHigh = Color(0xFF183042),
    surfaceContainerHighest = Color(0xFF1D394D),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0D81B8),
    onPrimary = Color.White,
    secondary = Color(0xFFBE6A28),
    background = Color(0xFFF3F7FB),
    onBackground = Color(0xFF08141F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF08141F),
    surfaceContainer = Color(0xFFEAF1F7),
    surfaceContainerHigh = Color(0xFFE3EDF5),
    surfaceContainerHighest = Color(0xFFD9E7F1),
)

@Composable
fun CodexAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}

