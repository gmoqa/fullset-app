package com.gmoqa.fullset.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

// El Photo Picker (PHPicker) guarda la imagen elegida en un archivo temporal y pasa su ruta acá.
// [model] es una URL file:// que Coil puede pintar para la vista previa.
actual class PlatformImage(val path: String) {
    actual val model: Any? get() = "file://$path"
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

@OptIn(ExperimentalForeignApi::class)
actual object FileStore {
    private val documents: String by lazy {
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: ""
    }

    private fun dir(name: String): String {
        val path = "$documents/$name"
        NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
        return path
    }

    actual val photosDir: String get() = dir("photos")
    actual val coversDir: String get() = dir("covers")
    actual val audioDir: String get() = dir("audio")

    actual fun exists(path: String): Boolean =
        NSFileManager.defaultManager.fileExistsAtPath(path)

    actual fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    actual fun listFilePaths(dir: String): List<String> {
        val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, null) ?: return emptyList()
        return names.filterIsInstance<String>().map { "$dir/$it" }
    }

    // Copia el temporal del picker a almacenamiento interno (covers/photos).
    actual fun copyImage(source: PlatformImage, destPath: String): Boolean {
        NSFileManager.defaultManager.removeItemAtPath(destPath, null) // copyItem falla si ya existe
        return NSFileManager.defaultManager.copyItemAtPath(source.path, destPath, null)
    }
}
