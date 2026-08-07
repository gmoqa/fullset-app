package com.gmoqa.fullset

import com.gmoqa.fullset.data.PlatformImage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

/**
 * Buzón de la foto que llega por el menú Compartir del sistema (Share Extension).
 *
 * La extensión corre en otro proceso y no comparte el sandbox con la app: deja la imagen en el
 * contenedor del App Group `group.com.gmoqa.fullset`. Cuando la app se vuelve activa, Swift llama a
 * [checkPending], que consume ese archivo (lo mueve a un temporal propio) y lo publica en [image].
 * `MainViewController` observa [image] y se lo pasa a `App(sharedImage = ...)`.
 */
object SharedImageInbox {
    private const val APP_GROUP = "group.com.gmoqa.fullset"
    private const val INCOMING = "shared_incoming.jpg"

    private val _image = MutableStateFlow<PlatformImage?>(null)
    val image: StateFlow<PlatformImage?> = _image.asStateFlow()

    /** La consume la UI cuando el usuario elige juego (o descarta el diálogo). */
    fun clear() {
        _image.value = null
    }

    /**
     * Se llama desde Swift al volver la app a primer plano. Si hay una imagen pendiente en el App
     * Group, la mueve a un temporal de la app y la publica. Mover (y no leer en su lugar) evita que
     * la extensión pise el archivo mientras la app lo usa, y deja el contenedor limpio.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun checkPending() {
        val fm = NSFileManager.defaultManager
        val container: NSURL = fm.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP) ?: return
        val srcPath = container.URLByAppendingPathComponent(INCOMING)?.path ?: return
        if (!fm.fileExistsAtPath(srcPath)) return

        val dst = NSTemporaryDirectory() + "shared_${NSUUID().UUIDString}.jpg"
        fm.removeItemAtPath(dst, null)
        if (fm.copyItemAtPath(srcPath, dst, null)) {
            fm.removeItemAtPath(srcPath, null) // consumida: no reaparece en el próximo foreground
            _image.value = PlatformImage(dst)
        }
    }
}
