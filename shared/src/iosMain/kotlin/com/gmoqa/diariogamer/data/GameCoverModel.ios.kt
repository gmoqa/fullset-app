package com.gmoqa.diariogamer.data

// Carátula/foto local en iOS: URL file:// que Coil resuelve desde el sistema de archivos.
actual fun localCoverModel(path: String): Any = "file://$path"
