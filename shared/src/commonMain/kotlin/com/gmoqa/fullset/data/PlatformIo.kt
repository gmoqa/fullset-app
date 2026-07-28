package com.gmoqa.fullset.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.datetime.Clock

/**
 * Handle de una imagen elegida por el usuario (Photo Picker). El contenido lo resuelve cada
 * plataforma (Android: `Uri`; iOS: pendiente — todavía no hay picker nativo). [model] es la
 * representación que Coil puede pintar para la vista previa (antes de copiarla a disco).
 */
expect class PlatformImage {
    val model: Any?
}

/** Milisegundos desde epoch. Reemplaza a `System.currentTimeMillis()`, que es solo de la JVM. */
internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Dispatcher para IO de disco/BD. Android: `Dispatchers.IO`; iOS: `Default` (no hay IO dedicado). */
expect val ioDispatcher: CoroutineDispatcher

/**
 * Almacenamiento interno de archivos (fotos, carátulas y audio de notas de voz). Frontera de
 * plataforma: en Android usa `filesDir` + `ContentResolver`; en iOS, el directorio Documents (la
 * copia desde el picker queda pendiente hasta que iOS tenga selector de imágenes).
 */
expect object FileStore {
    val photosDir: String
    val coversDir: String
    val audioDir: String
    fun exists(path: String): Boolean
    fun delete(path: String)
    fun listFilePaths(dir: String): List<String>
    fun copyImage(source: PlatformImage, destPath: String): Boolean
}
