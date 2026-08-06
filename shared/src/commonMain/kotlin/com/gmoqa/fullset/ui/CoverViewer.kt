package com.gmoqa.fullset.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage

/** Hasta dónde se puede acercar. Cinco veces alcanza para leer el catalog number del lomo. */
private const val ZOOM_MAX = 5f

/** A cuánto lleva el doble toque. Suficiente para leer la contratapa sin tener que pellizcar. */
private const val ZOOM_DOBLE_TOQUE = 2.5f

/**
 * La carátula a pantalla completa, para verla de cerca.
 *
 * En el estante la tapa mide 140dp: alcanza para reconocer el juego, no para leer el sello de
 * Nintendo, el catalog number del lomo ni el texto de la contratapa. Y eso es justo lo que se quiere
 * mirar cuando se está catalogando una copia física.
 *
 * Gestos: pellizcar para acercar, arrastrar para moverse, doble toque para ir y volver del zoom. Un
 * toque simple cierra **solo si no está acercada** — con la imagen ampliada ese toque es casi
 * siempre el final de un arrastre, y cerrar ahí se siente como que la app te sacó la foto de la
 * mano; ahí el toque vuelve al tamaño original y recién el siguiente cierra.
 */
@Composable
fun CoverViewer(model: Any?, contentDescription: String?, onDismiss: () -> Unit) {
    if (model == null) return
    Dialog(
        onDismissRequest = onDismiss,
        // Sin esto el diálogo se queda con el ancho de un diálogo normal y la carátula sale
        // diminuta en el medio, que es exactamente lo que veníamos a resolver.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center,
        ) {
            var escala by remember { mutableStateOf(1f) }
            var desplazamiento by remember { mutableStateOf(Offset.Zero) }

            // Cuánto se puede arrastrar sin que la imagen se vaya de pantalla: la mitad de lo que
            // sobresale de cada lado. Sin este tope se puede empujar la tapa fuera de la vista y
            // queda una pantalla negra sin forma de volver salvo cerrar.
            val maxX = { (constraints.maxWidth * (escala - 1f) / 2f).coerceAtLeast(0f) }
            val maxY = { (constraints.maxHeight * (escala - 1f) / 2f).coerceAtLeast(0f) }
            fun acotar(o: Offset) = Offset(
                o.x.coerceIn(-maxX(), maxX()),
                o.y.coerceIn(-maxY(), maxY()),
            )

            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, arrastre, zoom, _ ->
                            escala = (escala * zoom).coerceIn(1f, ZOOM_MAX)
                            desplazamiento =
                                if (escala > 1f) acotar(desplazamiento + arrastre) else Offset.Zero
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (escala > 1f) {
                                    escala = 1f
                                    desplazamiento = Offset.Zero
                                } else {
                                    onDismiss()
                                }
                            },
                            onDoubleTap = {
                                if (escala > 1f) {
                                    escala = 1f
                                    desplazamiento = Offset.Zero
                                } else {
                                    escala = ZOOM_DOBLE_TOQUE
                                }
                            },
                        )
                    }
                    .graphicsLayer(
                        scaleX = escala,
                        scaleY = escala,
                        translationX = desplazamiento.x,
                        translationY = desplazamiento.y,
                    ),
            )
        }
    }
}

/** Estado de "qué carátula estoy mirando"; null = ninguna. Lo comparten detalle y Playing. */
@Composable
fun rememberCoverViewer(): CoverViewerState = remember { CoverViewerState() }

class CoverViewerState {
    var model by mutableStateOf<Any?>(null)
        private set
    var description by mutableStateOf<String?>(null)
        private set

    fun show(model: Any?, description: String?) {
        if (model == null) return
        this.model = model
        this.description = description
    }

    fun dismiss() {
        model = null
    }
}
