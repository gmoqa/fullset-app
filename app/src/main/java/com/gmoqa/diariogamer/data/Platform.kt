package com.gmoqa.diariogamer.data

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Los modelos `Platform` y `PlatformInfo` viven en el módulo `:shared` (commonMain). Acá quedan
// solo la carga desde assets (Android) y los DTOs de deserialización.

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

/** Carga y consulta las plataformas declaradas en `config/platforms.json`. */
class PlatformRegistry(context: Context) {

    private val appContext = context.applicationContext

    private val platforms: List<Platform> by lazy {
        val text = appContext.assets.open("config/platforms.json")
            .bufferedReader().use { it.readText() }
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
