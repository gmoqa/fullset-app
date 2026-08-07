package com.gmoqa.fullset.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.readRawBytes
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import whispercpp.fullset_whisper_free
import whispercpp.fullset_whisper_init
import whispercpp.fullset_whisper_n_segments
import whispercpp.fullset_whisper_segment_text
import whispercpp.fullset_whisper_transcribe

/**
 * Descarga/instala los modelos de Whisper en Documents/models y verifica el sha256, como Android.
 * Usa Ktor (Darwin). Descarga el modelo entero a memoria y lo escribe: alcanza para los tamaños
 * actuales (59–190 MB); si hiciera falta, se puede streamear a archivo.
 */
@OptIn(ExperimentalForeignApi::class)
class IosWhisperModelStore : WhisperModelStore {
    private val fm = NSFileManager.defaultManager

    private val modelsDir: String by lazy {
        val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: ""
        "$docs/models".also { fm.createDirectoryAtPath(it, true, null, null) }
    }

    private val client by lazy { HttpClient(Darwin) }

    fun fileFor(model: WhisperModel): String = "$modelsDir/${model.fileName}"

    /** Instalado = el archivo existe y pesa exactamente lo esperado. */
    override fun isInstalled(model: WhisperModel): Boolean {
        val attrs = fm.attributesOfItemAtPath(fileFor(model), null) ?: return false
        val size = (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: return false
        return size == model.sizeBytes
    }

    override fun installed(): WhisperModel? = WhisperModel.entries.firstOrNull { isInstalled(it) }

    override fun delete(model: WhisperModel) {
        fm.removeItemAtPath(fileFor(model), null)
    }

    override suspend fun download(model: WhisperModel, onProgress: (Float) -> Unit) =
        withContext(ioDispatcher) {
            if (isInstalled(model)) return@withContext
            val target = fileFor(model)
            val part = "$target.part"
            fm.removeItemAtPath(part, null)

            // Escribe primero a `.part` y solo renombra si el sha256 coincide: nunca queda un modelo
            // corrupto que después haga fallar la transcripción.
            val bytes = client.prepareGet(model.url) {
                onDownload { received, total ->
                    val expected = total ?: model.sizeBytes
                    if (expected > 0) onProgress((received.toFloat() / expected).coerceIn(0f, 1f))
                }
            }.execute { it.readRawBytes() }

            if (sha256Hex(bytes) != model.sha256.lowercase()) {
                error("The downloaded file is corrupt (checksum mismatch)")
            }
            writeBytes(bytes, part)
            fm.removeItemAtPath(target, null)
            if (!fm.moveItemAtPath(part, target, null)) error("Could not save the model file")
        }

    private fun writeBytes(bytes: ByteArray, path: String) {
        if (bytes.isEmpty()) return
        val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        data.writeToFile(path, atomically = true)
    }
}

/**
 * Transcribe notas de voz con whisper.cpp en el dispositivo, vía el shim C (`whispercpp` cinterop).
 * Mantiene el modelo cargado entre notas; [release] lo libera.
 */
@OptIn(ExperimentalForeignApi::class)
class IosTranscriber(private val store: IosWhisperModelStore) : Transcriber {
    private var ctx: COpaquePointer? = null
    private var loadedModel: WhisperModel? = null

    override fun transcribe(wavPath: String, language: TranscriptionLanguage): String? {
        val model = store.installed() ?: return null
        if (!ensureLoaded(model)) return null

        val audio = readWavAsFloats(wavPath)
        if (audio.isEmpty()) return null

        // Dejamos un par de núcleos libres para que la UI siga fluida.
        val threads = (NSProcessInfo.processInfo.activeProcessorCount.toInt() - 2).coerceIn(2, 6)
        val code = audio.usePinned { pinned ->
            fullset_whisper_transcribe(ctx, pinned.addressOf(0), audio.size, language.code, threads)
        }
        if (code != 0) return null

        val segments = fullset_whisper_n_segments(ctx)
        return buildString {
            for (i in 0 until segments) append(fullset_whisper_segment_text(ctx, i)?.toKString() ?: "")
        }.trim()
    }

    private fun ensureLoaded(model: WhisperModel): Boolean {
        if (ctx != null && loadedModel == model) return true
        free()
        ctx = fullset_whisper_init(store.fileFor(model))
        if (ctx == null) return false
        loadedModel = model
        return true
    }

    override fun release() = free()

    private fun free() {
        ctx?.let { fullset_whisper_free(it) }
        ctx = null
        loadedModel = null
    }

    /**
     * WAV PCM16 mono → floats en -1..1 (lo que consume Whisper). Se saltea el header de 44 bytes que
     * escribe [IosVoiceRecorder]/AVAudioRecorder.
     */
    private fun readWavAsFloats(path: String): FloatArray {
        val data = NSData.dataWithContentsOfFile(path) ?: return FloatArray(0)
        val len = data.length.toInt()
        if (len <= WAV_HEADER) return FloatArray(0)
        val p = data.bytes!!.reinterpret<ByteVar>()
        val n = (len - WAV_HEADER) / 2
        val out = FloatArray(n)
        var off = WAV_HEADER
        for (i in 0 until n) {
            val s = ((p[off + 1].toInt() shl 8) or (p[off].toInt() and 0xFF)).toShort()
            out[i] = s / 32768f
            off += 2
        }
        return out
    }

    private companion object {
        const val WAV_HEADER = 44
    }
}

// ---------- SHA-256 en Kotlin puro (para verificar la integridad del modelo) ----------

private val SHA256_K = longArrayOf(
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
).map { it.toInt() }.toIntArray()

private fun sha256Hex(msg: ByteArray): String {
    var h0 = 0x6a09e667; var h1 = 0xbb67ae85.toInt(); var h2 = 0x3c6ef372; var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f; var h5 = 0x9b05688c.toInt(); var h6 = 0x1f83d9ab; var h7 = 0x5be0cd19

    val ml = msg.size.toLong() * 8
    // padding: 0x80, ceros, y el largo en 64 bits big-endian.
    val padLen = ((56 - (msg.size + 1) % 64) + 64) % 64
    val total = msg.size + 1 + padLen + 8
    val m = ByteArray(total)
    msg.copyInto(m)
    m[msg.size] = 0x80.toByte()
    for (i in 0 until 8) m[total - 1 - i] = (ml ushr (8 * i)).toByte()

    val w = IntArray(64)
    var block = 0
    while (block < total) {
        for (t in 0 until 16) {
            val j = block + t * 4
            w[t] = ((m[j].toInt() and 0xFF) shl 24) or ((m[j + 1].toInt() and 0xFF) shl 16) or
                ((m[j + 2].toInt() and 0xFF) shl 8) or (m[j + 3].toInt() and 0xFF)
        }
        for (t in 16 until 64) {
            val s0 = w[t - 15].rotr(7) xor w[t - 15].rotr(18) xor (w[t - 15] ushr 3)
            val s1 = w[t - 2].rotr(17) xor w[t - 2].rotr(19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }
        var a = h0; var b = h1; var c = h2; var d = h3; var e = h4; var f = h5; var g = h6; var hh = h7
        for (t in 0 until 64) {
            val s1 = e.rotr(6) xor e.rotr(11) xor e.rotr(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + SHA256_K[t] + w[t]
            val s0 = a.rotr(2) xor a.rotr(13) xor a.rotr(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
        }
        h0 += a; h1 += b; h2 += c; h3 += d; h4 += e; h5 += f; h6 += g; h7 += hh
        block += 64
    }
    return intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).joinToString("") { it.toHex8() }
}

private fun Int.rotr(n: Int): Int = (this ushr n) or (this shl (32 - n))
private fun Int.toHex8(): String {
    val hex = "0123456789abcdef"
    val sb = StringBuilder(8)
    for (i in 7 downTo 0) sb.append(hex[(this ushr (i * 4)) and 0xF])
    return sb.toString()
}
