package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.gmoqa.fullset.data.PlatformImage
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

// PHPicker.delegate es weak: hay que retener el delegate mientras el picker está abierto.
private val activeDelegates = mutableListOf<NSObject>()

/**
 * Selector de imágenes en iOS con PHPickerViewController (no pide permisos). Carga la imagen elegida
 * a un archivo temporal y la entrega como [PlatformImage] (que luego el repo copia a covers/photos).
 */
@Composable
actual fun rememberImagePicker(onPicked: (PlatformImage?) -> Unit): () -> Unit = remember(onPicked) {
    {
        val config = PHPickerConfiguration()
        config.filter = PHPickerFilter.imagesFilter()
        config.selectionLimit = 1L
        val picker = PHPickerViewController(configuration = config)
        val delegate = ImagePickerDelegate(onPicked)
        activeDelegates.add(delegate)
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(picker, animated = true, completion = null)
    }
}

private class ImagePickerDelegate(
    private val onPicked: (PlatformImage?) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        activeDelegates.remove(this)

        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null) {
            onPicked(null)
            return
        }
        provider.loadDataRepresentationForTypeIdentifier("public.image") { data: NSData?, _: NSError? ->
            // El completion llega en un hilo de fondo; volvemos al main para tocar estado de Compose.
            dispatch_async(dispatch_get_main_queue()) {
                onPicked(data?.let { writeTemp(it) })
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun writeTemp(data: NSData): PlatformImage? {
        val tmp = NSTemporaryDirectory() + "pick_${NSUUID().UUIDString}.jpg"
        return if (data.writeToFile(tmp, atomically = true)) PlatformImage(tmp) else null
    }
}
