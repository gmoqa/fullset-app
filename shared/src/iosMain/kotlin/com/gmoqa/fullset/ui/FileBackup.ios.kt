package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberBackupExporter(json: () -> String): () -> Unit = {
    val tmpPath = NSTemporaryDirectory() + "fullset-backup.json"
    (json() as NSString).writeToFile(tmpPath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    val url = NSURL.fileURLWithPath(tmpPath)
    val vc = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
    UIApplication.sharedApplication.keyWindow?.rootViewController
        ?.presentViewController(vc, animated = true, completion = null)
}

/**
 * El respaldo completo (ZIP) en iOS queda pendiente: Foundation no trae escritura de ZIP y habría
 * que sumar una dependencia. Mientras tanto exporta solo el JSON, así que no se pierden los datos —
 * solo las fotos, que en iOS todavía no se pueden ni sacar (ver `CameraCapture.ios.kt`).
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberArchiveExporter(archive: () -> BackupArchive): () -> Unit = {
    val tmpPath = NSTemporaryDirectory() + "fullset-backup.json"
    (archive().json as NSString)
        .writeToFile(tmpPath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    val url = NSURL.fileURLWithPath(tmpPath)
    val vc = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
    UIApplication.sharedApplication.keyWindow?.rootViewController
        ?.presentViewController(vc, animated = true, completion = null)
}

// Importar en iOS (UIDocumentPickerViewController) queda para después; por ahora no-op.
@Composable
actual fun rememberBackupImporter(onBackup: (RestoredBackup) -> Unit): () -> Unit = {}
