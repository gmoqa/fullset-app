package com.gmoqa.fullset.data

/**
 * Plataforma configurable: se define por datos, no por código. Para agregar una consola basta con
 * una entrada en `config/platforms.json` + su JSON de catálogo en `assets/catalogs/`. La carga
 * (que lee assets) vive en `PlatformRegistry`, del lado de cada plataforma.
 */
data class Platform(
    val id: String,
    val name: String,
    /** Catálogo por defecto (NTSC-U): compat + fallback cuando una región no tiene su propia lista. */
    val catalogFile: String,
    val libretroRepo: String,
    val enabled: Boolean,
    /** Ficha técnica (año por región, specs). Null si la entrada no la trae. */
    val info: PlatformInfo? = null,
    /** Catálogos por región (label de [RegionFilter] → archivo): `{"NTSC-U": "...", "PAL": "..."}`. */
    val catalogs: Map<String, String> = emptyMap(),
) {
    /**
     * Archivo de catálogo para [region].
     *
     * Cae a [catalogFile] (NTSC-U) si esa región no tiene lista propia, y si tampoco hay default
     * usa cualquiera que exista: pasa con las consolas de una sola región (la SG-1000 solo salió en
     * Japón), donde mostrar su catálogo japonés es mejor que mostrar una consola vacía.
     */
    fun catalogFor(region: RegionFilter): String =
        catalogs[region.label]?.takeIf { it.isNotBlank() }
            ?: catalogFile.takeIf { it.isNotBlank() }
            ?: catalogs.values.firstOrNull { it.isNotBlank() }
            ?: ""
}

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
