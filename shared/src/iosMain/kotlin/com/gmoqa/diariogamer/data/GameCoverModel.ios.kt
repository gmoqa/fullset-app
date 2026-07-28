package com.gmoqa.diariogamer.data

// En iOS la carátula local se pasa como ruta (String); Coil la resuelve desde el sistema de archivos.
// TODO(iOS): validar la carga real de archivos locales al levantar la UI en el simulador (Fase 4/5).
actual fun localCoverModel(path: String): Any = path
