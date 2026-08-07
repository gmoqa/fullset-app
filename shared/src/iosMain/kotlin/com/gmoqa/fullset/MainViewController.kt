package com.gmoqa.fullset

import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.gmoqa.fullset.data.IosTranscriber
import com.gmoqa.fullset.data.IosVoiceRecorder
import com.gmoqa.fullset.data.IosWhisperModelStore
import platform.UIKit.UIViewController

/**
 * Entry point de la UI en iOS: el proyecto Xcode (iosApp) lo llama desde Swift y monta este
 * UIViewController. Construye el [DiaryViewModel] con los stubs iOS de voz/whisper y la clave de
 * SteamGridDB ([STEAMGRIDDB_API_KEY], generada desde local.properties), y levanta la `App()` compartida.
 */
fun MainViewController(): UIViewController {
    // Coil no auto-registra el fetcher de red fuera de Android: lo agregamos al ImageLoader singleton
    // para que las carátulas remotas (URLs) carguen. Los archivos locales (file://) ya los maneja.
    SingletonImageLoader.setSafe { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }
    return ComposeUIViewController {
        val vm = viewModel {
            val whisperStore = IosWhisperModelStore()
            DiaryViewModel(
                recorder = IosVoiceRecorder(),
                modelStore = whisperStore,
                transcriber = IosTranscriber(whisperStore),
                steamGridKey = STEAMGRIDDB_API_KEY,
            )
        }
        // Foto que llegó por Compartir (Share Extension), vía el App Group. Swift dispara
        // checkPendingSharedImage() al volver la app a primer plano y esto recompone.
        val shared by SharedImageInbox.image.collectAsStateWithLifecycle()
        App(
            vm,
            isDebug = false,
            sharedImage = shared,
            onSharedImageHandled = { SharedImageInbox.clear() },
        )
    }
}

/**
 * Puente para Swift (`MainViewControllerKt.checkPendingSharedImage()`): lo llama el ciclo de vida de
 * la app al volver a primer plano, para levantar la foto que dejó la Share Extension en el App Group.
 */
fun checkPendingSharedImage() = SharedImageInbox.checkPending()
