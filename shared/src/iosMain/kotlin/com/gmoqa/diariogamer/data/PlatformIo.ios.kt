package com.gmoqa.diariogamer.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

// Stub: iOS todavía no tiene Photo Picker; no se construye desde código común (Fase 4/5).
actual class PlatformImage

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

    actual fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    actual fun listFilePaths(dir: String): List<String> {
        val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, null) ?: return emptyList()
        return names.filterIsInstance<String>().map { "$dir/$it" }
    }

    // Sin picker en iOS aún: no hay imagen que copiar (Fase 4/5).
    actual fun copyImage(source: PlatformImage, destPath: String): Boolean = false
}
