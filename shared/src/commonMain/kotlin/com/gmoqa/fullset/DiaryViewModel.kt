package com.gmoqa.fullset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmoqa.fullset.data.DiaryRepository
import com.gmoqa.fullset.data.FileStore
import com.gmoqa.fullset.data.exportSnapshot
import com.gmoqa.fullset.data.gameNotesJson
import com.gmoqa.fullset.data.gameNotesText
import com.gmoqa.fullset.data.importSnapshot
import com.gmoqa.fullset.data.syncSnapshotFromJson
import com.gmoqa.fullset.data.toJson
import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.ModelDownloadState
import com.gmoqa.fullset.data.Note
import com.gmoqa.fullset.data.Photo
import com.gmoqa.fullset.data.PlatformImage
import com.gmoqa.fullset.data.RegionFilter
import com.gmoqa.fullset.data.SteamGridDb
import com.gmoqa.fullset.data.SteamGridGame
import com.gmoqa.fullset.data.SortOrder
import com.gmoqa.fullset.data.ThemeMode
import com.gmoqa.fullset.data.TrackingMode
import com.gmoqa.fullset.data.Transcriber
import com.gmoqa.fullset.data.TranscriptionLanguage
import com.gmoqa.fullset.data.VoiceRecorder
import com.gmoqa.fullset.data.WhisperModel
import com.gmoqa.fullset.data.WhisperModelStore
import com.gmoqa.fullset.ui.BackupArchive
import com.gmoqa.fullset.ui.RestoredBackup
import com.gmoqa.fullset.data.WishlistItem
import com.gmoqa.fullset.data.collectionCsv
import com.gmoqa.fullset.data.ioDispatcher
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

/**
 * Única fuente de verdad de la UI. Es dueño del [DiaryRepository], expone las listas como
 * [StateFlow] reactivos (la UI se refresca sola tras cualquier cambio, sin trucos de navegación)
 * y corre siembra y escrituras **fuera del hilo principal**.
 *
 * Multiplataforma: las fronteras de plataforma llegan por constructor —el grabador de voz, el store
 * y el transcriber de Whisper, y la API key de SteamGridDB— para que el ViewModel quede sin
 * dependencias de Android. En iOS estas piezas son stubs hasta la Fase 5.
 *
 * [ready] pasa a `true` cuando termina la siembra; la UI muestra una carga breve hasta entonces
 * (relevante solo en la primera instalación; ya sembrada, la siembra es casi instantánea).
 */
class DiaryViewModel(
    private val recorder: VoiceRecorder,
    private val modelStore: WhisperModelStore,
    private val transcriber: Transcriber,
    steamGridKey: String,
) : ViewModel(), DiarioDeUnJuego {

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

    override fun gameFlow(id: Long): Flow<Game?> = repo.gameFlow(id)
    override fun notesFlow(id: Long): Flow<List<Note>> = repo.notesFlow(id)
    override fun photosFlow(id: Long): Flow<List<Photo>> = repo.photosFlow(id)

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
        /** Fecha ISO de precisión variable del catálogo. Sin esto el juego nace solo con el año. */
        releaseDate: String = "",
        genre: String = "",
        slug: String = "",
        publisher: String = "",
        /** Catalog number impreso en el cartucho o disco: identifica **esta** edición. */
        serial: String = "",
        /** Alta desde Playing: el juego arranca marcado como que lo estás jugando. */
        playing: Boolean = false,
        /**
         * "No lo poseo". En modo **Diary only** el alta sale del catálogo igual que siempre, pero no
         * afirma posesión: ahí la app es solo el diario. Si se marcara como poseído, al volver a
         * "Collection + diary" —que el propio ajuste ofrece, porque no borra nada— aparecería de
         * golpe una colección de juegos que nunca dijiste tener.
         */
        digital: Boolean = false,
    ) = io {
        val id = repo.addGame(
            title, platform, coverUrl,
            region = region, releaseYear = releaseYear, releaseDate = releaseDate, genre = genre,
            slug = slug, publisher = publisher, serial = serial, digital = digital,
        )
        if (playing) repo.setPlaying(id, true)
        _lastAdded.value = id
    }

    /**
     * Agrega un juego **digital** (no lo poseés). Entra directo a **Playing** (`digital=true`,
     * `playing=true`) y **no** aparece en Collection, que es tu colección física. La carátula puede
     * venir del buscador ([coverUrl]) o de la galería ([cover]).
     */
    fun addDigitalGame(title: String, platform: String, coverUrl: String, cover: PlatformImage?) = io {
        val id = repo.addGame(title, platform, coverUrl = coverUrl, digital = true)
        repo.setPlaying(id, true)
        if (cover != null) repo.setCoverFromImage(id, cover)
    }

    // ---- Buscador de carátulas (SteamGridDB, para plataformas sin catálogo) ----

    // La key se inyecta por constructor para que SteamGridDb quede sin dependencias de plataforma.
    private val coverSource = SteamGridDb(steamGridKey)

    /** Hay API key configurada: si no, el formulario manual no ofrece el buscador. */
    val coverSearchEnabled: Boolean get() = coverSource.isEnabled

    /** Juegos que coinciden con un título (para elegir el correcto antes de ver sus carátulas). */
    suspend fun searchGames(title: String): List<SteamGridGame> = coverSource.searchGames(title)

    /** Carátulas (URLs 600×900) del juego elegido; vacío si no tiene o falla la red. */
    suspend fun coversFor(gameId: Int): List<String> = coverSource.coversForGame(gameId)

    override fun setPlaying(id: Long, playing: Boolean) = io { repo.setPlaying(id, playing) }
    override fun setBacklog(id: Long, backlog: Boolean) = io { repo.setBacklog(id, backlog) }
    override fun setCondition(id: Long, condition: String) = io { repo.setCondition(id, condition) }

    override fun setFirstPlayed(id: Long, iso: String) = io { repo.setFirstPlayed(id, iso) }
    override fun deleteGame(id: Long) = io { repo.deleteGame(id) }

    override fun addNote(gameId: Long, text: String) = io { repo.addNote(gameId, text) }
    override fun editNote(id: Long, text: String) = io { repo.setNoteText(id, text) }

    /** JSON de las notas de un juego, para compartir (p. ej. pegarlo en un LLM). */
    override fun gameNotesJson(gameId: Long): String = repo.gameNotesJson(gameId)

    /** Las notas de un juego como texto legible, para compartir/leer/pegar en un chat. */
    override fun gameNotesText(gameId: Long): String = repo.gameNotesText(gameId)
    override fun deleteNote(id: Long) = io { repo.deleteNote(id) }

    // ---- Notas de voz (Fase 1: grabar y guardar; la transcripción llega después) ----

    /** Id del juego que se está grabando ahora, o null si no hay grabación en curso. */
    private val _recordingFor = MutableStateFlow<Long?>(null)
    override val recordingFor: StateFlow<Long?> = _recordingFor.asStateFlow()

    override val recordElapsedMs: StateFlow<Long> = recorder.elapsedMs
    override val recordAmplitude: StateFlow<Float> = recorder.amplitude

    private var pendingAudioPath: String? = null

    /** Empieza a grabar una nota para [gameId]. false si el micrófono no pudo abrirse. */
    override fun startVoiceNote(gameId: Long): Boolean {
        if (_recordingFor.value != null) return false
        val path = repo.newVoiceNoteFile(gameId)
        if (!recorder.start(path)) return false
        pendingAudioPath = path
        _recordingFor.value = gameId
        return true
    }

    /** Detiene y guarda la nota. Descarta grabaciones demasiado cortas. */
    override fun stopVoiceNote() {
        viewModelScope.launch {
            val gameId = _recordingFor.value ?: return@launch
            val path = pendingAudioPath
            val durationMs = recorder.stop()
            _recordingFor.value = null
            pendingAudioPath = null
            if (path == null) return@launch
            if (durationMs < MIN_VOICE_NOTE_MS || !FileStore.exists(path)) {
                withContext(ioDispatcher) { FileStore.delete(path) }
                return@launch
            }
            // Sin texto todavía: si hay modelo, la transcripción lo rellena en segundo plano.
            val noteId = withContext(ioDispatcher) {
                repo.addNote(gameId, text = "", audioPath = path, durationMs = durationMs)
            }
            transcribeNote(noteId, path)
        }
    }

    // ---- Transcripción (Whisper local) ----

    private val _transcriptionLanguage = MutableStateFlow(repo.transcriptionLanguage())
    val transcriptionLanguage: StateFlow<TranscriptionLanguage> = _transcriptionLanguage.asStateFlow()

    fun setTranscriptionLanguage(language: TranscriptionLanguage) {
        _transcriptionLanguage.value = language
        repo.setTranscriptionLanguage(language)
    }

    /** Ids de las notas que se están transcribiendo en este momento. */
    private val _transcribing = MutableStateFlow<Set<Long>>(emptySet())
    override val transcribing: StateFlow<Set<Long>> = _transcribing.asStateFlow()

    /**
     * Transcribe una nota de voz en segundo plano y guarda el resultado en su `text`;
     * como las notas son un Flow, la UI se actualiza sola cuando termina.
     */
    override fun transcribeNote(noteId: Long, audioPath: String) {
        if (installedModel.value == null || noteId in _transcribing.value) return
        viewModelScope.launch(Dispatchers.Default) {
            _transcribing.value = _transcribing.value + noteId
            try {
                val text = transcriber.transcribe(audioPath, _transcriptionLanguage.value)
                if (!text.isNullOrBlank()) {
                    withContext(ioDispatcher) {
                        repo.setNoteText(noteId, text)
                        // Si está activado, borra el WAV y deja la nota solo como texto.
                        if (repo.deleteAudioAfterTranscription()) repo.clearNoteAudio(noteId, audioPath)
                    }
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
    override val installedModel: StateFlow<WhisperModel?> = _installedModel.asStateFlow()

    // Este init va acá abajo a propósito: Kotlin inicializa en orden de declaración, así que
    // puesto junto al init de arriba, `_installedModel` todavía sería null.
    init {
        viewModelScope.launch(ioDispatcher) {
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
                _installedModel.value = withContext(ioDispatcher) {
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
    override fun cancelVoiceNote() {
        viewModelScope.launch {
            val path = pendingAudioPath
            recorder.stop()
            _recordingFor.value = null
            pendingAudioPath = null
            withContext(ioDispatcher) { path?.let { FileStore.delete(it) } }
        }
    }

    override fun addPhoto(gameId: Long, image: PlatformImage) = io { repo.addPhoto(gameId, image) }
    override fun deletePhoto(id: Long) = io { repo.deletePhoto(id) }

    override fun setCover(gameId: Long, image: PlatformImage) = io { repo.setCoverFromImage(gameId, image) }
    override fun clearCustomCover(gameId: Long) = io { repo.clearCustomCover(gameId) }

    fun addToWishlist(platform: String, game: String, slug: String, coverUrl: String) =
        io { repo.addToWishlist(platform, game, slug, coverUrl) }

    fun removeFromWishlist(id: Long) = io { repo.removeFromWishlist(id) }
    fun clearWishlist() = io { repo.clearWishlist() }

    // ---- Ajustes (SharedPreferences: rápido y seguro en el main) ----

    fun trackingMode(): TrackingMode = repo.trackingMode()
    fun setTrackingMode(mode: TrackingMode) = repo.setTrackingMode(mode)
    fun themeMode(): ThemeMode = repo.themeMode()
    fun setThemeMode(mode: ThemeMode) = repo.setThemeMode(mode)
    fun regionFilter(): RegionFilter = repo.regionFilter()
    fun setRegionFilter(region: RegionFilter) = repo.setRegionFilter(region)
    fun sortOrder(): SortOrder = repo.sortOrder()
    fun setSortOrder(order: SortOrder) = repo.setSortOrder(order)
    fun showCollectionLabels(): Boolean = repo.showCollectionLabels()
    fun setShowCollectionLabels(show: Boolean) = repo.setShowCollectionLabels(show)
    fun showConsoleTitles(): Boolean = repo.showConsoleTitles()
    fun setShowConsoleTitles(show: Boolean) = repo.setShowConsoleTitles(show)
    fun deleteAudioAfterTranscription(): Boolean = repo.deleteAudioAfterTranscription()
    fun setDeleteAudioAfterTranscription(on: Boolean) = repo.setDeleteAudioAfterTranscription(on)

    // ---- Respaldo / sync a archivo ----

    /** Resultado del último restore, para mostrarlo en Settings; null = nada que mostrar. */
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()
    fun clearSyncStatus() { _syncStatus.value = null }

    /** Serializa la colección (listas + transcripciones) a JSON para respaldar. */
    fun exportSnapshotJson(): String = repo.exportSnapshot().toJson()

    /**
     * Respaldo completo: el mismo JSON, pero con las fotos listadas, más sus archivos para que la
     * capa de plataforma los meta en el ZIP.
     */
    fun exportArchive(): BackupArchive = BackupArchive(
        json = repo.exportSnapshot(withPhotos = true).toJson(),
        photoPaths = repo.allPhotoPaths(),
    )

    /** Cuántas fotos y cuánto pesan, para poder avisar antes de armar un archivo de cientos de MB. */
    fun photoCount(): Int = repo.allPhotoPaths().size

    /** Une un respaldo a la colección (nunca borra) y reporta cuántos ítems nuevos entraron. */
    fun importBackup(backup: RestoredBackup) = io {
        _syncStatus.value = runCatching {
            repo.importSnapshot(syncSnapshotFromJson(backup.json), backup.photos)
        }.fold(
            onSuccess = { r ->
                if (r.nothingNew) "Backup already in sync — nothing new."
                else buildString {
                    append("Restored: +${r.newGames} games, +${r.newNotes} notes")
                    append(", +${r.newWishlist} wishlist")
                    if (r.newPhotos > 0) append(", +${r.newPhotos} photos")
                    append('.')
                }
            },
            onFailure = { "Couldn't read that file — is it a fullset backup?" },
        )
    }

    // Solo para probar los estados vacíos (Settings → Developer, visible únicamente en debug): vive
    // en memoria y no toca la BD; al reiniciar vuelve a false para no dejar la app "vacía" por error.
    private val _previewEmpty = MutableStateFlow(false)
    val previewEmpty: StateFlow<Boolean> = _previewEmpty.asStateFlow()
    fun setPreviewEmpty(on: Boolean) { _previewEmpty.value = on }

    /** Export CSV: acción puntual del usuario; lee la BD en el hilo llamante (colección chica). */
    fun exportCsv(): String = repo.collectionCsv()

    private inline fun io(crossinline block: () -> Unit) {
        viewModelScope.launch(ioDispatcher) { block() }
    }

    companion object {
        /** Por debajo de esto fue un toque accidental, no una nota. */
        private const val MIN_VOICE_NOTE_MS = 700L
    }
}
