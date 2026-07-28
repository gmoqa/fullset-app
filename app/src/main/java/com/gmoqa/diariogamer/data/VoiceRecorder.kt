package com.gmoqa.diariogamer.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Grabador de notas de voz. Captura **PCM 16 kHz mono 16-bit**, que es exactamente lo que consume
 * Whisper: así no hay que transcodificar antes de transcribir. Escribe un WAV (header + PCM) para
 * poder reproducir la nota después.
 *
 * Implementación Android de la frontera común [VoiceRecorder]; en iOS el equivalente sería
 * AVAudioEngine escribiendo el mismo WAV (Fase 5).
 */
class AndroidVoiceRecorder : VoiceRecorder {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recorder: AudioRecord? = null
    private var job: Job? = null

    @Volatile
    private var stopRequested = false

    private val _recording = MutableStateFlow(false)
    override val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    override val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    /** Nivel de entrada 0..1, para el indicador visual mientras grabás. */
    private val _amplitude = MutableStateFlow(0f)
    override val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    /**
     * Empieza a grabar en [path]. El llamador debe tener concedido RECORD_AUDIO.
     * Devuelve false si el micrófono no pudo inicializarse.
     */
    @SuppressLint("MissingPermission")
    override fun start(path: String): Boolean {
        if (_recording.value) return false
        val output = File(path)

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) return false
        val bufSize = maxOf(minBuf, BYTES_PER_SECOND / 4) // ~250 ms por lectura

        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, bufSize)
        }.getOrNull() ?: return false

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }

        recorder = rec
        stopRequested = false
        _elapsedMs.value = 0L
        _amplitude.value = 0f
        _recording.value = true
        rec.startRecording()

        job = scope.launch {
            var dataBytes = 0L
            val ok = runCatching {
                output.outputStream().buffered().use { out ->
                    out.write(ByteArray(HEADER_SIZE)) // hueco: el header se completa al final
                    val buf = ByteArray(bufSize)
                    while (isActive && !stopRequested) {
                        val n = rec.read(buf, 0, buf.size)
                        if (n <= 0) continue
                        out.write(buf, 0, n)
                        dataBytes += n
                        _elapsedMs.value = dataBytes * 1000 / BYTES_PER_SECOND
                        _amplitude.value = peakOf(buf, n)
                        if (_elapsedMs.value >= MAX_DURATION_MS) break
                    }
                }
                writeWavHeader(output, dataBytes)
            }.isSuccess
            if (!ok) runCatching { output.delete() }
        }
        return true
    }

    /** Detiene la grabación y devuelve la duración final en ms (0 si no se grabó nada). */
    override suspend fun stop(): Long {
        if (!_recording.value) return 0L
        stopRequested = true
        runCatching { recorder?.stop() }
        job?.join() // esperar a que termine de volcar el PCM y escribir el header
        recorder?.release()
        recorder = null
        job = null
        _recording.value = false
        _amplitude.value = 0f
        return _elapsedMs.value
    }

    /** Detiene y descarta el archivo (el usuario canceló). */
    override suspend fun cancel(path: String) {
        stop()
        runCatching { File(path).delete() }
        _elapsedMs.value = 0L
    }

    /** Amplitud pico del bloque PCM16 little-endian, normalizada a 0..1. */
    private fun peakOf(buf: ByteArray, n: Int): Float {
        var max = 0
        var i = 0
        while (i + 1 < n) {
            val sample = (((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort()).toInt()
            val a = abs(sample)
            if (a > max) max = a
            i += 2
        }
        return (max / 32768f).coerceIn(0f, 1f)
    }

    /** Header WAV canónico de 44 bytes (PCM sin comprimir), escrito una vez conocido el tamaño. */
    private fun writeWavHeader(file: File, dataBytes: Long) {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt((36 + dataBytes).toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)                       // tamaño del subchunk fmt
            putShort(1)                      // formato: PCM
            putShort(CHANNEL_COUNT.toShort())
            putInt(SAMPLE_RATE)
            putInt(BYTES_PER_SECOND)         // byte rate
            putShort((CHANNEL_COUNT * BITS_PER_SAMPLE / 8).toShort()) // block align
            putShort(BITS_PER_SAMPLE.toShort())
            put("data".toByteArray())
            putInt(dataBytes.toInt())
        }.array()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }

    companion object {
        /** 16 kHz mono: el sample rate que espera Whisper. */
        const val SAMPLE_RATE = 16_000
        const val MAX_DURATION_MS = 3 * 60 * 1000L // techo por batería y tiempo de transcripción

        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL_COUNT = 1
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNEL_COUNT * BITS_PER_SAMPLE / 8
        private const val HEADER_SIZE = 44
    }
}
