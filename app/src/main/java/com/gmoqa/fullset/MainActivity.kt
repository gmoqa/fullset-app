package com.gmoqa.fullset

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gmoqa.fullset.data.AndroidVoiceRecorder
import com.gmoqa.fullset.data.AndroidWhisperModelStore
import com.gmoqa.fullset.data.PlatformImage
import com.gmoqa.fullset.data.WhisperTranscriber

/**
 * Entry point de Android. La UI vive en `App()` (commonMain, compartida con iOS); acá solo se
 * construye el [DiaryViewModel] con las implementaciones Android de voz/whisper y la API key, y se
 * resuelve la imagen que llegue por el menú Compartir del sistema.
 */
class MainActivity : ComponentActivity() {

    /**
     * Imagen recibida por `ACTION_SEND`, si la hay. Es estado de Compose para que llegue a la UI
     * tanto en el arranque en frío como cuando el intent entra con la app ya abierta.
     */
    private var sharedImage by mutableStateOf<PlatformImage?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedImage = sharedImageOf(intent)
        setContent {
            // El ViewModel sobrevive rotaciones: no re-siembra ni re-consulta al girar la pantalla.
            val vm: DiaryViewModel = viewModel {
                val store = AndroidWhisperModelStore(applicationContext)
                DiaryViewModel(
                    recorder = AndroidVoiceRecorder(),
                    modelStore = store,
                    transcriber = WhisperTranscriber(store),
                    steamGridKey = BuildConfig.STEAMGRIDDB_API_KEY,
                )
            }
            App(
                vm = vm,
                isDebug = BuildConfig.DEBUG,
                sharedImage = sharedImage,
                onSharedImageHandled = { sharedImage = null },
            )
        }
    }

    /** Con `launchMode="singleTop"`, compartir con la app ya abierta entra por acá. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedImageOf(intent)?.let { sharedImage = it }
    }

    /**
     * La imagen de un intent de compartir, o null si no es uno.
     *
     * El permiso sobre este `Uri` lo concede el sistema **mientras esta activity viva**, así que la
     * foto se copia a almacenamiento propio en cuanto elegís el juego (`FileStore.copyImage`) y no
     * se guarda la URI en la base: al reiniciar ya no sería legible.
     */
    private fun sharedImageOf(intent: Intent?): PlatformImage? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        return uri?.let { PlatformImage(it) }
    }
}
