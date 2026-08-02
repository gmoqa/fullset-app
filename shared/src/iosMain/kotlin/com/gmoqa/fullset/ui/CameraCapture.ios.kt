package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.gmoqa.fullset.data.PlatformImage

/**
 * Cámara en iOS: pendiente (UIImagePickerController con `sourceType = .camera`, que además pide
 * `NSCameraUsageDescription` en el Info.plist). Hasta entonces se reporta como no disponible y la UI
 * simplemente no ofrece la acción, en vez de mostrar un botón que no hace nada.
 */
@Composable
actual fun rememberCameraCapture(onCaptured: (PlatformImage?) -> Unit): CameraCapture =
    remember(onCaptured) { CameraCapture(available = false, launch = {}) }
