package com.gmoqa.fullset.roles

import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.ModelDownloadState
import com.gmoqa.fullset.data.PlatformImage
import com.gmoqa.fullset.data.RegionFilter
import com.gmoqa.fullset.data.SortOrder
import com.gmoqa.fullset.data.SteamGridGame
import com.gmoqa.fullset.data.ThemeMode
import com.gmoqa.fullset.data.TrackingMode
import com.gmoqa.fullset.data.TranscriptionLanguage
import com.gmoqa.fullset.data.WhisperModel
import com.gmoqa.fullset.data.WishlistItem
import com.gmoqa.fullset.ui.BackupArchive
import com.gmoqa.fullset.ui.RestoredBackup
import kotlinx.coroutines.flow.StateFlow

/**
 * Las **fachadas por rol** de [com.gmoqa.fullset.DiaryViewModel].
 *
 * Están agrupadas por **concepto**, no por pantalla, a propósito: agruparlas por pantalla las
 * volvería el mismo cajón que vinieron a resolver, solo que repartido en tres. Una pantalla que
 * necesita dos conceptos declara el compuesto de abajo.
 *
 * El motivo está medido: el ViewModel expone 69 miembros y lo usan tres pantallas que tocan 22–26
 * cada una, con 86–92% de uso exclusivo. Ver `docs/REFACTOR.md`.
 */

/** Qué tenés y qué querés tener: la colección y la wishlist, y cómo se les agrega algo. */
interface Coleccion {
    /** Pasa a `true` cuando termina la siembra. Antes de eso la app muestra una carga breve. */
    val ready: StateFlow<Boolean>
    val games: StateFlow<List<Game>>
    val wishlist: StateFlow<List<WishlistItem>>

    /** Último juego agregado: Collection lo enfoca una sola vez y después se consume. */
    val lastAdded: StateFlow<Long?>
    fun consumeLastAdded()

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
    )
    /** Alta a mano de algo que no poseés: entra a Playing con el badge, nunca a Collection. */
    fun addDigitalGame(title: String, platform: String, coverUrl: String, cover: PlatformImage?)
    fun addPhoto(gameId: Long, image: PlatformImage)

    fun addToWishlist(platform: String, game: String, slug: String, coverUrl: String)
    fun removeFromWishlist(id: Long)
    fun clearWishlist()
}

/** Las preferencias: qué secciones ves, cómo se ve y qué región mirás. */
interface Ajustes {
    fun trackingMode(): TrackingMode
    fun setTrackingMode(mode: TrackingMode)
    fun themeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
    fun regionFilter(): RegionFilter
    fun setRegionFilter(region: RegionFilter)
    fun sortOrder(): SortOrder
    fun setSortOrder(order: SortOrder)
    fun showCollectionLabels(): Boolean
    fun setShowCollectionLabels(show: Boolean)
    fun showConsoleTitles(): Boolean
    fun setShowConsoleTitles(show: Boolean)
    fun deleteAudioAfterTranscription(): Boolean
    fun setDeleteAudioAfterTranscription(on: Boolean)
    val transcriptionLanguage: StateFlow<TranscriptionLanguage>
    fun setTranscriptionLanguage(language: TranscriptionLanguage)

    /** Solo en builds debug: previsualizar los estados vacíos sin borrar nada. */
    val previewEmpty: StateFlow<Boolean>
    fun setPreviewEmpty(on: Boolean)
}

/** Sacar y devolver tus datos: es lo que hace que la app no sea una jaula. */
interface Respaldo {
    fun exportCsv(): String
    fun exportSnapshotJson(): String
    fun exportArchive(): BackupArchive
    fun importBackup(backup: RestoredBackup)
    /** Resultado del último respaldo/restauración, para avisarlo una vez. */
    val syncStatus: StateFlow<String?>
    fun clearSyncStatus()
}

/** El modelo de Whisper: bajarlo, borrarlo y saber en qué anda. */
interface ModeloDeVoz {
    val installedModel: StateFlow<WhisperModel?>
    val modelDownload: StateFlow<ModelDownloadState>
    fun downloadModel(model: WhisperModel)
    fun cancelModelDownload()
    fun deleteModel(model: WhisperModel)
    fun dismissModelError()
}

/** Carátulas para las consolas sin catálogo (la PS5), desde SteamGridDB. */
interface BuscadorDeCaratulas {
    /** Sin API key el buscador no se ofrece. */
    val coverSearchEnabled: Boolean
    suspend fun searchGames(title: String): List<SteamGridGame>
    suspend fun coversFor(gameId: Int): List<String>
}

// ---------------------------------------------------------------------------------------------
// Compuestos: lo que necesita una pantalla que cruza dos conceptos. Un parámetro tiene un solo
// tipo, así que la combinación se declara acá en vez de pasarle dos objetos.

/**
 * El home: la colección, los ajustes, el respaldo y el modelo de voz que vive en Settings.
 *
 * Reparte en cinco pestañas, y entre las cinco necesitan justo esto.
 */
interface PantallaHome : Coleccion, Ajustes, Respaldo, ModeloDeVoz

// **La raíz (`AppRoot`) no lleva rol, y es a propósito.** Es el router: construye todas las
// pantallas, así que la unión de lo que necesita *es* el ViewModel entero. Darle un compuesto
// `PantallaRaiz : Coleccion, Ajustes, BuscadorDeCaratulas, DiarioDeUnJuego, PantallaHome` sería
// escribir "todo" con más palabras.
//
// La segregación se cobra en las **hojas**, no en la raíz: el trabajo de la raíz es conocerlo todo.
