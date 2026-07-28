package com.gmoqa.diariogamer.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/**
 * Lee un asset de texto (JSON de catálogos/config/seed) del bundle de la app iOS. Los archivos se
 * agregan al proyecto Xcode (iosApp) como referencia de carpeta, conservando la estructura
 * `catalogs/…`, `config/…`, `seed/…`, así que la ruta relativa mapea directo bajo `resourcePath`.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun readTextAsset(path: String): String? {
    val base = NSBundle.mainBundle.resourcePath ?: return null
    return NSString.stringWithContentsOfFile("$base/$path", NSUTF8StringEncoding, null)
}
