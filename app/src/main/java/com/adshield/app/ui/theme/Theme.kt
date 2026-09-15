package com.adshield.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.adshield.app.data.SettingsStore

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D5BD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE4FF),
    onPrimaryContainer = Color(0xFF00174B),
    secondary = Color(0xFF00786B),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF11141C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF11141C),
    surfaceVariant = Color(0xFFE4E7F0),
    onSurfaceVariant = Color(0xFF444A58),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB6FF),
    onPrimary = Color(0xFF00297A),
    primaryContainer = Color(0xFF0B3FA8),
    onPrimaryContainer = Color(0xFFDBE4FF),
    secondary = Color(0xFF4DD0C4),
    background = Color(0xFF0A0E1A),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF121829),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF242B41),
    onSurfaceVariant = Color(0xFFB6BCCD),
    error = Color(0xFFF2B8B5)
)

@Composable
fun AdShieldTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        SettingsStore.THEME_LIGHT -> false
        SettingsStore.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
