package com.gmoqa.diariogamer.data

/**
 * Modelos de Whisper (whisper.cpp, formato ggml cuantizado q5_1) que se pueden bajar desde Settings.
 *
 * Son los **multilingües** a propósito (no los `.en`): las notas se dictan en español. `sizeBytes` y
 * `sha256` salen del repo oficial en HuggingFace y se usan para verificar que la descarga quedó
 * íntegra antes de dar el modelo por instalado.
 *
 * KMP-ready: acá no hay nada de Android; solo metadatos.
 */
enum class WhisperModel(
    val key: String,
    val label: String,
    val detail: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    BASE(
        key = "base",
        label = "Base",
        detail = "Faster, good for short notes",
        fileName = "ggml-base-q5_1.bin",
        sizeBytes = 59_707_625,
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
    ),
    SMALL(
        key = "small",
        label = "Small",
        detail = "Better accuracy, slower",
        fileName = "ggml-small-q5_1.bin",
        sizeBytes = 190_085_487,
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
    );

    /** Tamaño legible para la UI, p. ej. "57 MB". */
    val sizeLabel: String get() = "${sizeBytes / 1_000_000} MB"

    val url: String get() = "$BASE_URL/$fileName"

    companion object {
        private const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

        fun fromKey(key: String?): WhisperModel? = entries.firstOrNull { it.key == key }
    }
}
