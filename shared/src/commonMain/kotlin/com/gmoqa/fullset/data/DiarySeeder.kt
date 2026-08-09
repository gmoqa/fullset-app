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
        fixForeignSnesSerials()
        fixDuplicateSnesSerials()
        refreshFromCatalog()
    }

    /**
     * El catálogo de SNES traía doce catalog number de otra región (`SNSP-` europeo, `SHVC-`
     * japonés) porque su generador tomaba de libretro la fila equivocada. Ya está corregido en el
     * catálogo, pero quien haya abierto la app antes tiene esos códigos copiados en su colección, y
     * [refreshFromCatalog] no los pisa (solo completa vacíos). Esto los limpia para que el refresh
     * de abajo los vuelva a completar bien; el que no tenga equivalente NTSC-U queda vacío, que es
     * más honesto que un código que no coincide con el cartucho.
     */
    private fun fixForeignSnesSerials() = migration(SNES_SERIAL_FIX_FLAG) {
        repo.clearForeignSerials("Super Nintendo", "SNSP-%", "SHVC-%")
    }

    /**
     * Cinco pares de juegos de SNES compartían catalog number porque el generador legacy cruzaba por
     * título de forma laxa: uno de cada par se quedaba con el código del otro. Ya está resuelto en el
     * catálogo contra la etiqueta de cada cartucho, pero [refreshFromCatalog] no pisa un valor
     * existente, así que las colecciones ya cargadas conservan el equivocado.
     *
     * Se reemplaza **solo si el juego todavía tiene exactamente el valor viejo**: si lo editaste a
     * mano, tu dato manda y no se toca.
     */
    private fun fixDuplicateSnesSerials() = migration(SNES_DUPLICATE_FIX_FLAG) {
        val corrections = listOf(
            // slug, valor viejo (compartido), valor correcto
            Triple("brawl-brothers", "SNS-RB", "SNS-RE-USA"),
            Triple("rival-turf", "SNS-RB", "SNS-RB-USA"),
            Triple("king-of-the-monsters-2", "SNS-A7SE-USA", "SNS-KT-USA"),
            Triple("lost-vikings-2", "SNS-LV", "SNS-ALVE-USA"),
            Triple("the-lost-vikings", "SNS-LV", "SNS-LV-USA"),
            Triple("tony-meolas-sidekicks-soccer", "SNS-WO", "SNS-6K-USA"),
            Triple("star-fox-super-weekend", "SNS-FO", "SNS-FO-DIS"),
        )
        repo.database.transaction {
            corrections.forEach { (slug, old, new) ->
                repo.updateSerialIfEquals("Super Nintendo", slug, old, new)
            }
        }
    }

    /**
     * Clave para reconocer un juego por su nombre: minúsculas, sin acentos y sin puntuación.
     * Tolera que el catálogo escriba "Heavy Metal: Geomatrix" y la colección "Heavy Metal Geomatrix".
     */
    private fun titleKey(raw: String): String = buildString {
        for (c in raw.lowercase()) if (c.isLetterOrDigit()) append(c)
    }

    /** Regiones a consultar para un juego: la suya primero, el resto como respaldo. */
    private fun regionsFor(region: String): List<RegionFilter> {
        val propia = RegionFilter.entries.firstOrNull { it.label == region.trim() }
        return listOfNotNull(propia) + RegionFilter.entries.filter { it != propia }
    }

    /**
     * Vuelve a cruzar la colección con los catálogos: completa **lo que esté vacío** (catalog
     * number, editora, género, año) y **corrige la carátula automática**, que es el único campo que
     * se pisa. Va aparte porque un dato faltante se nota y uno equivocado no: una tapa europea en
     * un juego americano se ve perfectamente normal, así que completar solo lo vacío dejaba el
     * error a la vista para siempre.
     *
     * Los catálogos son un dataset vivo: cuando una consola estrena lista o una existente se
     * enriquece, los juegos ya cargados se quedan con los datos que había el día que los agregaste.
     * Esto los pone al día sin tocar nada tuyo: no renombra, no pisa lo que completaste a mano y no
     * cambia condición ni notas. El match es por `slug` (la identidad del juego en el catálogo),
     * mirando todas las regiones de esa consola porque un juego puede estar solo en una.
     *
     * La bandera lleva versión: al mejorar los catálogos se sube el número y vuelve a correr una vez.
     */
    private fun refreshFromCatalog() = migration(CATALOG_REFRESH_FLAG) {
        val registry = PlatformRegistry()
        val catalog = GameCatalog()
        // slug -> entrada, por plataforma. Se arma una vez y sirve para todos los juegos.
        // Se indexa **por región**, no todo junto. Aplanarlo dejaba ganar siempre a la primera de
        // `RegionFilter.entries`, que es NTSC-U: una copia japonesa de *Darius Gaiden* de Saturn se
        // llevaba el serial americano (T-8123H en vez de T-1102G), su fecha y su carátula. El juego
        // era correcto, el catálogo también, y aun así mostraba los datos del mercado equivocado.
        val bySlug = registry.all().associate { platform ->
            platform.name to RegionFilter.entries.associateWith { region ->
                catalog.entries(platform, region).associateBy { it.slug }
            }
        }
        // Índice de respaldo por título normalizado. El `slug` es la identidad, pero se genera a
        // partir del título y esa generación cambió con el tiempo (los acentos se transliteran, el
        // "&" pasó a "and"). Cuando un catálogo se corrige, el juego que el usuario ya tenía queda
        // apuntando a un slug que ya no existe y **deja de recibir actualizaciones en silencio**,
        // porque acá abajo había un `?: continue` y nada más. Con esto se reconoce por título y se
        // le repara el slug, así el problema se arregla solo la próxima vez que corra.
        val byTitle = registry.all().associate { platform ->
            platform.name to RegionFilter.entries.associateWith { region ->
                catalog.entries(platform, region).associateBy { titleKey(it.title) }
            }
        }
        repo.database.transaction {
            for (game in repo.games()) {
                if (game.slug.isBlank()) continue
                // Primero la región del propio juego; las demás solo como respaldo, porque un
                // título puede estar catalogado en una sola.
                val orden = regionsFor(game.region)
                val entry = orden.firstNotNullOfOrNull { bySlug[game.platform]?.get(it)?.get(game.slug) }
                    ?: orden.firstNotNullOfOrNull {
                        byTitle[game.platform]?.get(it)?.get(titleKey(game.name))
                    }?.also { found ->
                        repo.linkCatalog(
                            name = game.name, platform = game.platform, slug = found.slug,
                            publisher = found.publisher, serial = found.serial, year = found.year,
                        )
                    }
                    ?: continue
                // Si lo guardado es exactamente el dato de OTRA región, se reasigna a la suya. Sin
                // esto el arreglo del índice solo servía para juegos nuevos: `fillFromCatalog` no
                // pisa un serial ni una fecha que ya tienen valor, aunque sean del mercado errado.
                if (game.serial.isNotBlank() && game.serial != entry.serial) {
                    val ajeno = orden.drop(1).any { otra ->
                        bySlug[game.platform]?.get(otra)?.get(game.slug)?.serial == game.serial
                    }
                    if (ajeno) {
                        repo.relinkToOwnRegion(
                            platform = game.platform, slug = game.slug, foreignSerial = game.serial,
                            serial = entry.serial, releaseDate = entry.releaseDate, year = entry.year,
                        )
                    }
                }
                repo.fillFromCatalog(
                    game.id,
                    serial = entry.serial,
                    publisher = entry.publisher,
                    genre = entry.genre,
                    releaseDate = entry.releaseDate,
                    year = entry.year,
                )
                // La carátula **sí** se pisa, a diferencia del resto: cuando el catálogo corrige
                // una que apuntaba a la región equivocada —el 32X americano mostraba cajas
                // europeas— completar solo lo vacío dejaba el error a la vista para siempre. La
                // consulta se limita a las de libretro, así que una elegida a mano no se toca.
                if (entry.coverUrl.isNotBlank()) {
                    repo.updateCatalogCover(game.platform, entry.slug, entry.coverUrl)
                }
            }
        }
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
        private const val SNES_SERIAL_FIX_FLAG = "snes_foreign_serial_fix_v1"
        private const val SNES_DUPLICATE_FIX_FLAG = "snes_duplicate_serial_fix_v1"
        /** Subir la versión cuando los catálogos mejoren, para volver a completar huecos. */
        private const val CATALOG_REFRESH_FLAG = "catalog_refresh_v15"
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
