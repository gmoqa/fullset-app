package com.gmoqa.diariogamer.data

/**
 * Plataforma configurable: se define por datos, no por código. Para agregar una consola basta con
 * una entrada en `config/platforms.json` + su JSON de catálogo en `assets/catalogs/`. La carga
 * (que lee assets) vive en `PlatformRegistry`, del lado de cada plataforma.
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
