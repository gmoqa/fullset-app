package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable
import com.gmoqa.fullset.data.PlatformImage

/**
 * Selector de imágenes de la galería. Frontera de plataforma: Android abre el Photo Picker
 * (PickVisualMedia) e iOS el PHPicker; los dos devuelven lo elegido como [PlatformImage] y ninguno
 * pide permisos. Devuelve una lambda `launch` para disparar la selección.
 */
@Composable
expect fun rememberImagePicker(onPicked: (PlatformImage?) -> Unit): () -> Unit
