package com.gmoqa.fullset.data

import kotlinx.serialization.Serializable

/** DTO del catálogo JSON. Esquema estándar: title/platform/region/year/publisher/genre/slug/serial/coverUrl. */
@Serializable
private data class CatalogEntryDto(
    val title: String = "",
    val platform: String = "",
    val region: String = "",
    val year: Int? = null,
    val releaseDate: String = "",
    val publisher: String = "",
    val genre: String = "",
    val slug: String = "",
    val serial: String = "",
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
                        publisher = dto.publisher.trim(),
                        genre = dto.genre.trim(),
                        serial = dto.serial.trim(),
                        coverUrl = dto.coverUrl.trim(),
                    )
                }
                .sortedBy { it.title.lowercase() }
        }
    }

    /** Mismo buscador difuso que la colección: tolera acentos, orden de palabras y typos. */
    fun search(platform: Platform, region: RegionFilter, query: String, limit: Int = 60): List<CatalogEntry> =
        GameSearch.rank(entries(platform, region), query, limit) { it.title }
}
