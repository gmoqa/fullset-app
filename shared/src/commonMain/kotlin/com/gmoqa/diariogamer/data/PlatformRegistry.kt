package com.gmoqa.diariogamer.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DTO de `config/platforms.json`. */
@Serializable
private data class PlatformDto(
    val id: String = "",
    val name: String = "",
    @SerialName("catalog") val catalogFile: String = "",
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

/** Carga y consulta las plataformas declaradas en `config/platforms.json`. Multiplataforma. */
class PlatformRegistry {

    private val platforms: List<Platform> by lazy {
        val text = readTextAsset("config/platforms.json") ?: return@lazy emptyList()
        AppJson.decodeFromString<List<PlatformDto>>(text).map { dto ->
            Platform(
                dto.id, dto.name, dto.catalogFile, dto.libretroRepo, dto.enabled,
                info = dto.info?.let {
                    PlatformInfo(
                        it.manufacturer, it.generation, it.media, it.released,
                        it.discontinued, it.unitsSold, it.cpu, it.description,
                    )
                },
            )
        }
    }

    fun all(): List<Platform> = platforms
}
