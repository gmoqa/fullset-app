package com.gmoqa.fullset.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()
            if (text != null) onJson(text)
            else Toast.makeText(context, "Couldn't read file", Toast.LENGTH_SHORT).show()
        }
    }
    // */* para que el picker no oculte el backup por su tipo MIME (varía según de dónde venga).
    return { opener.launch(arrayOf("*/*")) }
}
