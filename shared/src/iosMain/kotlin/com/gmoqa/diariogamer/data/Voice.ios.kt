package com.gmoqa.diariogamer.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stubs iOS del subsistema de voz/whisper. Compilan y dejan la app funcional (sin notas de voz) hasta
 * la Fase 5, donde se implementan con AVAudioEngine (grabación) y whisper.cpp como framework.
 */
class IosVoiceRecorder : VoiceRecorder {
    private val _recording = MutableStateFlow(false)
    override val recording: StateFlow<Boolean> = _recording.asStateFlow()
    private val _elapsedMs = MutableStateFlow(0L)
    override val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()
    private val _amplitude = MutableStateFlow(0f)
    override val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    override fun start(path: String): Boolean = false // sin micrófono todavía
    override suspend fun stop(): Long = 0L
    override suspend fun cancel(path: String) {}
}

class IosWhisperModelStore : WhisperModelStore {
    override fun installed(): WhisperModel? = null
    override fun isInstalled(model: WhisperModel): Boolean = false
    override suspend fun download(model: WhisperModel, onProgress: (Float) -> Unit) {}
    override fun delete(model: WhisperModel) {}
}

class IosTranscriber : Transcriber {
    override fun transcribe(wavPath: String, language: TranscriptionLanguage): String? = null
    override fun release() {}
}
