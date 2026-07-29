package com.gmoqa.fullset.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

// Un backup real pesa unos KB; este tope corta un archivo enorme (malicioso o equivocado) antes de
// leerlo entero a memoria (evita OOM).
private const val MAX_IMPORT_BYTES = 10 * 1024 * 1024

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
actual fun rememberBackupImporter(onJson: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val opener = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readCapped(MAX_IMPORT_BYTES).decodeToString() }
            }.getOrNull()
            if (text != null) onJson(text)
            else Toast.makeText(context, "Couldn't read file (or too large)", Toast.LENGTH_SHORT).show()
        }
    }
    // */* para que el picker no oculte el backup por su tipo MIME (varía según de dónde venga).
    return { opener.launch(arrayOf("*/*")) }
}
