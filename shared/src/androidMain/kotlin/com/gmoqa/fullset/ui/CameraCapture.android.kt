package com.gmoqa.fullset.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.FileProvider
import com.gmoqa.fullset.data.AndroidApp
import com.gmoqa.fullset.data.PlatformImage
import java.io.File

/**
 * Captura con la app de cámara del sistema (`ACTION_IMAGE_CAPTURE` vía `TakePicture`).
 *
 * Dos decisiones que importan:
 *  - **Sin permiso CAMERA.** La foto la saca otra app y nos deja el archivo, así que el permiso no
 *    hace falta. Es más: declararlo obligaría a pedirlo en runtime aunque no usemos la cámara
 *    directamente, por como Android trata ese intent. La app sigue con INTERNET + RECORD_AUDIO.
 *  - **Archivo en la caché.** La captura va a `cacheDir/captures`, que el sistema puede limpiar. El
 *    repo copia la imagen a `photos/` o `covers/` cuando la acepta, así que el temporal ya no
 *    importa; el que quede de una captura cancelada se borra en el siguiente intento.
 */
@Composable
actual fun rememberCameraCapture(onCaptured: (PlatformImage?) -> Unit): CameraCapture {
    val context = AndroidApp.context
    val available = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                .resolveActivity(context.packageManager) != null
    }
    // El destino se decide al lanzar (necesita nombre único), pero el resultado llega después: se
    // guarda acá para poder entregarlo cuando la cámara confirma.
    val pending = remember { arrayOfNulls<PlatformImage>(1) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        onCaptured(if (ok) pending[0] else null)
        pending[0] = null
    }

    return CameraCapture(available) {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        // Limpia capturas viejas (una cancelada deja el archivo vacío) para no acumular basura.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pending[0] = PlatformImage(uri)
        launcher.launch(uri)
    }
}
