package com.gmoqa.diariogamer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    secondary = Color(0xFF625B71),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
)

/**
 * Pinta la barra de estado del sistema según el tema. En Android ajusta color e íconos del status
 * bar (efecto de plataforma); en iOS no hace nada (la status bar la maneja UIKit).
 */
@Composable
expect fun SystemBarsEffect(statusBarColor: Color, darkTheme: Boolean)

@Composable
fun DiarioGamerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    SystemBarsEffect(statusBarColor = colors.primary, darkTheme = darkTheme)
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
