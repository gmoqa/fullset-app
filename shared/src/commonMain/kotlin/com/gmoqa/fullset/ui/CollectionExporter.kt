package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable

/**
 * Exporta la colección a un CSV que el usuario guarda donde quiera. Frontera de plataforma: en
 * Android usa "Guardar como" del sistema (SAF) y avisa con un Toast; en iOS será un share sheet en
 * la Fase 5 (por ahora no-op). Devuelve la lambda a invocar para lanzar la exportación; [csv]
 * produce el contenido en el momento.
 */
@Composable
expect fun rememberCollectionExporter(csv: () -> String): () -> Unit
