package com.gmoqa.diariogamer.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberCollectionExporter(csv: () -> String): () -> Unit {
    val context = LocalContext.current
    // "Guardar como" del sistema (SAF): el usuario elige carpeta/nombre; escribimos el CSV ahí.
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv().toByteArray(Charsets.UTF_8))
                } ?: error("no output stream")
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Collection exported" else "Export failed",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    return { saver.launch("fullset-collection.csv") }
}
