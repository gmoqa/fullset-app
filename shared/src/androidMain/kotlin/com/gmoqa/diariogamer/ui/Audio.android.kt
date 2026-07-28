package com.gmoqa.diariogamer.ui

import android.media.MediaPlayer

actual class AudioClip actual constructor(private val path: String) {
    private val player = MediaPlayer()

    actual fun setOnCompletion(callback: () -> Unit) {
        player.setOnCompletionListener { callback() }
    }

    actual fun prepare() {
        player.setDataSource(path)
        player.prepare()
    }

    actual val isPlaying: Boolean get() = player.isPlaying
    actual fun start() = player.start()
    actual fun pause() = player.pause()
    actual fun release() = player.release()
}
