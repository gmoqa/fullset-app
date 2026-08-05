package com.gmoqa.fullset.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
/**
 * El ámbar del badge *DIGITAL* como color de marca. Antes acá vivía `0xFF6750A4`, que es
 * literalmente el morado de ejemplo de la documentación de Material 3: no era una decisión, era la
 * plantilla sin tocar.
 *
 * No alcanza con cambiar `primary`: Material lo usa como **fondo** de los botones rellenos y pone
 * `onPrimary` encima. Con el morado ese contraste lo daba texto blanco; sobre amarillo el blanco no
 * se lee. Por eso va la familia completa —contenedores y "on" incluidos— y por eso el claro usa un
 * ámbar más oscuro: el amarillo brillante sobre blanco no tiene contraste suficiente para texto.
 */
object Palette {
    // Oscuro: el amarillo va al frente, con texto negro encima.
    val primaryDark = Color(0xFFFFC400)
    val onPrimaryDark = Color(0xFF3D2E00)
    val primaryContainerDark = Color(0xFF574500)
    val onPrimaryContainerDark = Color(0xFFFFE08A)
    val secondaryDark = Color(0xFFD7C68C)
    val onSecondaryDark = Color(0xFF3A3000)
    // El "seleccionado" de segmentados y del nav. Un amarillo oscurecido a secas (0xFF524600) daba
    // mostaza sucia: el amarillo pierde su carácter al bajarle luz y queda verdoso. Se desatura
    // hacia un marrón cálido, que convive con el ámbar sin competirle.
    val secondaryContainerDark = Color(0xFF453B26)
    val onSecondaryContainerDark = Color(0xFFFFE8AE)
    // Bordes de controles (segmentados, tarjetas de elección): ámbar apagado, para que la línea se
    // lea como del mismo material que el acento y no como un gris prestado.
    val outlineDark = Color(0xFF9C8A55)
    // Divisores: el MISMO color en Material alimenta bordes y separadores. Un ámbar visible en una
    // línea de 1dp que cruza toda la pantalla grita; acá va casi neutro, apenas cálido.
    val outlineVariantDark = Color(0xFF453F2F)

    // Claro: el mismo tono pero oscurecido, para que se lea sobre blanco.
    val primaryLight = Color(0xFF745B00)
    val onPrimaryLight = Color(0xFFFFFFFF)
    val primaryContainerLight = Color(0xFFFFE08A)
    val onPrimaryContainerLight = Color(0xFF241A00)
    val secondaryLight = Color(0xFF695D3F)
    val onSecondaryLight = Color(0xFFFFFFFF)
    val secondaryContainerLight = Color(0xFFF2E1BB)
    val onSecondaryContainerLight = Color(0xFF231B04)
    val outlineLight = Color(0xFF7D7440)
    val outlineVariantLight = Color(0xFFD8C9A4)
}

private val LightColors = lightColorScheme(
    primary = Palette.primaryLight,
    onPrimary = Palette.onPrimaryLight,
    primaryContainer = Palette.primaryContainerLight,
    onPrimaryContainer = Palette.onPrimaryContainerLight,
    secondary = Palette.secondaryLight,
    onSecondary = Palette.onSecondaryLight,
    secondaryContainer = Palette.secondaryContainerLight,
    onSecondaryContainer = Palette.onSecondaryContainerLight,
    outline = Palette.outlineLight,
    outlineVariant = Palette.outlineVariantLight,
)

private val DarkColors = darkColorScheme(
    primary = Palette.primaryDark,
    onPrimary = Palette.onPrimaryDark,
    primaryContainer = Palette.primaryContainerDark,
    onPrimaryContainer = Palette.onPrimaryContainerDark,
    secondary = Palette.secondaryDark,
    onSecondary = Palette.onSecondaryDark,
    secondaryContainer = Palette.secondaryContainerDark,
    onSecondaryContainer = Palette.onSecondaryContainerDark,
    outline = Palette.outlineDark,
    outlineVariant = Palette.outlineVariantDark,
)

/**
 * Las formas que usa **Material**: menús, tarjetas, diálogos, hojas y botones flotantes.
 *
 * Salen de [Tokens.Shape], la misma escala que usan nuestros componentes, para que no convivan dos
 * redondeos distintos. El que más se nota es `extraSmall`: Material lo tiene en 4dp y de ahí sacan
 * su forma los menús desplegables, que quedaban con esquinas visiblemente más filosas que el resto
 * de la interfaz.
 */
private val AppShapes = Shapes(
    extraSmall = Tokens.Shape.menu,
    small = Tokens.Shape.small,
    medium = Tokens.Shape.medium,
    large = Tokens.Shape.large,
    extraLarge = Tokens.Shape.dialog,
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
        shapes = AppShapes,
        content = content,
    )
}
