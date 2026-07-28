package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable

/**
 * Intercepta el gesto/botón de "volver" del sistema mientras [enabled] es true. Es una frontera de
 * plataforma: en Android engancha el back del sistema; en iOS no hay botón de retroceso global, así
 * que es no-op (la navegación la maneja la propia UI).
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
