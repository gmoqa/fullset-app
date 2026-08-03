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
 * Lo que va dentro del respaldo completo: el **mismo JSON** del respaldo de solo datos más las fotos
 * del diario. Que sea el mismo y no un formato aparte es a propósito — así restaurar es un único
 * camino de código y las dos variantes no pueden divergir.
 */
class BackupArchive(
    val json: String,
    /** Rutas locales de las fotos; en el ZIP quedan como `photos/<nombre de archivo>`. */
    val photoPaths: List<String>,
)

/**
 * Respaldo **completo** a un `.zip`: el JSON más las fotos. Va aparte del de solo datos porque pesan
 * órdenes de magnitud distintos — el de texto son KB y se hace seguido; este son cientos de MB y se
 * hace de vez en cuando.
 */
@Composable
expect fun rememberArchiveExporter(archive: () -> BackupArchive): () -> Unit

/**
 * Un respaldo leído de disco, listo para el merge.
 *
 * Si el archivo era un `.zip`, sus fotos **ya se extrajeron** al almacenamiento de la app y [photos]
 * mapea el nombre que tenían dentro del respaldo a su ruta local nueva; si era un `.json` suelto,
 * viene vacío.
 */
class RestoredBackup(
    val json: String,
    val photos: Map<String, String> = emptyMap(),
)

/**
 * Restaurar: el usuario elige un respaldo (`.json` o `.zip`) y su contenido llega por [onBackup],
 * que hace el merge. El formato se detecta por el **contenido**, no por la extensión, así que un
 * archivo renombrado igual funciona. Android: selector del sistema; iOS: pendiente.
 */
@Composable
expect fun rememberBackupImporter(onBackup: (RestoredBackup) -> Unit): () -> Unit
