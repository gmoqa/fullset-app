package com.gmoqa.diariogamer.data

/**
 * Lee un asset de texto empaquetado (JSON de catálogos/config) por ruta relativa. Null si no está.
 * En Android sale del AssetManager (vía [AndroidApp]); en iOS, del bundle de la app (pendiente).
 */
expect fun readTextAsset(path: String): String?
