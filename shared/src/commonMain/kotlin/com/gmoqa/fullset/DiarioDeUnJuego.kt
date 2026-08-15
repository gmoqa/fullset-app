package com.gmoqa.fullset

import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.Note
import com.gmoqa.fullset.data.Photo
import com.gmoqa.fullset.data.PlatformImage
import com.gmoqa.fullset.data.WhisperModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Todo lo que se puede hacer **parado en un juego**: leerlo, anotarlo, fotografiarlo, grabarle una
 * nota de voz y cambiar su estado.
 *
 * Es una **fachada por rol** sobre [DiaryViewModel], no otra clase: el ViewModel la implementa y la
 * pantalla del detalle recibe esto en vez del objeto entero.
 *
 * El motivo es concreto y está medido. `DiaryViewModel` expone 69 miembros y lo usan tres pantallas
 * que tocan 22–26 cada una, con **86–92% de uso exclusivo**: no hay un ViewModel desordenado, hay
 * tres contratos distintos disfrazados de uno. Mientras el detalle reciba el objeto completo, nada
 * impide que mañana llame a `exportArchive()` o `downloadModel()`, que no tienen nada que ver con
 * mirar un juego. Declarando el rol, **el compilador lo impide**.
 *
 * Lo que se gana además: el contrato del detalle se lee de un vistazo —es este archivo— y un doble
 * para tests implementa 26 miembros en vez de 69.
 *
 * Lo que **no** se gana, y conviene no confundir: esto no achica el ViewModel. Reduce acoplamiento,
 * no tamaño.
 */
interface DiarioDeUnJuego {

    // ---- Lo que se lee, reactivo (recordar con remember(id) en el composable) ----

    fun gameFlow(id: Long): Flow<Game?>
    fun notesFlow(id: Long): Flow<List<Note>>
    fun photosFlow(id: Long): Flow<List<Photo>>

    // ---- Estado del juego ----

    fun setPlaying(id: Long, playing: Boolean)
    fun setBacklog(id: Long, backlog: Boolean)
    fun setCondition(id: Long, condition: String)
    /** Primera vez que lo jugaste: ISO de precisión variable ("1994" | "1994-06" | "1994-06-08"). */
    fun setFirstPlayed(id: Long, iso: String)
    fun deleteGame(id: Long)

    // ---- Notas escritas ----

    fun addNote(gameId: Long, text: String)
    fun editNote(id: Long, text: String)
    fun deleteNote(id: Long)
    /** Las notas como JSON, para compartir (p. ej. pegarlas en un LLM). */
    fun gameNotesJson(gameId: Long): String
    /** Las notas como texto legible, para leer o pegar en un chat. */
    fun gameNotesText(gameId: Long): String

    // ---- Fotos y carátula ----

    fun addPhoto(gameId: Long, image: PlatformImage)
    fun deletePhoto(id: Long)
    fun setCover(gameId: Long, image: PlatformImage)
    fun clearCustomCover(gameId: Long)

    // ---- Notas de voz ----

    /** El juego que se está grabando ahora, o null. Solo se graba de a uno. */
    val recordingFor: StateFlow<Long?>
    val recordElapsedMs: StateFlow<Long>
    val recordAmplitude: StateFlow<Float>
    /** false si ya había una grabación en curso o el micrófono no arrancó. */
    fun startVoiceNote(gameId: Long): Boolean
    fun stopVoiceNote()
    fun cancelVoiceNote()

    // ---- Transcripción (Whisper local) ----

    /** Las notas que se están transcribiendo ahora mismo. */
    val transcribing: StateFlow<Set<Long>>
    fun transcribeNote(noteId: Long, audioPath: String)
    /** Sin modelo instalado no se ofrece transcribir. */
    val installedModel: StateFlow<WhisperModel?>
}
