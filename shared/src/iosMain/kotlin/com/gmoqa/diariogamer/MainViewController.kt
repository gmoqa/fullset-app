package com.gmoqa.diariogamer

import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gmoqa.diariogamer.data.IosTranscriber
import com.gmoqa.diariogamer.data.IosVoiceRecorder
import com.gmoqa.diariogamer.data.IosWhisperModelStore
import platform.UIKit.UIViewController

/**
 * Entry point de la UI en iOS: el proyecto Xcode (iosApp) lo llama desde Swift y monta este
 * UIViewController. Construye el [DiaryViewModel] con los stubs iOS de voz/whisper y sin API key de
 * SteamGridDB (el buscador de carátulas queda inactivo por ahora), y levanta la `App()` compartida.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    val vm = viewModel {
        DiaryViewModel(
            recorder = IosVoiceRecorder(),
            modelStore = IosWhisperModelStore(),
            transcriber = IosTranscriber(),
            steamGridKey = "",
        )
    }
    App(vm, isDebug = false)
}
