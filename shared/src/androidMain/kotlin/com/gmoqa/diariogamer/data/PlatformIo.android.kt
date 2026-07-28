package com.gmoqa.diariogamer.data

import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

actual class PlatformImage(val uri: Uri)

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual object FileStore {
    private fun dir(name: String): File =
        File(AndroidApp.context.filesDir, name).apply { if (!exists()) mkdirs() }

    actual val photosDir: String get() = dir("photos").absolutePath
    actual val coversDir: String get() = dir("covers").absolutePath
    actual val audioDir: String get() = dir("audio").absolutePath

    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    actual fun listFilePaths(dir: String): List<String> =
        File(dir).listFiles()?.map { it.absolutePath } ?: emptyList()

    actual fun copyImage(source: PlatformImage, destPath: String): Boolean = runCatching {
        AndroidApp.context.contentResolver.openInputStream(source.uri)?.use { input ->
            File(destPath).outputStream().use { output -> input.copyTo(output) }
        } ?: return false
    }.isSuccess
}
