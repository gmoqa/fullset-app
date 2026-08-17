package com.gmoqa.fullset.data

import com.gmoqa.fullset.domain.GameSearch

import kotlinx.serialization.Serializable

/** DTO del catálogo JSON. Esquema estándar: title/platform/region/year/publisher/genre/slug/serial/coverUrl. */
@Serializable
private data class CatalogEntryDto(
    val title: String = "",
    val platform: String = "",
    val region: String = "",
    val year: Int? = null,
    val releaseDate: String = "",
    val developer: String = "",
    val publisher: String = "",
    val genre: String = "",
    val slug: String = "",
    val serial: String = "",
    val rating: String = "",
    val coverUrl: String = "",
)

/**
 * Lee los catálogos JSON empaquetados en assets y permite buscarlos. Las plataformas se declaran en
 * `catalogs/platforms.json` (ver [PlatformRegistry]); agregar una consola es solo config + su JSON de
 * catálogo. Multiplataforma: los assets se leen con [readTextAsset] (expect/actual), inyectable para
 * poder testear el parseo real contra los catálogos del repo.
 */
class GameCatalog(private val readAsset: (String) -> String? = ::readTextAsset) {

    private val cache = mutableMapOf<String, List<CatalogEntry>>()

    fun entries(platform: Platform, region: RegionFilter = RegionFilter.NTSC_U): List<CatalogEntry> {
        val file = platform.catalogFor(region)
        // Cache por archivo (no por plataforma): cada región carga y cachea su propio catálogo.
        return cache.getOrPut("${platform.id}:$file") {
            // Plataformas modernas (PS5…) no traen catálogo: se cargan a mano, sin lista que buscar.
            if (file.isBlank()) return@getOrPut emptyList()
            val text = readAsset(file) ?: return@getOrPut emptyList()
            AppJson.decodeFromString<List<CatalogEntryDto>>(text)
                .mapNotNull { dto ->
                    val title = dto.title.trim()
                    if (title.isEmpty()) return@mapNotNull null
                    val subtitle = listOfNotNull(dto.publisher.trim().ifEmpty { null }, dto.year?.toString())
                        .joinToString(" · ")
                    CatalogEntry(
                        title = title,
                        subtitle = subtitle,
                        slug = dto.slug,
                        region = dto.region,
                        year = dto.year,
                        releaseDate = dto.releaseDate.trim(),
                        developer = dto.developer.trim(),
                        publisher = dto.publisher.trim(),
                        genre = dto.genre.trim(),
                        serial = dto.serial.trim(),
                        rating = dto.rating.trim(),
                        coverUrl = dto.coverUrl.trim(),
                    )
                }
                .sortedBy { it.title.lowercase() }
        }
    }

    /**
     * **Todas** las listas de la consola, una detrás de otra: primero NTSC-U, después NTSC-J, al
     * final PAL.
     *
     * La consola es una sola máquina aunque haya tenido dos nombres, así que su catálogo se lee de
     * corrido y no cambiando de solapa. El mismo juego puede aparecer más de una vez —la edición
     * americana y la japonesa son dos piezas distintas, con su tapa, su año y su editora— y eso es
     * a propósito: son dos cosas que se pueden tener por separado.
     *
     * Se recorren las regiones **declaradas** y no las tres: [Platform.catalogFor] tiene respaldo, y
     * pedirle PAL a la TurboGrafx devolvería el archivo americano de nuevo.
     */
    fun entriesAllRegions(platform: Platform): List<CatalogEntry> {
        val regiones = platform.declaredRegions()
        if (regiones.isEmpty()) return entries(platform)
        return regiones.flatMap { entries(platform, it) }
    }

    /** Cuántos juegos suman todas las listas de la consola. */
    fun countAllRegions(platform: Platform): Int =
        platform.totalCount().takeIf { it > 0 } ?: entriesAllRegions(platform).size

    /**
     * Mismo buscador difuso que la colección: tolera acentos, orden de palabras y typos.
     *
     * Busca sobre las listas juntas. Con texto tipeado el resultado sale **ordenado por relevancia
     * y sin separar por región**: quien escribe "castlevania" quiere ver los Castlevania, no
     * recorrer tres bloques para encontrarlos.
     */
    fun searchAllRegions(platform: Platform, query: String, limit: Int = 60): List<CatalogEntry> =
        GameSearch.rank(entriesAllRegions(platform), query, limit) { it.title }
}
