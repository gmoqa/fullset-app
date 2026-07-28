package com.gmoqa.fullset.data

/**
 * Modelo local de carátula que entiende Coil en cada plataforma (Android: `File`; iOS: la ruta como
 * String). Se resuelve por [expect]/[actual] porque `java.io.File` es solo de la JVM.
 */
expect fun localCoverModel(path: String): Any

/**
 * Carátula a mostrar: foto local (archivo) > URL automática (String) > null. Coil acepta ambos.
 */
val Game.coverModel: Any?
    get() = when {
        coverPath.isNotBlank() -> localCoverModel(coverPath)
        coverUrl.isNotBlank() -> coverUrl
        else -> null
    }
