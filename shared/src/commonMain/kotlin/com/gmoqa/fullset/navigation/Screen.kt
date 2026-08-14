package com.gmoqa.fullset.navigation

/** A qué se entra desde el flujo de "agregar": cambia el destino del juego elegido. */
internal enum class AddTarget { LIBRARY, PLAYING, WISHLIST }

/**
 * Pantalla principal actual dentro de la navegación.
 *
 * Vive acá y no dentro de `App.kt` porque es el **vocabulario** de la navegación: se usa desde la
 * primera línea del router y estaba declarado 200 líneas más abajo, en medio del archivo.
 */
internal sealed interface Screen {
    data object Home : Screen
    data class Detail(val gameId: Long) : Screen
    data class Add(val target: AddTarget) : Screen
    data object AddDigital : Screen
    /** Vista propia de una plataforma: ficha + juegos por lanzamiento. */
    data class Platform(val platform: String) : Screen
    /** Los juegos por "primera vez que lo jugué", incluidos los digitales. */
    data object Timeline : Screen
}
