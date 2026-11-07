package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CoolVitalsColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = VitalsTeal,
    onSecondary = Color.Black,
    tertiary = VitalsCoral,
    background = VitalsBgBottom,
    onBackground = VitalsTextPrimary,
    surface = VitalsCardOverlay,
    onSurface = VitalsTextPrimary,
    surfaceVariant = VitalsPillBg,
    onSurfaceVariant = VitalsTextMuted,
    error = VitalsCoral,
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CoolVitalsColorScheme,
        typography = Typography,
        content = content,
    )
}
