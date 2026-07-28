package com.gmoqa.diariogamer.ui

/**
 * Reproductor de un clip de audio local (las notas de voz WAV). Frontera de plataforma: en Android
 * envuelve MediaPlayer; en iOS es un stub por ahora (AVAudioPlayer llega en la Fase 5).
 */
expect class AudioClip(path: String) {
    /** Registra un callback para cuando el clip termina de reproducirse. */
    fun setOnCompletion(callback: () -> Unit)

    /** Prepara la fuente (abre el archivo). Llamar una vez antes de [start]. */
    fun prepare()

    val isPlaying: Boolean
    fun start()
    fun pause()
    fun release()
}
