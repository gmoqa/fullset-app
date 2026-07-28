package com.gmoqa.fullset.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Fronteras del subsistema de notas de voz, para que el [DiaryViewModel] común no dependa de las
 * APIs de audio/JNI de cada plataforma. Android tiene implementaciones reales (AudioRecord +
 * whisper.cpp); iOS arranca con stubs (AVAudioEngine/whisper quedan para la Fase 5).
 */

/** Graba una nota de voz a un WAV (PCM 16 kHz mono, lo que consume Whisper). */
interface VoiceRecorder {
    val recording: StateFlow<Boolean>
    val elapsedMs: StateFlow<Long>
    /** Nivel de entrada 0..1 para el indicador visual mientras grabás. */
    val amplitude: StateFlow<Float>

    /** Empieza a grabar en [path]. Devuelve false si el micrófono no pudo abrirse. */
    fun start(path: String): Boolean

    /** Detiene y devuelve la duración final en ms (0 si no se grabó nada). */
    suspend fun stop(): Long

    /** Detiene y descarta el archivo (el usuario canceló). */
    suspend fun cancel(path: String)
}

/** Descarga/instala/borra los modelos de Whisper en almacenamiento interno. */
interface WhisperModelStore {
    fun installed(): WhisperModel?
    fun isInstalled(model: WhisperModel): Boolean

    /** Descarga [model] informando avance (0..1). Lanza si falla o el checksum no coincide. */
    suspend fun download(model: WhisperModel, onProgress: (Float) -> Unit)

    fun delete(model: WhisperModel)
}

/** Transcribe una nota de voz a texto, en el dispositivo. */
interface Transcriber {
    /** Transcribe el WAV en [wavPath]; null si no hay modelo, no cargó, o la inferencia falló. */
    fun transcribe(wavPath: String, language: TranscriptionLanguage): String?

    /** Libera el modelo cargado en memoria. */
    fun release()
}

/** Estado de la descarga del modelo de transcripción, para la sección de Settings. */
sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState

    /** `progress` 1.0 significa que ya bajó todo y está verificando el checksum. */
    data class Downloading(val model: WhisperModel, val progress: Float) : ModelDownloadState

    data class Failed(val message: String) : ModelDownloadState
}
