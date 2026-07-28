package com.gmoqa.diariogamer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gmoqa.diariogamer.data.AndroidVoiceRecorder
import com.gmoqa.diariogamer.data.AndroidWhisperModelStore
import com.gmoqa.diariogamer.data.WhisperTranscriber

/**
 * Entry point de Android. La UI vive en `App()` (commonMain, compartida con iOS); acá solo se
 * construye el [DiaryViewModel] con las implementaciones Android de voz/whisper y la API key.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            App(vm, isDebug = BuildConfig.DEBUG)
        }
    }
}
