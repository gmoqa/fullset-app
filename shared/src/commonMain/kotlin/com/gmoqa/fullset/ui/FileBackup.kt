package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable

/**
 * Respaldo de las listas a un archivo `.json` que el usuario guarda donde quiera (Android: SAF
 * "Guardar como"; iOS: share sheet). [json] arma el contenido en el momento. Devuelve la lambda a
 * invocar para lanzar el guardado.
 */
@Composable
expect fun rememberBackupExporter(json: () -> String): () -> Unit

/**
 * Restaurar: el usuario elige un `.json` de respaldo y su contenido llega por [onJson] (que hace el
 * merge). Android: selector de archivos del sistema; iOS: pendiente (por ahora no-op).
 */
@Composable
expect fun rememberBackupImporter(onJson: (String) -> Unit): () -> Unit
