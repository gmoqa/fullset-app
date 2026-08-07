package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.gmoqa.fullset.data.PlatformImage
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

// UIImagePickerController.delegate es weak: hay que retener el delegate mientras el picker está
// abierto, igual que en ImagePicker.ios.kt.
private val activeCameraDelegates = mutableListOf<NSObject>()

/**
 * Cámara en iOS con UIImagePickerController (`sourceType = .camera`). Saca la foto, la escribe a un
 * archivo temporal como JPEG y la entrega como [PlatformImage] (que luego el repo copia a photos/).
 *
 * [available] es `isSourceTypeAvailable(.camera)`: en el simulador da false y la UI no ofrece la
 * acción, así que **hay que probar en un dispositivo real**. Necesita `NSCameraUsageDescription` en el
 * Info.plist o la app crashea al abrir la cámara.
 */
@Composable
actual fun rememberCameraCapture(onCaptured: (PlatformImage?) -> Unit): CameraCapture =
    remember(onCaptured) {
        val available = UIImagePickerController.isSourceTypeAvailable(
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
        )
        CameraCapture(available) {
            val picker = UIImagePickerController()
            picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            val delegate = CameraCaptureDelegate(onCaptured)
            activeCameraDelegates.add(delegate)
            picker.delegate = delegate
            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.presentViewController(picker, animated = true, completion = null)
        }
    }

private class CameraCaptureDelegate(
    private val onCaptured: (PlatformImage?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, null)
        activeCameraDelegates.remove(this)

        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        onCaptured(image?.let { writeTemp(it) })
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
        activeCameraDelegates.remove(this)
        onCaptured(null)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun writeTemp(image: UIImage): PlatformImage? {
        val data = UIImageJPEGRepresentation(image, 0.9) ?: return null
        val tmp = NSTemporaryDirectory() + "capture_${NSUUID().UUIDString}.jpg"
        return if (data.writeToFile(tmp, atomically = true)) PlatformImage(tmp) else null
    }
}
