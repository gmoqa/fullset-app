package com.gmoqa.fullset.data

/**
 * Idioma en el que dictás las notas de voz. Se le pasa a Whisper como código ISO; "auto" deja que
 * lo detecte solo (un poco más lento y a veces se equivoca en notas cortas, por eso el default es
 * fijar el idioma).
 */
enum class TranscriptionLanguage(val code: String, val label: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English"),
    AUTO("auto", "Auto"),
    ;

    companion object {
        val DEFAULT = SPANISH

        fun fromCode(code: String?): TranscriptionLanguage =
            entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}
