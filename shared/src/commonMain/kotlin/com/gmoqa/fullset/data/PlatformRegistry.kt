package com.gmoqa.fullset.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DTO de `config/platforms.json`. */
@Serializable
private data class PlatformDto(
    val id: String = "",
    val name: String = "",
    @SerialName("catalog") val catalogFile: String = "",
    /** Catálogos por región (label → archivo). Si está vacío, se usa el legacy `catalog` como NTSC-U. */
    val catalogs: Map<String, String> = emptyMap(),
    val libretroRepo: String = "",
    val enabled: Boolean = false,
    val info: PlatformInfoDto? = null,
)

@Serializable
private data class PlatformInfoDto(
    val manufacturer: String = "",
    val generation: Int? = null,
    val media: String = "",
    val released: Map<String, Int> = emptyMap(),
    val discontinued: Int? = null,
    val unitsSold: String = "",
    val cpu: String = "",
    val description: String = "",
)

/**
 * Carga y consulta las plataformas declaradas en `config/platforms.json`. Multiplataforma.
 *
 * [readAsset] es inyectable para tests (leer los assets del repo desde la JVM); por defecto usa la
 * frontera `expect/actual` real de cada plataforma.
 */
class PlatformRegistry(private val readAsset: (String) -> String? = ::readTextAsset) {

    private val platforms: List<Platform> by lazy {
        val text = readAsset("config/platforms.json") ?: return@lazy emptyList()
        AppJson.decodeFromString<List<PlatformDto>>(text).map { dto ->
            // Soporta ambos formatos: el mapa `catalogs` nuevo, o el legacy `catalog` (= NTSC-U).
            val cats = dto.catalogs.ifEmpty {
                if (dto.catalogFile.isNotBlank()) mapOf("NTSC-U" to dto.catalogFile) else emptyMap()
            }
            Platform(
                dto.id, dto.name, cats["NTSC-U"] ?: dto.catalogFile, dto.libretroRepo, dto.enabled,
                info = dto.info?.let {
                    PlatformInfo(
                        it.manufacturer, it.generation, it.media, it.released,
                        it.discontinued, it.unitsSold, it.cpu, it.description,
                    )
                },
                catalogs = cats,
            )
        }
    }

    fun all(): List<Platform> = platforms
}
