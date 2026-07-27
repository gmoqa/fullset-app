package com.gmoqa.diariogamer.data

import java.io.File

/**
 * Carátula a mostrar en Android: foto local (File) > URL automática (String) > null. Coil acepta
 * ambos. Vive del lado Android porque usa `java.io.File`; en el futuro target de iOS se resolverá
 * con el equivalente de Coil multiplataforma.
 */
val Game.coverModel: Any?
    get() = when {
        coverPath.isNotBlank() -> File(coverPath)
        coverUrl.isNotBlank() -> coverUrl
        else -> null
    }
