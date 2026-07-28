package com.gmoqa.diariogamer.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import kotlin.math.pow

/**
 * Grabador de notas de voz en iOS. AVAudioRecorder captura directo a un WAV **PCM 16 kHz mono
 * 16-bit** (el mismo formato que graba Android y que consume Whisper), así que no hay que convertir
 * ni armar el header a mano. Un NSTimer va publicando tiempo y nivel para la UI.
 */
@OptIn(ExperimentalForeignApi::class)
class IosVoiceRecorder : VoiceRecorder {
    private val _recording = MutableStateFlow(false)
    override val recording: StateFlow<Boolean> = _recording.asStateFlow()
    private val _elapsedMs = MutableStateFlow(0L)
    override val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()
    private val _amplitude = MutableStateFlow(0f)
    override val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var recorder: AVAudioRecorder? = null
    private var timer: NSTimer? = null

    override fun start(path: String): Boolean {
        if (_recording.value) return false

        // PlayAndRecord (no solo Record) para poder reproducir la nota justo después de grabarla;
        // DefaultToSpeaker evita que la salida quede ruteada al auricular.
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            AVAudioSessionCategoryOptionDefaultToSpeaker,
            null,
        )
        session.setActive(true, null)

        val settings: Map<Any?, *> = mapOf(
            AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatLinearPCM),
            AVSampleRateKey to NSNumber(double = 16_000.0),
            AVNumberOfChannelsKey to NSNumber(int = 1),
            AVLinearPCMBitDepthKey to NSNumber(int = 16),
            AVLinearPCMIsFloatKey to NSNumber(bool = false),
            AVLinearPCMIsBigEndianKey to NSNumber(bool = false),
        )
        val rec = AVAudioRecorder(uRL = NSURL.fileURLWithPath(path), settings = settings, error = null)
            ?: return false
        rec.meteringEnabled = true
        if (!rec.record()) return false

        recorder = rec
        _elapsedMs.value = 0L
        _amplitude.value = 0f
        _recording.value = true
        timer = NSTimer.scheduledTimerWithTimeInterval(0.1, repeats = true) {
            rec.updateMeters()
            _elapsedMs.value = (rec.currentTime * 1000).toLong()
            _amplitude.value = dbToLinear(rec.peakPowerForChannel(0u))
        }
        return true
    }

    override suspend fun stop(): Long {
        if (!_recording.value) return 0L
        timer?.invalidate()
        timer = null
        recorder?.stop()
        recorder = null
        _recording.value = false
        _amplitude.value = 0f
        // Libera la sesión: corta el ducking a otras apps y limpia el ruteo antes de reproducir.
        AVAudioSession.sharedInstance()
            .setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, null)
        return _elapsedMs.value
    }

    override suspend fun cancel(path: String) {
        stop()
        NSFileManager.defaultManager.removeItemAtPath(path, null)
        _elapsedMs.value = 0L
    }

    /** Potencia pico en dB (-160..0) → nivel lineal 0..1 para el indicador. */
    private fun dbToLinear(db: Float): Float =
        if (db < -60f) 0f else (10f.pow(db / 20f)).coerceIn(0f, 1f)
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
