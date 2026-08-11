package com.gmoqa.fullset.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DTO de `catalogs/platforms.json`. */
@Serializable
private data class PlatformDto(
    val id: String = "",
    val name: String = "",
    @SerialName("catalog") val catalogFile: String = "",
    /** Catálogos por región (label → archivo). Si está vacío, se usa el legacy `catalog` como NTSC-U. */
    val catalogs: Map<String, String> = emptyMap(),
    val libretroRepo: String = "",
    val enabled: Boolean = false,
    /** Cuántos juegos tiene cada región. Lo escribe `tools/platform_counts.py`. */
    val counts: Map<String, Int> = emptyMap(),
    val info: PlatformInfoDto? = null,
)

@Serializable
private data class PlatformInfoDto(
    val manufacturer: String = "",
    val generation: Int? = null,
    val handheld: Boolean = false,
    val media: String = "",
    val released: Map<String, Int> = emptyMap(),
    val discontinued: Int? = null,
    val unitsSold: String = "",
    val cpu: String = "",
    val description: String = "",
)

/**
 * Carga y consulta las plataformas declaradas en `catalogs/platforms.json`. Multiplataforma.
 *
 * [readAsset] es inyectable para tests (leer los assets del repo desde la JVM); por defecto usa la
 * frontera `expect/actual` real de cada plataforma.
 */
class PlatformRegistry(private val readAsset: (String) -> String? = ::readTextAsset) {

    private val platforms: List<Platform> by lazy {
        val text = readAsset("catalogs/platforms.json") ?: return@lazy emptyList()
        AppJson.decodeFromString<List<PlatformDto>>(text).map { dto ->
            // Soporta ambos formatos: el mapa `catalogs` nuevo, o el legacy `catalog` (= NTSC-U).
            val cats = dto.catalogs.ifEmpty {
                if (dto.catalogFile.isNotBlank()) mapOf("NTSC-U" to dto.catalogFile) else emptyMap()
            }
            Platform(
                dto.id, dto.name, cats["NTSC-U"] ?: dto.catalogFile, dto.libretroRepo, dto.enabled,
                info = dto.info?.let {
                    // Por nombre y no por posición: agregar un campo en el medio de `PlatformInfo`
                    // reasignaba en silencio los que venían después.
                    PlatformInfo(
                        manufacturer = it.manufacturer,
                        generation = it.generation,
                        handheld = it.handheld,
                        media = it.media,
                        released = it.released,
                        discontinued = it.discontinued,
                        unitsSold = it.unitsSold,
                        cpu = it.cpu,
                        description = it.description,
                    )
                },
                catalogs = cats,
                counts = dto.counts,
            )
        }
    }

    fun all(): List<Platform> = platforms
}
