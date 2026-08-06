package com.gmoqa.fullset.data

/**
 * Plataforma configurable: se define por datos, no por código. Para agregar una consola basta con
 * una entrada en `catalogs/platforms.json` + su JSON de catálogo al lado. La carga
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
    /**
     * Cuántos juegos tiene cada región, **precalculado**.
     *
     * Está acá y no se cuenta en el momento porque contarlo obliga a parsear el catálogo entero, y
     * la grilla de alta necesita el número de las quince consolas a la vez: eran 9,4 MB de JSON en
     * el hilo principal. Lo escribe `tools/platform_counts.py` y el lint verifica que no mienta.
     */
    val counts: Map<String, Int> = emptyMap(),
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

    /**
     * Si tiene lista de dónde elegir, en la región que sea.
     *
     * Es lo que separa una consola que se agrega a la **colección** de una que no: sin catálogo no
     * hay nada retro que buscar, y ese juego se carga a mano desde Playing (con su carátula de
     * SteamGridDB) porque en la práctica es digital. Hoy solo la PS5 cae de este lado.
     */
    val hasCatalog: Boolean get() = catalogFor(RegionFilter.entries.first()).isNotBlank()

    /**
     * Cuántos juegos hay en [region], sin abrir el catálogo. `null` si esta consola no tiene lista
     * (la PS5): quien lo muestre tiene que decir "se carga a mano", no "0 games".
     */
    fun countFor(region: RegionFilter): Int? = counts[region.label]
}

/**
 * Ficha técnica de una consola (bloque `info` en `catalogs/platforms.json`). Todos los campos son
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
