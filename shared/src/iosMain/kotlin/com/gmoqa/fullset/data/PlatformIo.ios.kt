package com.gmoqa.fullset.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

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

    /**
     * Achica el temporal del picker a [maxEdge] de lado largo y lo guarda como JPEG en covers/photos.
     * `UIImage` ya entrega la imagen enderezada según su EXIF, así que acá no hace falta el paso de
     * rotación que sí lleva Android.
     *
     * Si por lo que sea no se puede decodificar, cae a copiar el archivo tal cual: es preferible
     * guardar la foto pesada que perderla.
     */
    actual fun copyImage(source: PlatformImage, destPath: String, maxEdge: Int): Boolean {
        NSFileManager.defaultManager.removeItemAtPath(destPath, null) // copyItem falla si ya existe
        val image = UIImage.imageWithContentsOfFile(source.path)
            ?: return NSFileManager.defaultManager.copyItemAtPath(source.path, destPath, null)

        val size = image.size.useContents { width to height }
        val longest = maxOf(size.first, size.second)
        val ratio = if (longest > maxEdge) maxEdge / longest else 1.0
        val target = CGSizeMake(size.first * ratio, size.second * ratio)

        UIGraphicsBeginImageContextWithOptions(target, false, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, target.useContents { width }, target.useContents { height }))
        val resized = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        val data = resized?.let { UIImageJPEGRepresentation(it, IMAGE_QUALITY / 100.0) }
            ?: return NSFileManager.defaultManager.copyItemAtPath(source.path, destPath, null)
        return data.writeToFile(destPath, atomically = true)
    }
}
