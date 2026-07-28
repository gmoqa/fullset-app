package com.gmoqa.diariogamer.ui

// Stub: iOS reproducirá las notas con AVAudioPlayer en la Fase 5. Por ahora no suena.
actual class AudioClip actual constructor(path: String) {
    actual fun setOnCompletion(callback: () -> Unit) {}
    actual fun prepare() {}
    actual val isPlaying: Boolean get() = false
    actual fun start() {}
    actual fun pause() {}
    actual fun release() {}
}
