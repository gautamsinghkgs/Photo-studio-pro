package com.photostudio.pro.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF2F6FED)
private val BrandDark = Color(0xFF1B4FBF)
private val Accent = Color(0xFF00B894)

private val LightColors = lightColorScheme(
    primary = Brand, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF), onPrimaryContainer = BrandDark,
    secondary = Accent, onSecondary = Color.White,
    background = Color(0xFFFBFCFE), onBackground = Color(0xFF12161D),
    surface = Color.White, onSurface = Color(0xFF12161D),
    surfaceVariant = Color(0xFFEEF1F6), onSurfaceVariant = Color(0xFF5A6472),
    error = Color(0xFFC0392B),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FA9FF), onPrimary = Color(0xFF06122B),
    primaryContainer = Color(0xFF1B335E), onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF4FD8B6), onSecondary = Color(0xFF00251C),
    background = Color(0xFF0F131A), onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF161C26), onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF212A38), onSurfaceVariant = Color(0xFFA6B1C2),
)

@Composable
fun PhotoStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
