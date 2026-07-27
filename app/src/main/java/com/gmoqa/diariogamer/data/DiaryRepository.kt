package com.gmoqa.diariogamer.data

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.gmoqa.diariogamer.db.FullsetDatabase
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Acceso a datos (CRUD) sobre SQLDelight (multiplataforma) + manejo de archivos de foto/carátula.
 * La siembra inicial y los ajustes puntuales de FF7 viven en [DiarySeeder], lanzados por el
 * [DiaryViewModel] fuera del hilo principal vía [seed] (antes corrían en el constructor).
 *
 * Dos estilos de lectura:
 *  - `*Flow()` → reactivas ([Flow]): la UI se refresca sola cuando cambia la tabla. Lo normal.
 *  - síncronas (`games()`, `game()`…) → una sola lectura; las usan la siembra y el export CSV.
 * Las escrituras son síncronas; el ViewModel las corre en [Dispatchers.IO].
 *
 * KMP-ready: la BD es SQLDelight (en iOS el driver sería NativeSqliteDriver, expect/actual).
 */
class DiaryRepository(context: Context) {

    private val appContext = context.applicationContext

    private val driver: SqlDriver = AndroidSqliteDriver(
        schema = FullsetDatabase.Schema,
        context = appContext,
        name = "diario_gamer.db",
        callback = object : AndroidSqliteDriver.Callback(FullsetDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true) // conserva ON DELETE CASCADE
            }
        },
    )

    // `internal` para que [DiarySeeder] (mismo módulo) reutilice la BD durante la siembra.
    internal val database = FullsetDatabase(driver)
    private val q get() = database.fullsetQueries

    /** Carpeta interna donde se copian las fotos. */
    internal val photosDir: File by lazy {
        File(appContext.filesDir, "photos").apply { if (!exists()) mkdirs() }
    }

    /** Carpeta interna para carátulas personalizadas. */
    internal val coversDir: File by lazy {
        File(appContext.filesDir, "covers").apply { if (!exists()) mkdirs() }
    }

    /** Carpeta interna con los WAV de las notas de voz. */
    internal val audioDir: File by lazy {
        File(appContext.filesDir, "audio").apply { if (!exists()) mkdirs() }
    }

    /** Archivo destino para una nueva nota de voz (aún no insertada en la BD). */
    fun newVoiceNoteFile(gameId: Long): File =
        File(audioDir, "note_${gameId}_${System.currentTimeMillis()}.wav")

    // multiplatform-settings (KMP-ready): en iOS será NSUserDefaultsSettings vía expect/actual.
    private val settings: Settings = SharedPreferencesSettings(
        appContext.getSharedPreferences("diario_gamer_prefs", Context.MODE_PRIVATE)
    )

    /** Siembra + migraciones puntuales, fuera del hilo principal. Idempotente (banderas en prefs). */
    suspend fun seed() = withContext(Dispatchers.IO) {
        DiarySeeder(appContext, this@DiaryRepository, settings).run()
        pruneOrphanAudio()
    }

    /**
     * Borra los WAV que quedaron sin nota asociada: pasa si el proceso muere mientras se graba
     * (el archivo ya existe pero la fila todavía no se insertó). Corre en cada arranque.
     */
    private fun pruneOrphanAudio() {
        val referenced = q.selectAllNoteAudioPaths().executeAsList().toSet()
        audioDir.listFiles()?.forEach { file ->
            if (file.absolutePath !in referenced) runCatching { file.delete() }
        }
    }

    // ---------------------------------------------------------------- Juegos

    /** Lista reactiva de todos los juegos: re-emite ante cualquier alta/baja/cambio en `games`. */
    fun gamesFlow(): Flow<List<Game>> =
        q.selectAllGames(::mapGame).asFlow().mapToList(Dispatchers.IO)

    /** Un juego reactivo (o null si se borra). */
    fun gameFlow(id: Long): Flow<Game?> =
        q.selectGameById(id, ::mapGame).asFlow().mapToOneOrNull(Dispatchers.IO)

    fun games(): List<Game> = q.selectAllGames(::mapGame).executeAsList()

    private fun mapGame(
        id: Long,
        name: String,
        platform: String,
        coverUrl: String,
        coverPath: String,
        playing: Long,
        backlog: Long,
        createdAt: Long,
        region: String,
        releaseYear: Long?,
        genre: String,
        condition: String,
        slug: String,
        publisher: String,
        serial: String,
        digital: Long,
        noteCount: Long,
        photoCount: Long,
    ): Game = Game(
        id = id,
        name = name,
        platform = platform,
        coverUrl = coverUrl,
        coverPath = coverPath,
        playing = playing != 0L,
        backlog = backlog != 0L,
        createdAt = createdAt,
        region = region,
        releaseYear = releaseYear?.toInt(),
        genre = genre,
        condition = condition,
        slug = slug,
        publisher = publisher,
        serial = serial,
        digital = digital != 0L,
        noteCount = noteCount.toInt(),
        photoCount = photoCount.toInt(),
    )

    fun addGame(
        name: String,
        platform: String,
        coverUrl: String = "",
        createdAt: Long = System.currentTimeMillis(),
        region: String = "",
        releaseYear: Int? = null,
        genre: String = "",
        condition: String = "",
        slug: String = "",
        publisher: String = "",
        serial: String = "",
        digital: Boolean = false,
    ): Long = database.transactionWithResult {
        q.insertGame(
            name.trim(), platform.trim(), coverUrl.trim(), createdAt,
            region.trim(), releaseYear?.toLong(), genre.trim(), condition.trim(),
            slug.trim(), publisher.trim(), serial.trim(), if (digital) 1L else 0L,
        )
        q.lastInsertRowId().executeAsOne()
    }

    /** Asocia un juego con su entrada del catálogo (slug + editora, y el año si faltaba). */
    fun linkCatalog(
        name: String,
        platform: String,
        slug: String,
        publisher: String,
        serial: String,
        year: Int?,
    ) = q.linkCatalogByName(slug.trim(), publisher.trim(), serial.trim(), year?.toLong(), name, platform)

    /** Rellena los metadatos normalizados de un juego por nombre (usado por la siembra). */
    fun setMetadataByName(name: String, region: String, releaseYear: Int?, genre: String, condition: String) =
        q.updateMetadataByName(region.trim(), releaseYear?.toLong(), genre.trim(), condition.trim(), name)

    /** Marca o desmarca "jugando ahora". */
    fun setPlaying(gameId: Long, playing: Boolean) = q.setPlaying(if (playing) 1L else 0L, gameId)

    /** Marca o desmarca "backlog" (pendiente por jugar). */
    fun setBacklog(gameId: Long, backlog: Boolean) = q.setBacklog(if (backlog) 1L else 0L, gameId)

    /** Borra el juego, sus notas y sus fotos (filas por CASCADE + archivos en disco). */
    fun deleteGame(id: Long) {
        photos(id).forEach { runCatching { File(it.path).delete() } }
        // Las filas se borran por CASCADE, pero los archivos en disco hay que limpiarlos a mano.
        notes(id).filter { it.isVoice }.forEach { runCatching { File(it.audioPath).delete() } }
        currentCoverPath(id)?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        q.deleteGame(id)
    }

    // ------------------------------------------------------------- Carátulas

    /** Fija una carátula personalizada copiando la imagen elegida a almacenamiento interno. */
    fun setCoverFromUri(gameId: Long, sourceUri: Uri): Boolean {
        val dest = File(coversDir, "cover_${gameId}_${System.currentTimeMillis()}.jpg")
        val ok = runCatching {
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
        }.isSuccess
        if (!ok) return false

        currentCoverPath(gameId)?.takeIf { it.isNotBlank() && it != dest.absolutePath }
            ?.let { runCatching { File(it).delete() } }
        q.updateCoverPath(dest.absolutePath, gameId)
        return true
    }

    /** Quita la carátula personalizada (vuelve a la automática). */
    fun clearCustomCover(gameId: Long) {
        currentCoverPath(gameId)?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        q.updateCoverPath("", gameId)
    }

    private fun currentCoverPath(gameId: Long): String? =
        q.selectCoverPath(gameId).executeAsOneOrNull()

    // ----------------------------------------------------------------- Notas

    private fun mapNote(
        id: Long,
        gameId: Long,
        text: String,
        createdAt: Long,
        audioPath: String,
        durationMs: Long,
    ): Note = Note(id, gameId, text, createdAt, audioPath, durationMs)

    /** Notas reactivas de un juego (re-emiten al agregar/borrar y al llegar la transcripción). */
    fun notesFlow(gameId: Long): Flow<List<Note>> =
        q.selectNotes(gameId, ::mapNote).asFlow().mapToList(Dispatchers.IO)

    fun notes(gameId: Long): List<Note> = q.selectNotes(gameId, ::mapNote).executeAsList()

    fun addNote(
        gameId: Long,
        text: String,
        createdAt: Long = System.currentTimeMillis(),
        audioPath: String = "",
        durationMs: Long = 0,
    ): Long = database.transactionWithResult {
        q.insertNote(gameId, text.trim(), createdAt, audioPath.trim(), durationMs)
        q.lastInsertRowId().executeAsOne()
    }

    /** Rellena la transcripción de una nota de voz (Whisper corre en segundo plano). */
    fun setNoteText(id: Long, text: String) = q.updateNoteText(text.trim(), id)

    /** Borra la nota y, si era de voz, su archivo de audio. */
    fun deleteNote(id: Long) {
        q.selectNoteAudioPath(id).executeAsOneOrNull()?.takeIf { it.isNotBlank() }
            ?.let { runCatching { File(it).delete() } }
        q.deleteNote(id)
    }

    // ----------------------------------------------------------------- Fotos

    /** Fotos reactivas de un juego (re-emiten al agregar/borrar). */
    fun photosFlow(gameId: Long): Flow<List<Photo>> =
        q.selectPhotos(gameId) { id, gid, path, caption, createdAt -> Photo(id, gid, path, caption, createdAt) }
            .asFlow().mapToList(Dispatchers.IO)

    fun photos(gameId: Long): List<Photo> =
        q.selectPhotos(gameId) { id, gid, path, caption, createdAt -> Photo(id, gid, path, caption, createdAt) }
            .executeAsList()

    /**
     * Copia la imagen apuntada por [sourceUri] (del Photo Picker) a almacenamiento interno
     * y guarda la ruta. Devuelve la fila creada, o null si la copia falla.
     */
    fun addPhoto(gameId: Long, sourceUri: Uri, caption: String = ""): Photo? {
        val now = System.currentTimeMillis()
        val dest = File(photosDir, "photo_${gameId}_$now.jpg")
        val copied = runCatching {
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
        }
        if (copied.isFailure) return null

        val id = database.transactionWithResult {
            q.insertPhoto(gameId, dest.absolutePath, caption.trim(), now)
            q.lastInsertRowId().executeAsOne()
        }
        return Photo(id, gameId, dest.absolutePath, caption.trim(), now)
    }

    fun deletePhoto(id: Long) {
        q.selectPhotoPath(id).executeAsOneOrNull()?.let { runCatching { File(it).delete() } }
        q.deletePhoto(id)
    }

    // -------------------------------------------------------------- Wishlist

    /** Wishlist reactiva (re-emite al agregar/quitar/vaciar). */
    fun wishlistFlow(): Flow<List<WishlistItem>> =
        q.selectWishlist { id, platform, game, slug, coverUrl, addedAt ->
            WishlistItem(id, platform, game, slug, coverUrl, addedAt)
        }.asFlow().mapToList(Dispatchers.IO)

    /** Agrega a la wishlist (INSERT OR IGNORE: dedup por plataforma+juego). */
    fun addToWishlist(
        platform: String,
        game: String,
        slug: String,
        coverUrl: String,
        addedAt: Long = System.currentTimeMillis(),
    ) = q.insertWishlist(platform.trim(), game.trim(), slug.trim(), coverUrl.trim(), addedAt)

    fun removeFromWishlist(id: Long) = q.deleteWishlist(id)

    fun clearWishlist() = q.clearWishlist()

    // -------------------------------------------------------------- Ajustes

    fun themeMode(): ThemeMode = ThemeMode.fromKey(settings.getStringOrNull(THEME_KEY))

    fun setThemeMode(mode: ThemeMode) {
        settings.putString(THEME_KEY, mode.key)
    }

    fun regionFilter(): RegionFilter = RegionFilter.fromKey(settings.getStringOrNull(REGION_KEY))

    fun setRegionFilter(region: RegionFilter) {
        settings.putString(REGION_KEY, region.key)
    }

    /** Idioma en el que se dictan las notas de voz (se le pasa a Whisper). */
    fun transcriptionLanguage(): TranscriptionLanguage =
        TranscriptionLanguage.fromCode(settings.getStringOrNull(LANGUAGE_KEY))

    fun setTranscriptionLanguage(language: TranscriptionLanguage) {
        settings.putString(LANGUAGE_KEY, language.code)
    }

    companion object {
        private const val THEME_KEY = "theme_mode"
        private const val REGION_KEY = "region_filter"
        private const val LANGUAGE_KEY = "transcription_language"
    }
}
