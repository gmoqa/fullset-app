package com.gmoqa.diariogamer.data

import android.content.Context
import kotlinx.serialization.Serializable

/** Una entrada del catálogo de una plataforma. */
data class CatalogEntry(
    val title: String,
    val subtitle: String,
    val slug: String,
    val region: String = "",
    val year: Int? = null,
    val publisher: String = "",
    val genre: String = "",
    /** URL de carátula horneada en el catálogo (Libretro). Vacío → se deriva del título. */
    val coverUrl: String = "",
)

/** DTO del catálogo JSON. Esquema estándar: title/platform/region/year/publisher/genre/slug/serial/coverUrl. */
@Serializable
private data class CatalogEntryDto(
    val title: String = "",
    val platform: String = "",
    val region: String = "",
    val year: Int? = null,
    val publisher: String = "",
    val genre: String = "",
    val slug: String = "",
    val serial: String = "",
    val coverUrl: String = "",
)

/**
 * Lee los catálogos JSON empaquetados en assets y permite buscarlos.
 * Las plataformas se declaran en `config/platforms.json` (ver [PlatformRegistry]); agregar una
 * consola es solo config + su JSON de catálogo.
 */
class GameCatalog(context: Context) {

    private val appContext = context.applicationContext
    private val cache = mutableMapOf<String, List<CatalogEntry>>()

    fun entries(platform: Platform): List<CatalogEntry> = cache.getOrPut(platform.id) {
        // Plataformas modernas (PS5…) no traen catálogo: se cargan a mano, sin lista que buscar.
        if (platform.catalogFile.isBlank()) return@getOrPut emptyList()
        val text = appContext.assets.open(platform.catalogFile)
            .bufferedReader().use { it.readText() }
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
                    publisher = dto.publisher.trim(),
                    genre = dto.genre.trim(),
                    coverUrl = dto.coverUrl.trim(),
                )
            }
            .sortedBy { it.title.lowercase() }
    }

    /** Mismo buscador difuso que la colección: tolera acentos, orden de palabras y typos. */
    fun search(platform: Platform, query: String, limit: Int = 60): List<CatalogEntry> =
        GameSearch.rank(entries(platform), query, limit) { it.title }
}
