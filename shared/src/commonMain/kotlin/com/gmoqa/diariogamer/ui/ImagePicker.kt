package com.gmoqa.diariogamer.ui

import androidx.compose.runtime.Composable
import com.gmoqa.diariogamer.data.PlatformImage

/**
 * Selector de imágenes de la galería. Frontera de plataforma: en Android abre el Photo Picker
 * (PickVisualMedia) y devuelve la imagen elegida como [PlatformImage]; en iOS todavía es no-op
 * (PHPicker queda para la Fase 4/5). Devuelve una lambda `launch` para disparar la selección.
 */
@Composable
expect fun rememberImagePicker(onPicked: (PlatformImage?) -> Unit): () -> Unit
