package com.gmoqa.fullset.data

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable

/**
 * Siembra la colección inicial desde `seed/collection.json` la primera vez, y en actualizaciones
 * posteriores incorpora los juegos que se hayan agregado a ese seed. Cada paso corre una sola vez
 * (bandera en prefs) vía [migration]. Separado de [DiaryRepository] para que ese quede enfocado en CRUD.
 *
 * El `collection.json` versionado en este repo viene **vacío**: la app arranca sin juegos y cada
 * quien arma su colección desde la app. Si querés precargar títulos, completá ese JSON con el
 * esquema de [LibraryEntryDto] / [WishlistEntryDto].
 */
class DiarySeeder(
    private val repo: DiaryRepository,
    private val settings: Settings,
) {
    fun run() {
        seedCollectionIfNeeded()
        syncSeededGames()
        fixMissingCovers()
    }

    /**
     * Rellena la carátula de juegos cuyo `coverUrl` quedó vacío por un desajuste de nombre con
     * libretro (la imagen existe, con otro título). Solo toca los que están sin cover. Una vez.
     */
    private fun fixMissingCovers() = migration(COVER_FIX_FLAG) {
        val q = repo.database.fullsetQueries
        q.fillCoverUrlByName(GENESIS_BOXART + "Aladdin%20%28USA%29%20%28Final%20Cut%29.png", "Disney's Aladdin")
        q.fillCoverUrlByName(GENESIS_BOXART + "Lotus%20II%20%28USA%29.png", "Lotus II: RECS")
    }

    /** Ejecuta [block] una sola vez (marca [flag] al terminar). Base del mecanismo de siembra. */
    private fun migration(flag: String, block: () -> Unit) {
        if (settings.getBoolean(flag, false)) return
        block()
        settings.putBoolean(flag, true)
    }

    /** Carga inicial de `collection.json` (biblioteca + wishlist). */
    private fun seedCollectionIfNeeded() = migration(SEED_FLAG) {
        val collection = loadCollection() ?: return@migration
        repo.database.transaction {
            val base = nowMillis()
            collection.library.forEachIndexed { i, o -> insertLibraryEntry(o, base - i) }
            collection.wishlist.forEachIndexed { j, w ->
                repo.addToWishlist(w.platform, w.title, "", w.cover, base - j)
            }
        }
    }

    /**
     * Inserta los juegos del `collection.json` que aún NO existan (por título+plataforma), sin tocar
     * los presentes ni las notas/fotos del usuario. Una vez por versión del flag (no revive borrados).
     */
    private fun syncSeededGames() = migration(SYNC_SEED_FLAG) { insertMissingSeedGames() }

    /** Da de alta los juegos del seed que todavía no estén en la BD (por título + plataforma). */
    private fun insertMissingSeedGames() {
        val collection = loadCollection() ?: return
        val existing = repo.games().map { it.name to it.platform }.toSet()
        val base = nowMillis()
        repo.database.transaction {
            var i = 0
            collection.library.forEach { entry ->
                if ((entry.title to entry.platform) !in existing) insertLibraryEntry(entry, base - i++)
            }
        }
    }

    /** Inserta un juego + su nota de metadatos (género · año · estado + notas). Devuelve el id. */
    private fun insertLibraryEntry(o: LibraryEntryDto, createdAt: Long): Long {
        val id = repo.addGame(
            o.title, o.platform, o.cover, createdAt,
            region = o.region, releaseYear = o.year, genre = o.genre, condition = o.condition,
            slug = o.slug, publisher = o.publisher, serial = o.serial,
        )
        val meta = listOf(o.genre, o.year?.toString().orEmpty(), o.condition)
            .filter { it.isNotBlank() }.joinToString(" · ")
        val note = listOf(meta, o.notes).filter { it.isNotBlank() }.joinToString("\n")
        if (note.isNotBlank()) repo.addNote(id, note, createdAt)
        return id
    }

    private fun loadCollection(): CollectionDto? =
        readTextAsset("seed/collection.json")?.let { AppJson.decodeFromString<CollectionDto>(it) }

    companion object {
        private const val SEED_FLAG = "seed_v1"
        private const val SYNC_SEED_FLAG = "sync_seed_v1"
        private const val COVER_FIX_FLAG = "cover_fix_genesis_v1"
        private const val GENESIS_BOXART =
            "https://raw.githubusercontent.com/libretro-thumbnails/Sega_-_Mega_Drive_-_Genesis/master/Named_Boxarts/"
    }
}

/** DTOs de `seed/collection.json`. */
@Serializable
private data class CollectionDto(
    val library: List<LibraryEntryDto> = emptyList(),
    val wishlist: List<WishlistEntryDto> = emptyList(),
)

@Serializable
private data class LibraryEntryDto(
    val title: String = "",
    val platform: String = "",
    val region: String = "",
    val year: Int? = null,
    val genre: String = "",
    val condition: String = "",
    val notes: String = "",
    val cover: String = "",
    /** Datos del catálogo oficial: identifican el juego y completan lo que el Excel no anota. */
    val slug: String = "",
    val publisher: String = "",
    val serial: String = "",
)

@Serializable
private data class WishlistEntryDto(
    val title: String = "",
    val platform: String = "",
    val cover: String = "",
)
