package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable

/**
 * Asegura el permiso de micrófono y ejecuta [onGranted] cuando está concedido. Frontera de
 * plataforma: en Android chequea/pide RECORD_AUDIO; en iOS el permiso lo gestiona AVAudioSession
 * (prompt automático al grabar), así que el stub llama directo a [onGranted]. Devuelve la lambda a
 * invocar para disparar la solicitud.
 */
@Composable
expect fun rememberMicPermission(onGranted: () -> Unit): () -> Unit
