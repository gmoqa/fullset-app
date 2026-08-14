package com.gmoqa.fullset.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * La pila de navegación sobre Home.
 *
 * Home es el fondo —el pager con las pestañas— y cada pantalla que se abre (detalle, plataforma,
 * agregar) se apila encima; "atrás" desapila **una**. Por eso volver desde el detalle de un juego
 * regresa a la vista de plataforma desde la que se abrió, y no siempre a Home.
 *
 * Está separado de los destinos a propósito: el **mecanismo** —qué hay apilado, cómo se entra y se
 * sale, hacia dónde anima— no depende de qué pantallas existan. Los destinos, en cambio, sí
 * necesitan todo el contexto de la app, y por eso se quedan donde ese contexto vive.
 */
internal class BackStack(
    private val pila: MutableList<Screen>,
    var haciaAtras: Boolean,
    private val setHaciaAtras: (Boolean) -> Unit,
) {
    val actual: Screen get() = pila.lastOrNull() ?: Screen.Home

    fun abrir(destino: Screen) {
        // Doble tap rápido en la misma fila: no apilar dos veces la misma pantalla, o "atrás"
        // parecería no hacer nada la primera vez.
        if (pila.lastOrNull() == destino) return
        setHaciaAtras(false)
        pila.add(destino)
    }

    fun atras() {
        setHaciaAtras(true)
        pila.removeLastOrNull()
    }
}

@Composable
internal fun rememberBackStack(): BackStack {
    val pila = remember { mutableStateListOf<Screen>() }
    var haciaAtras by remember { mutableStateOf(false) }
    return BackStack(pila, haciaAtras) { haciaAtras = it }
}

/**
 * Dibuja la pantalla actual con la animación que corresponde a la dirección.
 *
 * Al apilar, la nueva entra desde la derecha; al desapilar, sale hacia la derecha y la anterior
 * reaparece con fundido. Es lo que hace que "atrás" se sienta como volver y no como ir a otro lado.
 */
@Composable
internal fun NavHost(stack: BackStack, content: @Composable (Screen) -> Unit) {
    AnimatedContent(
        targetState = stack.actual,
        transitionSpec = {
            if (!stack.haciaAtras) {
                (slideInHorizontally(tween(240)) { it } + fadeIn(tween(240))) togetherWith
                    fadeOut(tween(200))
            } else {
                fadeIn(tween(220)) togetherWith
                    (slideOutHorizontally(tween(240)) { it } + fadeOut(tween(240)))
            }
        },
        label = "nav",
    ) { current -> content(current) }
}
