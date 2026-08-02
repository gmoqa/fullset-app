package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable
import com.gmoqa.fullset.data.PlatformImage

/**
 * Toma una foto con la cámara y la entrega como [PlatformImage], sin pasar por la galería: sacar la
 * foto, guardarla y después buscarla eran tres pasos para algo que ocurre mientras jugás.
 *
 * Frontera de plataforma. En Android delega en la app de cámara del sistema
 * (`ACTION_IMAGE_CAPTURE`), así que **no hace falta el permiso CAMERA**: la foto la saca la otra app
 * y nos devuelve el archivo. En iOS todavía es no-op, igual que el resto de la Fase 4/5.
 *
 * [available] dice si la plataforma puede capturar; la UI usa esto para no ofrecer una acción que no
 * va a funcionar (un emulador sin cámara, por ejemplo).
 */
@Composable
expect fun rememberCameraCapture(onCaptured: (PlatformImage?) -> Unit): CameraCapture

/** Disparador de la cámara + si está disponible en este dispositivo. */
class CameraCapture(
    val available: Boolean,
    val launch: () -> Unit,
)
