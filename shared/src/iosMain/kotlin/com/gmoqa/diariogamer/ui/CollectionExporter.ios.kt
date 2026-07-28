package com.gmoqa.diariogamer.ui

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

/**
 * Exporta la colección en iOS: escribe el CSV a un archivo temporal y lo comparte con el share sheet
 * del sistema (UIActivityViewController), desde el que se puede guardar en Archivos, mandar por mail, etc.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCollectionExporter(csv: () -> String): () -> Unit = {
    val tmpPath = NSTemporaryDirectory() + "fullset-collection.csv"
    (csv() as NSString).writeToFile(tmpPath, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    val url = NSURL.fileURLWithPath(tmpPath)
    val activityVC = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
    UIApplication.sharedApplication.keyWindow?.rootViewController
        ?.presentViewController(activityVC, animated = true, completion = null)
}
