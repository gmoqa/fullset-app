package com.gmoqa.diariogamer.data

/**
 * Puente JNI con whisper.cpp (`libwhisper_jni.so`).
 *
 * El nombre de la clase, el paquete y las firmas tienen que coincidir **exactamente** con las
 * funciones declaradas en `src/main/cpp/whisper_jni.c`; si se renombra algo acá, hay que renombrarlo
 * allá. Es la única pieza atada a Android: en iOS whisper.cpp se enlazaría directo desde Swift.
 */
internal class WhisperLib {
    companion object {
        /** Si el .so no está (build sin NDK), las llamadas fallan y el llamador lo reporta. */
        val available: Boolean = runCatching { System.loadLibrary("whisper_jni") }.isSuccess

        /** Devuelve un puntero al contexto, o 0 si el modelo no pudo cargarse. */
        external fun initContext(modelPath: String): Long

        external fun freeContext(contextPtr: Long)

        /** 0 = ok. `language` es ISO ("es", "en"…) o "auto". */
        external fun fullTranscribe(
            contextPtr: Long,
            numThreads: Int,
            audioData: FloatArray,
            language: String,
        ): Int

        external fun getTextSegmentCount(contextPtr: Long): Int

        external fun getTextSegment(contextPtr: Long, index: Int): String
    }
}
