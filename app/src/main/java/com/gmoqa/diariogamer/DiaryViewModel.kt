package com.gmoqa.diariogamer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gmoqa.diariogamer.data.DiaryRepository
import com.gmoqa.diariogamer.data.Game
import com.gmoqa.diariogamer.data.Note
import com.gmoqa.diariogamer.data.ModelDownloadState
import com.gmoqa.diariogamer.data.Photo
import com.gmoqa.diariogamer.data.PlatformImage
import com.gmoqa.diariogamer.data.RegionFilter
import com.gmoqa.diariogamer.data.SteamGridDb
import com.gmoqa.diariogamer.data.SteamGridGame
import com.gmoqa.diariogamer.data.ThemeMode
import com.gmoqa.diariogamer.data.TranscriptionLanguage
import com.gmoqa.diariogamer.data.AndroidVoiceRecorder
import com.gmoqa.diariogamer.data.WhisperTranscriber
import com.gmoqa.diariogamer.data.WhisperModel
import com.gmoqa.diariogamer.data.AndroidWhisperModelStore
import com.gmoqa.diariogamer.data.WishlistItem
import com.gmoqa.diariogamer.data.collectionCsv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Única fuente de verdad de la UI. Es dueño del [DiaryRepository], expone las listas como
 * [StateFlow] reactivos (la UI se refresca sola tras cualquier cambio, sin trucos de navegación)
 * y corre siembra y escrituras **fuera del hilo principal**.
 *
 * [ready] pasa a `true` cuando termina la siembra; la UI muestra una carga breve hasta entonces
 * (relevante solo en la primera instalación; ya sembrada, la siembra es casi instantánea).
 */
class DiaryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DiaryRepository()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val games: StateFlow<List<Game>> =
        repo.gamesFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val wishlist: StateFlow<List<WishlistItem>> =
        repo.wishlistFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            repo.seed()
            _ready.value = true
        }
    }

    // ---- Lecturas reactivas por juego (recordar con remember(id) en el composable) ----

    fun gameFlow(id: Long): Flow<Game?> = repo.gameFlow(id)
    fun notesFlow(id: Long): Flow<List<Note>> = repo.notesFlow(id)
    fun photosFlow(id: Long): Flow<List<Photo>> = repo.photosFlow(id)

    // ---- Escrituras: fuera del main; los Flows se re-emiten solos ----

    /**
     * Último juego agregado. Collection lo observa para **llevar la vista hasta él** (sube a su
     * franja y corre la fila) en vez de dejarte donde estabas. Se consume una sola vez.
     */
    private val _lastAdded = MutableStateFlow<Long?>(null)
    val lastAdded: StateFlow<Long?> = _lastAdded.asStateFlow()

    fun consumeLastAdded() { _lastAdded.value = null }

    fun addGame(
        title: String,
        platform: String,
        coverUrl: String,
        region: String = "",
        releaseYear: Int? = null,
        genre: String = "",
        slug: String = "",
        publisher: String = "",
    ) = io {
        _lastAdded.value = repo.addGame(
            title, platform, coverUrl,
            region = region, releaseYear = releaseYear, genre = genre,
            slug = slug, publisher = publisher,
        )
    }

    /**
     * Agrega un juego cargado a mano (plataformas sin catálogo, como PS5). [digital] decide si es
     * físico (va a Collection) o digital (no lo poseés). La carátula puede venir de una URL elegida
     * en el buscador ([coverUrl], p. ej. SteamGridDB) o de una imagen de la galería ([coverUri]).
     * Corre entero en IO para tener el id antes de asociar la imagen local.
     */
    fun addManualGame(
        title: String,
        platform: String,
        coverUrl: String,
        coverUri: Uri?,
        digital: Boolean,
    ) = io {
        val id = repo.addGame(title, platform, coverUrl = coverUrl, digital = digital)
        if (coverUri != null) repo.setCoverFromImage(id, PlatformImage(coverUri))
        _lastAdded.value = id
    }

    /**
     * Agrega un juego **digital** (no lo poseés). Entra directo a **Playing** (`digital=true`,
     * `playing=true`) y **no** aparece en Collection, que es tu colección física. La carátula puede
     * venir del buscador ([coverUrl]) o de la galería ([coverUri]).
     */
    fun addDigitalGame(title: String, platform: String, coverUrl: String, coverUri: Uri?) = io {
        val id = repo.addGame(title, platform, coverUrl = coverUrl, digital = true)
        repo.setPlaying(id, true)
        if (coverUri != null) repo.setCoverFromImage(id, PlatformImage(coverUri))
    }

    // ---- Buscador de carátulas (SteamGridDB, para plataformas sin catálogo) ----

    // La key (Android/BuildConfig) se inyecta acá para que SteamGridDb quede sin dependencias de Android.
    private val coverSource = SteamGridDb(BuildConfig.STEAMGRIDDB_API_KEY)

    /** Hay API key configurada: si no, el formulario manual no ofrece el buscador. */
    val coverSearchEnabled: Boolean get() = coverSource.isEnabled

    /** Juegos que coinciden con un título (para elegir el correcto antes de ver sus carátulas). */
    suspend fun searchGames(title: String): List<SteamGridGame> = coverSource.searchGames(title)

    /** Carátulas (URLs 600×900) del juego elegido; vacío si no tiene o falla la red. */
    suspend fun coversFor(gameId: Int): List<String> = coverSource.coversForGame(gameId)

    fun setPlaying(id: Long, playing: Boolean) = io { repo.setPlaying(id, playing) }
    fun setBacklog(id: Long, backlog: Boolean) = io { repo.setBacklog(id, backlog) }
    fun deleteGame(id: Long) = io { repo.deleteGame(id) }

    fun addNote(gameId: Long, text: String) = io { repo.addNote(gameId, text) }
    fun deleteNote(id: Long) = io { repo.deleteNote(id) }

    // ---- Notas de voz (Fase 1: grabar y guardar; la transcripción llega después) ----

    private val recorder = AndroidVoiceRecorder()

    /** Id del juego que se está grabando ahora, o null si no hay grabación en curso. */
    private val _recordingFor = MutableStateFlow<Long?>(null)
    val recordingFor: StateFlow<Long?> = _recordingFor.asStateFlow()

    val recordElapsedMs: StateFlow<Long> = recorder.elapsedMs
    val recordAmplitude: StateFlow<Float> = recorder.amplitude

    private var pendingAudio: File? = null

    /** Empieza a grabar una nota para [gameId]. false si el micrófono no pudo abrirse. */
    fun startVoiceNote(gameId: Long): Boolean {
        if (_recordingFor.value != null) return false
        val file = File(repo.newVoiceNoteFile(gameId))
        if (!recorder.start(file.absolutePath)) return false
        pendingAudio = file
        _recordingFor.value = gameId
        return true
    }

    /** Detiene y guarda la nota. Descarta grabaciones demasiado cortas. */
    fun stopVoiceNote() = viewModelScope.launch {
        val gameId = _recordingFor.value ?: return@launch
        val file = pendingAudio
        val durationMs = recorder.stop()
        _recordingFor.value = null
        pendingAudio = null
        if (file == null) return@launch
        if (durationMs < MIN_VOICE_NOTE_MS || !file.exists()) {
            withContext(Dispatchers.IO) { runCatching { file.delete() } }
            return@launch
        }
        // Sin texto todavía: si hay modelo, la transcripción lo rellena en segundo plano.
        val noteId = withContext(Dispatchers.IO) {
            repo.addNote(gameId, text = "", audioPath = file.absolutePath, durationMs = durationMs)
        }
        transcribeNote(noteId, file.absolutePath)
    }

    // ---- Transcripción (Whisper local) ----

    private val modelStore = AndroidWhisperModelStore(app)
    private val transcriber = WhisperTranscriber(modelStore)

    private val _transcriptionLanguage = MutableStateFlow(repo.transcriptionLanguage())
    val transcriptionLanguage: StateFlow<TranscriptionLanguage> = _transcriptionLanguage.asStateFlow()

    fun setTranscriptionLanguage(language: TranscriptionLanguage) {
        _transcriptionLanguage.value = language
        repo.setTranscriptionLanguage(language)
    }

    /** Ids de las notas que se están transcribiendo en este momento. */
    private val _transcribing = MutableStateFlow<Set<Long>>(emptySet())
    val transcribing: StateFlow<Set<Long>> = _transcribing.asStateFlow()

    /**
     * Transcribe una nota de voz en segundo plano y guarda el resultado en su `text`;
     * como las notas son un Flow, la UI se actualiza sola cuando termina.
     */
    fun transcribeNote(noteId: Long, audioPath: String) {
        if (installedModel.value == null || noteId in _transcribing.value) return
        viewModelScope.launch(Dispatchers.Default) {
            _transcribing.value = _transcribing.value + noteId
            try {
                val text = transcriber.transcribe(audioPath, _transcriptionLanguage.value)
                if (!text.isNullOrBlank()) {
                    withContext(Dispatchers.IO) { repo.setNoteText(noteId, text) }
                }
            } finally {
                _transcribing.value = _transcribing.value - noteId
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        transcriber.release()
    }

    private val _installedModel = MutableStateFlow<WhisperModel?>(null)
    val installedModel: StateFlow<WhisperModel?> = _installedModel.asStateFlow()

    // Este init va acá abajo a propósito: Kotlin inicializa en orden de declaración, así que
    // puesto junto al init de arriba, `modelStore` y `_installedModel` todavía serían null.
    init {
        viewModelScope.launch(Dispatchers.IO) {
            _installedModel.value = modelStore.installed()
        }
    }

    private val _modelDownload = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val modelDownload: StateFlow<ModelDownloadState> = _modelDownload.asStateFlow()

    private var downloadJob: Job? = null

    fun downloadModel(model: WhisperModel) {
        if (downloadJob?.isActive == true) return
        _modelDownload.value = ModelDownloadState.Downloading(model, 0f)
        downloadJob = viewModelScope.launch {
            try {
                modelStore.download(model) { progress ->
                    _modelDownload.value = ModelDownloadState.Downloading(model, progress)
                }
                _installedModel.value = withContext(Dispatchers.IO) {
                    // Se usa un solo modelo a la vez: al cambiar, se libera el anterior en vez de
                    // dejar 59 MB + 190 MB ocupados y que "el instalado" quede ambiguo.
                    WhisperModel.entries.filter { it != model }.forEach { modelStore.delete(it) }
                    modelStore.installed()
                }
                _modelDownload.value = ModelDownloadState.Idle
            } catch (e: CancellationException) {
                _modelDownload.value = ModelDownloadState.Idle
                throw e
            } catch (e: Throwable) {
                _modelDownload.value = ModelDownloadState.Failed(e.message ?: "Download failed")
            }
        }
    }

    fun cancelModelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _modelDownload.value = ModelDownloadState.Idle
    }

    fun deleteModel(model: WhisperModel) = io {
        modelStore.delete(model)
        _installedModel.value = modelStore.installed()
    }

    fun dismissModelError() {
        _modelDownload.value = ModelDownloadState.Idle
    }

    /** Cancela la grabación en curso y descarta el audio. */
    fun cancelVoiceNote() = viewModelScope.launch {
        val file = pendingAudio
        recorder.stop()
        _recordingFor.value = null
        pendingAudio = null
        withContext(Dispatchers.IO) { file?.let { runCatching { it.delete() } } }
    }

    fun addPhoto(gameId: Long, uri: Uri) = io { repo.addPhoto(gameId, PlatformImage(uri)) }
    fun deletePhoto(id: Long) = io { repo.deletePhoto(id) }

    fun setCoverFromUri(gameId: Long, uri: Uri) = io { repo.setCoverFromImage(gameId, PlatformImage(uri)) }
    fun clearCustomCover(gameId: Long) = io { repo.clearCustomCover(gameId) }

    fun addToWishlist(platform: String, game: String, slug: String, coverUrl: String) =
        io { repo.addToWishlist(platform, game, slug, coverUrl) }

    fun removeFromWishlist(id: Long) = io { repo.removeFromWishlist(id) }
    fun clearWishlist() = io { repo.clearWishlist() }

    // ---- Ajustes (SharedPreferences: rápido y seguro en el main) ----

    fun themeMode(): ThemeMode = repo.themeMode()
    fun setThemeMode(mode: ThemeMode) = repo.setThemeMode(mode)
    fun regionFilter(): RegionFilter = repo.regionFilter()
    fun setRegionFilter(region: RegionFilter) = repo.setRegionFilter(region)

    // Solo para probar los estados vacíos (Settings → Developer, visible únicamente en debug): vive
    // en memoria y no toca la BD; al reiniciar vuelve a false para no dejar la app "vacía" por error.
    private val _previewEmpty = MutableStateFlow(false)
    val previewEmpty: StateFlow<Boolean> = _previewEmpty.asStateFlow()
    fun setPreviewEmpty(on: Boolean) { _previewEmpty.value = on }

    /** Export CSV: acción puntual del usuario; lee la BD en el hilo llamante (colección chica). */
    fun exportCsv(): String = repo.collectionCsv()

    private inline fun io(crossinline block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    companion object {
        /** Por debajo de esto fue un toque accidental, no una nota. */
        private const val MIN_VOICE_NOTE_MS = 700L
    }
}
