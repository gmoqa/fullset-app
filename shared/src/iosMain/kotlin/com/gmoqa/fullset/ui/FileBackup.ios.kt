package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.appendBytes
import platform.Foundation.appendData
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
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

// Nombres dentro del ZIP: iguales a los que escribe Android, para que un respaldo hecho en iOS se
// restaure en Android (y viceversa). El JSON es idéntico al del respaldo de solo datos.
private const val BACKUP_JSON_ENTRY = "backup.json"
private const val PHOTOS_PREFIX = "photos/"

/**
 * Respaldo completo a un `.zip` (JSON + fotos) y share sheet. Foundation no trae escritura de ZIP y
 * no se puede llamar una lib de Swift desde Kotlin/Native, así que se arma el ZIP a mano en formato
 * **stored** (sin compresión): los JPEG ya vienen comprimidos y el JSON es chico, y `ZipInputStream`
 * de Android lo lee igual. Se construye en memoria; alcanza para los tamaños actuales de iOS.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberArchiveExporter(archive: () -> BackupArchive): () -> Unit = {
    val content = archive()
    val zip = StoredZip()
    zip.addEntry(BACKUP_JSON_ENTRY, content.json.encodeToByteArray())
    content.photoPaths.forEach { path ->
        val data = NSData.dataWithContentsOfFile(path) ?: return@forEach
        zip.addEntry("$PHOTOS_PREFIX${path.substringAfterLast('/')}", data)
    }

    val tmpPath = NSTemporaryDirectory() + "fullset-backup.zip"
    zip.finish().writeToFile(tmpPath, atomically = true)
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

// ---------- Escritura de ZIP en formato stored (sin compresión), a mano ----------

private val CRC32_TABLE = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1) }
    c
}

private fun crc32Byte(crc: Int, byte: Int): Int = CRC32_TABLE[(crc xor byte) and 0xFF] xor (crc ushr 8)

private fun crc32(bytes: ByteArray): Int {
    var crc = -1 // 0xFFFFFFFF
    for (b in bytes) crc = crc32Byte(crc, b.toInt() and 0xFF)
    return crc xor -1
}

@OptIn(ExperimentalForeignApi::class)
private fun crc32(data: NSData): Int {
    val len = data.length.toInt()
    if (len == 0) return 0
    val p = data.bytes!!.reinterpret<ByteVar>()
    var crc = -1
    for (i in 0 until len) crc = crc32Byte(crc, p[i].toInt() and 0xFF)
    return crc xor -1
}

/**
 * Arma un ZIP en memoria en formato *stored* (método 0, sin compresión). Estructura mínima: por cada
 * entrada un local file header + sus datos; al final el central directory y el end-of-central-directory.
 */
@OptIn(ExperimentalForeignApi::class)
private class StoredZip {
    private val out = NSMutableData()
    private val central = mutableListOf<CentralEntry>()

    private class CentralEntry(val name: ByteArray, val crc: Int, val size: Int, val offset: Int)

    fun addEntry(name: String, bytes: ByteArray) =
        writeEntry(name, crc32(bytes), bytes.size) { out.appendByteArray(bytes) }

    fun addEntry(name: String, data: NSData) =
        writeEntry(name, crc32(data), data.length.toInt()) { out.appendData(data) }

    private inline fun writeEntry(name: String, crc: Int, size: Int, writeData: () -> Unit) {
        val nameBytes = name.encodeToByteArray()
        val offset = out.length.toInt()
        val h = LeBuilder()
        h.le32(0x04034b50)   // firma local file header
        h.le16(20)           // versión necesaria
        h.le16(0)            // flags
        h.le16(0)            // método: stored
        h.le16(0)            // hora
        h.le16(0x21)         // fecha (1980-01-01; 0 es inválido)
        h.le32(crc)
        h.le32(size)         // comprimido = sin comprimir (stored)
        h.le32(size)
        h.le16(nameBytes.size)
        h.le16(0)            // extra
        h.bytes(nameBytes)
        out.appendByteArray(h.build())
        writeData()
        central.add(CentralEntry(nameBytes, crc, size, offset))
    }

    fun finish(): NSData {
        val cdStart = out.length.toInt()
        for (e in central) {
            val h = LeBuilder()
            h.le32(0x02014b50)   // firma central directory header
            h.le16(20); h.le16(20)
            h.le16(0); h.le16(0)
            h.le16(0); h.le16(0x21)
            h.le32(e.crc)
            h.le32(e.size); h.le32(e.size)
            h.le16(e.name.size)
            h.le16(0); h.le16(0)   // extra / comentario
            h.le16(0); h.le16(0)   // disco / attrs internos
            h.le32(0)              // attrs externos
            h.le32(e.offset)       // offset del local header
            h.bytes(e.name)
            out.appendByteArray(h.build())
        }
        val cdSize = out.length.toInt() - cdStart
        val eocd = LeBuilder()
        eocd.le32(0x06054b50)      // firma EOCD
        eocd.le16(0); eocd.le16(0)
        eocd.le16(central.size); eocd.le16(central.size)
        eocd.le32(cdSize)
        eocd.le32(cdStart)
        eocd.le16(0)               // comentario
        out.appendByteArray(eocd.build())
        return out
    }

    private fun NSMutableData.appendByteArray(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        bytes.usePinned { appendBytes(it.addressOf(0), bytes.size.toULong()) }
    }
}

/** Acumulador de bytes little-endian para armar los headers del ZIP. */
private class LeBuilder {
    private val buf = ArrayList<Byte>()
    fun le16(v: Int) { buf.add((v and 0xFF).toByte()); buf.add(((v ushr 8) and 0xFF).toByte()) }
    fun le32(v: Int) {
        buf.add((v and 0xFF).toByte()); buf.add(((v ushr 8) and 0xFF).toByte())
        buf.add(((v ushr 16) and 0xFF).toByte()); buf.add(((v ushr 24) and 0xFF).toByte())
    }
    fun bytes(b: ByteArray) { b.forEach { buf.add(it) } }
    fun build(): ByteArray = buf.toByteArray()
}
