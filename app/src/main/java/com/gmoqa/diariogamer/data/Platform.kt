package com.gmoqa.diariogamer.data

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Plataforma configurable: se define por datos, no por código. Para agregar una consola basta con
 * una entrada en `config/platforms.json` + su JSON de catálogo en `assets/catalogs/`.
 */
data class Platform(
    val id: String,
    val name: String,
    val catalogFile: String,
    val libretroRepo: String,
    val enabled: Boolean,
    /** Ficha técnica (año por región, specs). Null si la entrada no la trae. */
    val info: PlatformInfo? = null,
)

/**
 * Ficha técnica de una consola (bloque `info` en `config/platforms.json`). Todos los campos son
 * opcionales: se muestra solo lo presente. [released] mapea región → año, con las mismas keys que
 * [RegionFilter] (`ntsc-j`/`ntsc`/`pal`).
 */
data class PlatformInfo(
    val manufacturer: String = "",
    val generation: Int? = null,
    val media: String = "",
    val released: Map<String, Int> = emptyMap(),
    val discontinued: Int? = null,
    val unitsSold: String = "",
    val cpu: String = "",
    val description: String = "",
) {
    /** Año de lanzamiento en la región pedida (o el más temprano disponible como respaldo). */
    fun releaseYear(region: RegionFilter): Int? =
        released[region.key] ?: released.values.minOrNull()
}

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
