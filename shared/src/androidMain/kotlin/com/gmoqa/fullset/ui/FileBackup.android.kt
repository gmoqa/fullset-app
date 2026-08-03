package com.gmoqa.fullset.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.gmoqa.fullset.data.FileStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Un backup real pesa unos KB; este tope corta un archivo enorme (malicioso o equivocado) antes de
// leerlo entero a memoria (evita OOM).
private const val MAX_IMPORT_BYTES = 10 * 1024 * 1024

// Nombres dentro del ZIP. El JSON es idéntico al del respaldo de solo datos.
private const val BACKUP_JSON_ENTRY = "backup.json"
private const val PHOTOS_PREFIX = "photos/"

/** Lee hasta [max] bytes; aborta si el archivo lo supera (sin cargarlo entero). */
private fun InputStream.readCapped(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (true) {
        val r = read(buf)
        if (r < 0) break
        total += r
        if (total > max) error("Backup file too large")
        out.write(buf, 0, r)
    }
    return out.toByteArray()
}

@Composable
actual fun rememberBackupExporter(json: () -> String): () -> Unit {
    val context = LocalContext.current
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json().toByteArray(Charsets.UTF_8))
                } ?: error("no output stream")
            }.isSuccess
            Toast.makeText(context, if (ok) "Backup saved" else "Backup failed", Toast.LENGTH_SHORT).show()
        }
    }
    return { saver.launch("fullset-backup.json") }
}

@Composable
actual fun rememberArchiveExporter(archive: () -> BackupArchive): () -> Unit {
    val context = LocalContext.current
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            val content = archive()
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ZipOutputStream(out.buffered()).use { zip ->
                        zip.putNextEntry(ZipEntry(BACKUP_JSON_ENTRY))
                        zip.write(content.json.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                        // Las fotos van con su nombre de archivo: la ruta local no le sirve a otro
                        // dispositivo. El JSON las referencia por ese mismo nombre.
                        content.photoPaths.forEach { path ->
                            val file = File(path)
                            if (!file.isFile) return@forEach
                            zip.putNextEntry(ZipEntry("$PHOTOS_PREFIX${file.name}"))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                } ?: error("no output stream")
            }.isSuccess
            Toast.makeText(context, if (ok) "Backup saved" else "Backup failed", Toast.LENGTH_SHORT).show()
        }
    }
    return { saver.launch("fullset-backup.zip") }
}

@Composable
actual fun rememberBackupImporter(onBackup: (RestoredBackup) -> Unit): () -> Unit {
    val context = LocalContext.current
    val opener = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val restored = runCatching { readBackup(context, uri) }.getOrNull()
            if (restored != null) onBackup(restored)
            else Toast.makeText(context, "Couldn't read file (or too large)", Toast.LENGTH_SHORT).show()
        }
    }
    // */* para que el picker no oculte el backup por su tipo MIME (varía según de dónde venga).
    return { opener.launch(arrayOf("*/*")) }
}

/**
 * Lee un respaldo, sea `.json` suelto o `.zip` completo. El formato se detecta por los **primeros
 * bytes** (`PK`, la firma de ZIP) y no por la extensión: un archivo renombrado sigue funcionando.
 */
private fun readBackup(context: android.content.Context, uri: android.net.Uri): RestoredBackup? {
    val head = context.contentResolver.openInputStream(uri)?.use { input ->
        ByteArray(2).also { input.read(it) }
    } ?: return null
    val isZip = head.size == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()

    if (!isZip) {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.readCapped(MAX_IMPORT_BYTES).decodeToString() } ?: return null
        return RestoredBackup(text)
    }

    var json: String? = null
    val photos = mutableMapOf<String, String>()
    val destDir = File(FileStore.photosDir).apply { mkdirs() }
    context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == BACKUP_JSON_ENTRY ->
                        json = zip.readCapped(MAX_IMPORT_BYTES).decodeToString()

                    name.startsWith(PHOTOS_PREFIX) && !entry.isDirectory -> {
                        // Solo el nombre del archivo: un ZIP hostil podría traer "../.." y escribir
                        // fuera de nuestro directorio (zip slip).
                        val safe = name.substringAfterLast('/')
                        if (safe.isNotBlank()) {
                            val dest = File(destDir, "restored_${System.currentTimeMillis()}_$safe")
                            dest.outputStream().use { zip.copyTo(it) }
                            photos[safe] = dest.absolutePath
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
    return json?.let { RestoredBackup(it, photos) }
}
