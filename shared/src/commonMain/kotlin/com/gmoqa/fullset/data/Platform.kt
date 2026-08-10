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

    /**
     * Año en que la consola salió **por primera vez**, en el mercado que fuera.
     *
     * A propósito no depende de la región que estés mirando: la posición de una consola en la
     * historia es un hecho sobre la consola, no sobre tu mercado. Si ordenara por el año local, la
     * grilla se reacomodaría sola al cambiar la región por defecto, que se lee como un error.
     */
    val debutYear: Int? get() = info?.released?.values?.minOrNull()

    /**
     * Las regiones que esta consola **declara**, en el orden en que se muestran: NTSC-U, NTSC-J, PAL.
     *
     * A diferencia de [catalogFor], acá **no hay respaldo**: si la TurboGrafx no declara PAL, PAL no
     * está. Eso importa cuando se juntan las listas de todas las regiones, porque el respaldo
     * devolvería el archivo americano otra vez y el mismo juego aparecería dos veces bajo dos
     * banderas distintas.
     */
    fun declaredRegions(): List<RegionFilter> = RegionFilter.entries.filter { it.label in catalogs }

    /**
     * Cuántos juegos suman **todas** sus listas. Es lo que se ve al entrar, así que es lo que tiene
     * que decir el cubo de la grilla: mostrar solo el americano prometía 93 y adentro había 381.
     */
    fun totalCount(): Int = declaredRegions().sumOf { countFor(it) ?: 0 }
}

/** Una generación de consolas y las que le pertenecen, ya ordenadas por año de debut. */
data class PlatformGeneration(
    /** `null` = la entrada no declara generación; van juntas al final. */
    val generation: Int?,
    val platforms: List<Platform>,
) {
    val firstYear: Int? get() = platforms.mapNotNull { it.debutYear }.minOrNull()
    val lastYear: Int? get() = platforms.mapNotNull { it.debutYear }.maxOrNull()
}

/**
 * Las plataformas por **generación** y, dentro de cada una, por año de debut.
 *
 * El orden de `platforms.json` es por fabricante —las cuatro Nintendo, después las ocho Sega, las
 * Sony, las NEC—, que es cómodo para mantener el archivo y malo para elegir: pone la NES de 1983
 * al lado de la GameCube de 2001 y manda la Master System, su contemporánea, veinte casilleros más
 * abajo. Quien busca "la consola de esa época" tiene que saber de antemano quién la fabricó.
 *
 * Los empates de año se resuelven solos: `sortedWith` es **estable**, así que dos consolas del
 * mismo año conservan el orden del archivo en vez de barajarse en cada build.
 */
fun List<Platform>.groupedByGeneration(): List<PlatformGeneration> =
    sortedWith(
        compareBy(
            { it.info?.generation ?: Int.MAX_VALUE },
            { it.debutYear ?: Int.MAX_VALUE },
            // Desempate por fabricante: cuando dos consolas comparten año, dejarlas al azar separa
            // una máquina de su propio accesorio. La TurboGrafx-CD (1988) y la Genesis (1988)
            // empatan, y sin esto la CD caía entre la Genesis y la SNES, lejos de la TurboGrafx-16
            // con la que forma un solo aparato.
            { it.info?.manufacturer ?: "" },
        ),
    )
        .groupBy { it.info?.generation }
        .map { (gen, list) -> PlatformGeneration(gen, list) }

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
