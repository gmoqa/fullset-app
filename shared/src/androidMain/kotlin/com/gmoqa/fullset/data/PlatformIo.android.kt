package com.gmoqa.fullset.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

actual class PlatformImage(val uri: Uri) {
    // Coil pinta un Uri directamente para la vista previa.
    actual val model: Any? get() = uri
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual object FileStore {
    private fun dir(name: String): File =
        File(AndroidApp.context.filesDir, name).apply { if (!exists()) mkdirs() }

    actual val photosDir: String get() = dir("photos").absolutePath
    actual val coversDir: String get() = dir("covers").absolutePath
    actual val audioDir: String get() = dir("audio").absolutePath

    actual fun exists(path: String): Boolean = File(path).exists()

    actual fun delete(path: String) {
        runCatching { File(path).delete() }
    }

    actual fun listFilePaths(dir: String): List<String> =
        File(dir).listFiles()?.map { it.absolutePath } ?: emptyList()

    /**
     * Decodifica la imagen elegida, la achica a [maxEdge] de lado largo, endereza según su EXIF y la
     * guarda como JPEG. Antes se copiaba byte a byte, así que una foto de cámara entraba con sus
     * ~3,5 MB y 4000px (ver [IMAGE_MAX_EDGE]).
     *
     * Se decodifica en dos pasos —primero solo los bordes, después con `inSampleSize`— para no cargar
     * nunca la imagen entera en memoria: un JPEG de 12 MP son ~48 MB de bitmap, suficiente para
     * tumbar la app en un teléfono con poca RAM.
     */
    actual fun copyImage(source: PlatformImage, destPath: String, maxEdge: Int): Boolean = runCatching {
        val resolver = AndroidApp.context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        // `inSampleSize` solo admite potencias de 2, así que deja la imagen entre 1x y 2x del
        // objetivo; el escalado fino lo hace el paso siguiente.
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        val decoded = resolver.openInputStream(source.uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) } ?: return false

        val scaled = scaleToFit(decoded, maxEdge)
        val upright = applyExifRotation(resolver, source.uri, scaled)

        File(destPath).outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, it) }
        upright.recycle()
        true
    }.getOrDefault(false)

    /** Escala para que el lado largo sea [maxEdge]. Si ya es más chica, la deja como está. */
    private fun scaleToFit(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /**
     * Aplica la rotación que declara el EXIF y la descarta del archivo nuevo.
     *
     * Sin esto, una foto sacada en vertical se guarda apaisada con una etiqueta que dice "rotame":
     * la cámara la muestra bien y nuestra tarjeta la muestra acostada. Al reescribir el JPEG el EXIF
     * se pierde, así que la rotación tiene que quedar horneada en los píxeles.
     */
    private fun applyExifRotation(
        resolver: android.content.ContentResolver,
        uri: Uri,
        bitmap: Bitmap,
    ): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
