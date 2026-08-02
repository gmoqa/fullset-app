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
/**
 * Lado largo máximo al guardar una foto o carátula, en píxeles.
 *
 * Las fotos del diario se muestran en una tarjeta de 220dp de alto a todo el ancho — menos de
 * 1100px en la pantalla más densa —, así que guardar los 4000px que entrega la cámara es cargar
 * unas veinte veces más píxeles de los que se ven: a tres fotos por juego, una colección de 400
 * juegos son gigabytes. Con 1600 queda el doble de resolución de la que la vista necesita, que deja
 * margen para un visor con zoom más adelante, y cada foto pasa de ~3,5 MB a unos 300 KB.
 */
const val IMAGE_MAX_EDGE = 1600

/** Calidad JPEG al reescribir. 85 es donde la pérdida deja de notarse a simple vista. */
const val IMAGE_QUALITY = 85

/**
 * Factor de submuestreo al decodificar: la mayor potencia de 2 que **no** baja el lado largo por
 * debajo de [maxEdge]. Sirve para no cargar nunca la imagen entera en memoria (un JPEG de 12 MP son
 * ~48 MB de bitmap); el ajuste fino al tamaño exacto lo hace después un escalado normal.
 *
 * Se queda corto a propósito: si dejara la imagen *por debajo* del objetivo, el escalado posterior
 * tendría que agrandarla y se vería borrosa. Mejor decodificar hasta 2× de más y bajar.
 */
internal fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
    return sample
}

expect object FileStore {
    val photosDir: String
    val coversDir: String
    val audioDir: String
    fun exists(path: String): Boolean
    fun delete(path: String)
    fun listFilePaths(dir: String): List<String>

    /**
     * Copia la imagen elegida a [destPath], **redimensionándola** a [maxEdge] de lado largo y
     * normalizando su orientación. Devuelve false si no se pudo leer o decodificar.
     */
    fun copyImage(source: PlatformImage, destPath: String, maxEdge: Int = IMAGE_MAX_EDGE): Boolean
}
