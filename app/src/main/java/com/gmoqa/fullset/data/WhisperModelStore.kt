package com.gmoqa.fullset.data

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Descarga y guarda los modelos de Whisper en almacenamiento interno (`files/models/`).
 *
 * Escribe primero a un `.part` y solo lo renombra si el **sha256 coincide**: así nunca queda un
 * modelo a medias o corrupto que después haga fallar la transcripción. Si se cancela o falla,
 * el parcial se borra.
 *
 * Implementación Android de la frontera común [WhisperModelStore]. Usa Ktor (no HttpURLConnection);
 * solo el Context es de Android.
 */
class AndroidWhisperModelStore(context: Context) : WhisperModelStore {

    private val appContext = context.applicationContext

    private val modelsDir: File by lazy {
        File(appContext.filesDir, "models").apply { if (!exists()) mkdirs() }
    }

    private val client by lazy { HttpClient(OkHttp) }

    fun fileFor(model: WhisperModel): File = File(modelsDir, model.fileName)

    /** Instalado = el archivo existe y pesa exactamente lo esperado. */
    override fun isInstalled(model: WhisperModel): Boolean =
        fileFor(model).let { it.exists() && it.length() == model.sizeBytes }

    /** El modelo instalado, si hay alguno. */
    override fun installed(): WhisperModel? = WhisperModel.entries.firstOrNull { isInstalled(it) }

    override fun delete(model: WhisperModel) {
        runCatching { fileFor(model).delete() }
    }

    /**
     * Descarga [model] informando el avance (0..1) y verifica su integridad.
     * Lanza si la descarga falla o el checksum no coincide; el llamador decide cómo mostrarlo.
     */
    override suspend fun download(model: WhisperModel, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            if (isInstalled(model)) return@withContext

            val target = fileFor(model)
            val part = File(modelsDir, "${model.fileName}.part")
            runCatching { part.delete() }

            try {
                client.prepareGet(model.url) {
                    onDownload { received, total ->
                        val expected = total ?: model.sizeBytes
                        if (expected > 0) {
                            onProgress((received.toFloat() / expected).coerceIn(0f, 1f))
                        }
                    }
                }.execute { response ->
                    part.outputStream().use { out -> response.bodyAsChannel().copyTo(out) }
                }

                if (!sha256Of(part).equals(model.sha256, ignoreCase = true)) {
                    error("The downloaded file is corrupt (checksum mismatch)")
                }
                runCatching { target.delete() }
                if (!part.renameTo(target)) error("Could not save the model file")
            } catch (e: Throwable) {
                runCatching { part.delete() } // cancelado o fallido: no dejamos basura
                throw e
            }
        }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
