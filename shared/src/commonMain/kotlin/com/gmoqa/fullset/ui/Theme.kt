package com.gmoqa.fullset.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * **La paleta de la app.** Cambiando estos cuatro valores cambia el color de toda la interfaz:
 * botones, chips seleccionados, acentos y la barra de estado.
 *
 * El resto del `ColorScheme` (fondos, superficies, texto) lo deriva Material 3 solo, así que no hace
 * falta declararlo — y conviene no hacerlo, porque Material ya garantiza el contraste entre sus
 * roles. Lo que **no** vive acá: los colores de cada consola (`PlatformLogos.kt`, son datos de la
 * plataforma) y las capas sobre carátula ([Tokens.Overlay], que van sobre una imagen y por eso no
 * dependen del tema).
 */
object Palette {
    val primaryLight = Color(0xFF6750A4)
    val secondaryLight = Color(0xFF625B71)
    val primaryDark = Color(0xFFD0BCFF)
    val secondaryDark = Color(0xFFCCC2DC)
}

private val LightColors = lightColorScheme(
    primary = Palette.primaryLight,
    secondary = Palette.secondaryLight,
)

private val DarkColors = darkColorScheme(
    primary = Palette.primaryDark,
    secondary = Palette.secondaryDark,
)

/**
 * Pinta la barra de estado del sistema según el tema. En Android ajusta color e íconos del status
 * bar (efecto de plataforma); en iOS no hace nada (la status bar la maneja UIKit).
 */
@Composable
expect fun SystemBarsEffect(statusBarColor: Color, darkTheme: Boolean)

/**
 * Tema de la app. Los colores salen de [Palette]; el espaciado, las formas, las capas sobre carátula
 * y los tamaños recurrentes, de [Tokens]. Entre esos dos está todo el estilo de fullset.
 */
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
