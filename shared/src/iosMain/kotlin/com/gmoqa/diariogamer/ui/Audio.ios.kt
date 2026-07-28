package com.gmoqa.diariogamer.ui

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSURL
import platform.darwin.NSObject

/**
 * Reproductor de notas de voz en iOS con AVAudioPlayer. Los WAV los graba [AndroidVoiceRecorder]
 * en Android; en iOS los grabará AVAudioEngine (misma frontera [VoiceRecorder]).
 */
actual class AudioClip actual constructor(private val path: String) {
    private var player: AVAudioPlayer? = null
    private var delegate: CompletionDelegate? = null

    actual fun setOnCompletion(callback: () -> Unit) {
        delegate = CompletionDelegate(callback)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun prepare() {
        // Asegura salida por parlante aunque venga de una sesión de grabación (Record es solo-entrada).
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        AVAudioSession.sharedInstance().setActive(true, null)
        val url = NSURL.fileURLWithPath(path)
        val p = AVAudioPlayer(contentsOfURL = url, error = null)
        p.setDelegate(delegate)
        p.prepareToPlay()
        player = p
    }

    actual val isPlaying: Boolean get() = player?.playing ?: false
    actual fun start() { player?.play() }
    actual fun pause() { player?.pause() }
    actual fun release() {
        player?.stop()
        player = null
        delegate = null
    }
}

/** Avisa cuando termina la reproducción, para que la UI vuelva el ícono a "play". */
private class CompletionDelegate(
    private val onDone: () -> Unit,
) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        onDone()
    }
}
