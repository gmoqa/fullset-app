package com.gmoqa.diariogamer.ui

import androidx.compose.runtime.Composable

// iOS: el permiso de micrófono lo pide AVAudioSession al grabar (Fase 5). Por ahora pasa directo.
@Composable
actual fun rememberMicPermission(onGranted: () -> Unit): () -> Unit = { onGranted() }
