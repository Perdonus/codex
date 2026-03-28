package com.codex.android.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6FD3C1),
    onPrimary = Color(0xFF05231E),
    secondary = Color(0xFFFFB37A),
    background = Color(0xFF0C141B),
    onBackground = Color(0xFFF8F4EC),
    surface = Color(0xFF111D25),
    onSurface = Color(0xFFF8F4EC),
    surfaceContainer = Color(0xFF162630),
    surfaceContainerHigh = Color(0xFF1A2E39),
    surfaceContainerHighest = Color(0xFF233847),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF197A73),
    onPrimary = Color(0xFFF8F4EC),
    secondary = Color(0xFFC96B39),
    background = Color(0xFFF6EFE4),
    onBackground = Color(0xFF171313),
    surface = Color(0xFFFFFBF6),
    onSurface = Color(0xFF171313),
    surfaceContainer = Color(0xFFF0E6DA),
    surfaceContainerHigh = Color(0xFFE8DDD0),
    surfaceContainerHighest = Color(0xFFDFCFC0),
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
