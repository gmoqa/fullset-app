package com.gmoqa.fullset.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Instantánea **portable** de las listas, para respaldo y portabilidad — el usuario la guarda donde
 * quiera (un archivo, la nube que elija, lo que sea). Es solo texto: juegos, wishlist y notas
 * (transcripciones incluidas). **No** lleva audios ni fotos (binarios)
 * ni carátulas personalizadas locales (`cover_path`) — solo `coverUrl`, que es una URL portable.
 *
 * El merge al importar ([DiaryRepository.importSnapshot]) es una **unión por clave natural**: agrega
 * lo que falta y **nunca borra ni pisa** lo existente, así no se pierden altas de ningún dispositivo.
 * (Los borrados no se propagan entre dispositivos: es la decisión "simple" de v1.)
 */
@Serializable
data class SyncSnapshot(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val games: List<GameSync> = emptyList(),
    val wishlist: List<WishlistSync> = emptyList(),
)

@Serializable
data class GameSync(
    val name: String,
    val platform: String,
    val slug: String = "",
    val region: String = "",
    val year: Int? = null,
    val genre: String = "",
    val condition: String = "",
    val publisher: String = "",
    val serial: String = "",
    val digital: Boolean = false,
    /** Primera vez jugado (ISO de precisión variable); "" = sin dato. */
    val firstPlayed: String = "",
    /** Lanzamiento con precisión de mes/día cuando el catálogo la tiene. */
    val releaseDate: String = "",
    val playing: Boolean = false,
    val backlog: Boolean = false,
    val coverUrl: String = "",
    val createdAt: Long = 0,
    val notes: List<NoteSync> = emptyList(),
    /** Vacío en el backup de solo datos; con contenido en el archivo completo. */
    val photos: List<PhotoSync> = emptyList(),
)

/**
 * Una foto del diario dentro del respaldo. Guarda el **nombre del archivo**, no su ruta: las rutas
 * son absolutas y propias de cada dispositivo, así que no sirven para restaurar en otro. El archivo
 * viaja en `photos/<name>` dentro del ZIP.
 */
@Serializable
data class PhotoSync(
    val name: String,
    val caption: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class NoteSync(
    val text: String,
    val createdAt: Long = 0,
    /** > 0 significa que era una nota de voz: guardamos la transcripción, no el audio. */
    val durationMs: Long = 0,
)

@Serializable
data class WishlistSync(
    val platform: String,
    val game: String,
    val slug: String = "",
    val coverUrl: String = "",
    val addedAt: Long = 0,
)

/** Qué entró en el último merge (para avisarle al usuario). */
data class SyncMergeResult(
    val newGames: Int,
    val newNotes: Int,
    val newWishlist: Int,
    val newPhotos: Int = 0,
) {
    val nothingNew: Boolean
        get() = newGames == 0 && newNotes == 0 && newWishlist == 0 && newPhotos == 0
}

// Claves naturales para la unión. Separador = NUL (\u0000): no aparece en nombres/slugs, así que dos
// campos distintos nunca producen la misma clave.
private const val SEP = "\u0000"

private fun gameKey(platform: String, slug: String, name: String): String =
    platform.trim().lowercase() + SEP + slug.trim().ifBlank { name.trim().lowercase() }

private fun wishKey(platform: String, slug: String, game: String): String =
    platform.trim().lowercase() + SEP + slug.trim().ifBlank { game.trim().lowercase() }

private fun noteKey(createdAt: Long, text: String): String = createdAt.toString() + SEP + text.trim()

/**
 * Arma la instantánea de las listas desde la BD.
 *
 * [withPhotos] decide si además se listan las fotos del diario. Va apagado por defecto porque el
 * respaldo de solo datos pesa unos KB y se hace seguido; las fotos son cientos de MB y van en el
 * archivo completo (ver [DiaryRepository.exportArchive]).
 */
fun DiaryRepository.exportSnapshot(withPhotos: Boolean = false): SyncSnapshot {
    val games = games().map { g ->
        GameSync(
            name = g.name, platform = g.platform, slug = g.slug, region = g.region,
            year = g.releaseYear, genre = g.genre, condition = g.condition,
            publisher = g.publisher, serial = g.serial, digital = g.digital,
            firstPlayed = g.firstPlayed, releaseDate = g.releaseDate,
            playing = g.playing, backlog = g.backlog, coverUrl = g.coverUrl, createdAt = g.createdAt,
            notes = notes(g.id).filter { it.text.isNotBlank() }
                .map { NoteSync(it.text, it.createdAt, it.durationMs) },
            photos = if (!withPhotos) emptyList() else photos(g.id).map {
                PhotoSync(it.path.substringAfterLast('/'), it.caption, it.createdAt)
            },
        )
    }
    val wishlist = wishlist().map { WishlistSync(it.platform, it.game, it.slug, it.coverUrl, it.addedAt) }
    return SyncSnapshot(version = 1, exportedAt = nowMillis(), games = games, wishlist = wishlist)
}

/**
 * Une [snapshot] con la BD local: agrega juegos, notas y wishlist que falten (por clave natural),
 * sin tocar ni borrar lo que ya existe. Idempotente: reimportar la misma instantánea no agrega nada.
 */
fun DiaryRepository.importSnapshot(
    snapshot: SyncSnapshot,
    /** Fotos ya extraídas del archivo: nombre dentro del respaldo -> ruta local donde quedaron. */
    photoFiles: Map<String, String> = emptyMap(),
): SyncMergeResult {
    var newGames = 0
    var newNotes = 0
    var newWish = 0
    var newPhotos = 0

    val local = games()
    // id local + claves de nota y foto existentes, por clave natural del juego.
    val idByKey = HashMap<String, Long>()
    val noteKeysByGame = HashMap<String, MutableSet<String>>()
    val photoKeysByGame = HashMap<String, MutableSet<Long>>()
    for (g in local) {
        val key = gameKey(g.platform, g.slug, g.name)
        idByKey[key] = g.id
        noteKeysByGame[key] = notes(g.id).map { noteKey(it.createdAt, it.text) }.toMutableSet()
        photoKeysByGame[key] = photos(g.id).map { it.createdAt }.toMutableSet()
    }

    for (gs in snapshot.games) {
        val key = gameKey(gs.platform, gs.slug, gs.name)
        val gameId = idByKey[key] ?: run {
            val id = addGame(
                name = gs.name, platform = gs.platform, coverUrl = gs.coverUrl,
                createdAt = gs.createdAt.takeIf { it > 0 } ?: nowMillis(),
                region = gs.region, releaseYear = gs.year, genre = gs.genre,
                condition = gs.condition, slug = gs.slug, publisher = gs.publisher,
                serial = gs.serial, digital = gs.digital,
            )
            if (gs.playing) setPlaying(id, true)
            if (gs.backlog) setBacklog(id, true)
            if (gs.firstPlayed.isNotBlank()) setFirstPlayed(id, gs.firstPlayed)
            idByKey[key] = id
            noteKeysByGame[key] = mutableSetOf()
            photoKeysByGame[key] = mutableSetOf()
            newGames++
            id
        }
        val existingNotes = noteKeysByGame.getValue(key)
        for (ns in gs.notes) {
            if (ns.text.isBlank()) continue
            val nk = noteKey(ns.createdAt, ns.text)
            if (!existingNotes.add(nk)) continue
            addNote(gameId, ns.text, createdAt = ns.createdAt.takeIf { it > 0 } ?: nowMillis(), durationMs = ns.durationMs)
            newNotes++
        }

        // Las fotos solo entran si el archivo las traía: en un respaldo de solo datos la lista viene
        // vacía y no se toca nada. Se identifican por su instante de captura, único en la práctica.
        val existingPhotos = photoKeysByGame.getValue(key)
        for (ps in gs.photos) {
            val extracted = photoFiles[ps.name] ?: continue
            if (!existingPhotos.add(ps.createdAt)) continue
            if (adoptPhoto(gameId, extracted, ps.caption, ps.createdAt)) newPhotos++
        }
    }

    val localWish = wishlist().map { wishKey(it.platform, it.slug, it.game) }.toMutableSet()
    for (ws in snapshot.wishlist) {
        val wk = wishKey(ws.platform, ws.slug, ws.game)
        if (!localWish.add(wk)) continue
        addToWishlist(ws.platform, ws.game, ws.slug, ws.coverUrl, addedAt = ws.addedAt.takeIf { it > 0 } ?: nowMillis())
        newWish++
    }

    return SyncMergeResult(newGames, newNotes, newWish, newPhotos)
}

/** Serialización JSON de la instantánea (lo que se sube/baja del Drive). */
fun SyncSnapshot.toJson(): String = AppJson.encodeToString(this)

fun syncSnapshotFromJson(json: String): SyncSnapshot = AppJson.decodeFromString(json)
