package com.gmoqa.diariogamer.data

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Transcribe notas de voz con whisper.cpp, **en el dispositivo** (nada sale a la red).
 *
 * Mantiene el modelo cargado entre notas porque abrirlo cuesta bastante; [release] lo libera.
 * Las llamadas están sincronizadas: whisper_full no es reentrante sobre el mismo contexto.
 */
class WhisperTranscriber(private val store: WhisperModelStore) {

    private var contextPtr = 0L
    private var loadedModel: WhisperModel? = null

    /**
     * Transcribe [wav] (PCM 16 kHz mono, tal como lo graba [VoiceRecorder]).
     * Devuelve null si no hay modelo instalado, el .so no cargó, o la inferencia falló.
     */
    @Synchronized
    fun transcribe(wav: File, language: TranscriptionLanguage): String? {
        if (!WhisperLib.available) return null
        val model = store.installed() ?: return null
        if (!ensureLoaded(model)) return null

        val audio = readWavAsFloats(wav)
        if (audio.isEmpty()) return null

        // Dejamos un par de núcleos libres para que la UI siga fluida mientras transcribe.
        val threads = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
        val startedAt = System.currentTimeMillis()
        val code = runCatching {
            WhisperLib.fullTranscribe(contextPtr, threads, audio, language.code)
        }.getOrElse { return null }

        // Queda en logcat para poder comparar rendimiento sin adivinar (audio vs. tiempo real).
        val audioSeconds = audio.size / VoiceRecorder.SAMPLE_RATE.toFloat()
        val elapsedSeconds = (System.currentTimeMillis() - startedAt) / 1000f
        Log.i(
            TAG,
            "transcribed %.1fs of audio in %.1fs (%.1fx) with %d threads, lang=%s"
                .format(audioSeconds, elapsedSeconds, elapsedSeconds / audioSeconds, threads, language.code),
        )
        if (code != 0) return null

        return runCatching {
            buildString {
                val segments = WhisperLib.getTextSegmentCount(contextPtr)
                for (i in 0 until segments) append(WhisperLib.getTextSegment(contextPtr, i))
            }.trim()
        }.getOrNull()
    }

    private fun ensureLoaded(model: WhisperModel): Boolean {
        if (contextPtr != 0L && loadedModel == model) return true
        freeContext()
        val ptr = runCatching {
            WhisperLib.initContext(store.fileFor(model).absolutePath)
        }.getOrDefault(0L)
        if (ptr == 0L) return false
        contextPtr = ptr
        loadedModel = model
        return true
    }

    /** Libera el modelo de memoria (p. ej. al borrarlo o al cerrar la app). */
    @Synchronized
    fun release() = freeContext()

    private fun freeContext() {
        if (contextPtr != 0L) {
            runCatching { WhisperLib.freeContext(contextPtr) }
            contextPtr = 0L
            loadedModel = null
        }
    }

    /**
     * WAV PCM16 mono → floats en -1..1, que es lo que consume Whisper. Se saltea el header de
     * 44 bytes que escribe [VoiceRecorder].
     */
    private fun readWavAsFloats(file: File): FloatArray {
        val bytes = runCatching { file.readBytes() }.getOrElse { return FloatArray(0) }
        if (bytes.size <= WAV_HEADER_SIZE) return FloatArray(0)

        val samples = ByteBuffer
            .wrap(bytes, WAV_HEADER_SIZE, bytes.size - WAV_HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val out = FloatArray(samples.remaining())
        var i = 0
        while (samples.hasRemaining()) {
            out[i++] = samples.get() / 32768f
        }
        return out
    }

    private companion object {
        const val WAV_HEADER_SIZE = 44
        const val TAG = "WhisperTranscriber"
    }
}
