package com.gmoqa.diariogamer.ui

import androidx.compose.runtime.Composable
import com.gmoqa.diariogamer.data.PlatformImage

// Stub: iOS todavía no tiene selector de imágenes (PHPicker llega en la Fase 4/5). No-op.
@Composable
actual fun rememberImagePicker(onPicked: (PlatformImage?) -> Unit): () -> Unit = {}
