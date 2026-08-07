package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypeZIP
import platform.darwin.NSObject

// Un backup real pesa unos KB; este tope corta un archivo enorme antes de leerlo entero a memoria.
private const val MAX_IMPORT_BYTES = 10 * 1024 * 1024

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

// PHPicker/UIDocumentPicker tienen delegate weak: hay que retenerlo mientras el picker está abierto.
private val activeImporters = mutableListOf<NSObject>()

/**
 * Restaurar en iOS: el usuario elige un archivo con UIDocumentPickerViewController. Se detecta el
 * formato por los **primeros bytes** (`PK` = ZIP), no por la extensión. El `.json` de solo datos se
 * restaura entero; el `.zip` (con fotos) todavía no —necesita descompresión, que llega con la lib de
 * ZIP de la tarea 3— y avisa con un alert en vez de fallar en silencio.
 */
@Composable
actual fun rememberBackupImporter(onBackup: (RestoredBackup) -> Unit): () -> Unit = remember(onBackup) {
    {
        // asCopy = true: el picker deja una copia temporal dentro del sandbox de la app, así se lee
        // sin el baile de startAccessingSecurityScopedResource().
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeJSON, UTTypeZIP, UTTypeData),
            asCopy = true,
        )
        val delegate = BackupImporterDelegate(onBackup)
        activeImporters.add(delegate)
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(picker, animated = true, completion = null)
    }
}

private class BackupImporterDelegate(
    private val onBackup: (RestoredBackup) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        activeImporters.remove(this)
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        handle(url)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        activeImporters.remove(this)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun handle(url: NSURL) {
        val data = NSData.dataWithContentsOfURL(url)
        if (data == null || data.length.toInt() == 0) {
            presentAlert("No se pudo leer el archivo")
            return
        }
        if (data.length.toInt() > MAX_IMPORT_BYTES) {
            presentAlert("El respaldo es demasiado grande")
            return
        }
        if (isZip(data)) {
            presentAlert("Restaurar desde ZIP todavía no está disponible en iOS. Usá el respaldo de solo datos (.json).")
            return
        }
        val text = NSString.create(data, NSUTF8StringEncoding) as String?
        if (text == null) {
            presentAlert("No se pudo leer el archivo")
            return
        }
        onBackup(RestoredBackup(text))
    }

    /** Firma de ZIP: los primeros dos bytes son "PK". */
    @OptIn(ExperimentalForeignApi::class)
    private fun isZip(data: NSData): Boolean {
        if (data.length.toInt() < 2) return false
        val p = data.bytes!!.reinterpret<ByteVar>()
        return p[0] == 'P'.code.toByte() && p[1] == 'K'.code.toByte()
    }

    private fun presentAlert(message: String) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = "Restaurar",
            message = message,
            preferredStyle = UIAlertControllerStyleAlert,
        )
        alert.addAction(UIAlertAction.actionWithTitle("OK", UIAlertActionStyleDefault, null))
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(alert, animated = true, completion = null)
    }
}
