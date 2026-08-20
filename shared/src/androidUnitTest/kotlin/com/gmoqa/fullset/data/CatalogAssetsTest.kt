package com.gmoqa.fullset.data

import java.io.File
import com.gmoqa.fullset.domain.GameSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrato entre los datos y la app: parsea los assets **reales** del repo con el mismo código que
 * usa la app en el dispositivo. Los catálogos se editan a mano y con scripts, así que este test es
 * la red que avisa si un archivo queda mal formado, mal enlazado o con la región cambiada — sin
 * tener que instalar el APK para descubrirlo.
 */
class CatalogAssetsTest {

    // El test corre con working dir = shared/; los assets viven en el módulo :app.
    private val assets = File("../data")
    private val readAsset: (String) -> String? = { path ->
        File(assets, path).takeIf { it.isFile }?.readText()
    }

    private val registry = PlatformRegistry(readAsset)
    private val catalog = GameCatalog(readAsset)

    private fun platforms() = registry.all().also {
        assertTrue(it.isNotEmpty(), "no se pudo leer catalogs/platforms.json desde ${assets.absolutePath}")
    }

    @Test
    fun todaPlataformaHabilitadaCargaSuCatalogo() {
        for (platform in platforms().filter { it.enabled }) {
            for (region in RegionFilter.entries.filter { it.supported }) {
                val file = platform.catalogFor(region)
                if (file.isBlank()) continue
                // **Puede estar vacío y estar bien.** Las consolas modernas arrancan con una lista
                // incompleta que se completa de a poco, y llega a los teléfonos por
                // `CatalogSync` sin publicar un APK. Lo que no se tolera es que el archivo
                // declarado no exista o no parsee: eso sí es una consola rota.
                assertTrue(
                    readAsset(file) != null,
                    "${platform.name} / ${region.label}: falta el archivo '$file'",
                )
                catalog.entries(platform, region)
            }
        }
    }

    /**
     * Una clave de `catalogs` es `"PAL"` o `"PAL/BR"`: región y, opcionalmente, territorio. Bajo PAL
     * conviven Europa, Brasil y Australia, que no son la misma lista.
     */
    private fun partes(label: String): Pair<RegionFilter, String?> {
        val region = RegionFilter.entries.first { it.label == label.substringBefore("/") }
        return region to label.substringAfter("/", "").ifBlank { null }
    }

    @Test
    fun cadaCatalogoDeclaraLaPlataformaYRegionQueLeCorresponde() {
        for (platform in platforms()) {
            for ((label, file) in platform.catalogs) {
                val text = readAsset(file)
                assertTrue(text != null, "${platform.name}: falta el archivo '$file'")
                val (region, territory) = partes(label)
                val entries = catalog.entries(platform, region, territory)
                // Un catálogo aún sin llenar no tiene de qué declarar región.
                if (entries.isEmpty()) continue
                // `region` sale del propio catálogo; si no coincide, el archivo está mal mapeado.
                // El territorio **no** viaja en la entrada: un cartucho brasileño sigue siendo PAL,
                // y de qué territorio es lo dice la lista en la que está.
                val regions = entries.map { it.region }.toSet()
                assertEquals(setOf(region.label), regions, "$file está mapeado como $label")
            }
        }
    }

    @Test
    fun losSlugsSonUnicosDentroDeCadaCatalogo() {
        // El slug es la identidad del juego: si se repite, los overrides y el merge de backup
        // apuntarían al juego equivocado.
        for (platform in platforms()) {
            for ((label, _) in platform.catalogs) {
                val (region, territory) = partes(label)
                val slugs = catalog.entries(platform, region, territory)
                    .map { it.slug }.filter { it.isNotBlank() }
                val dupes = slugs.groupingBy { it }.eachCount().filter { it.value > 1 }
                assertTrue(dupes.isEmpty(), "${platform.name}/$label: slugs repetidos ${dupes.keys}")
            }
        }
    }

    @Test
    fun lasConsolasSegaCubrenLasTresRegiones() {
        // Lo que cerramos: 8 consolas Sega con NTSC-U, NTSC-J y PAL. La SG-1000 es la excepción
        // real: solo salió en Japón (en Europa su lugar lo ocupó el Master System).
        val sega = platforms().filter { it.name.startsWith("Sega") || it.name in setOf("Dreamcast", "SG-1000") }
        assertEquals(8, sega.size, "se esperaban 8 consolas Sega, hay ${sega.map { it.name }}")
        for (platform in sega) {
            assertTrue(platform.enabled, "${platform.name} debería estar habilitada")
            // Se comparan las **regiones**, no las claves crudas: siete consolas Sega declaran
            // además territorios (`PAL/BR`, `PAL/AU`) porque Brasil y Australia no son Europa.
            val expected =
                if (platform.id == "sg-1000") setOf("NTSC-J") else setOf("NTSC-U", "NTSC-J", "PAL")
            assertEquals(
                expected,
                platform.catalogs.keys.map { it.substringBefore("/") }.toSet(),
                "regiones de ${platform.name}",
            )
        }
    }

    @Test
    fun palEstaSoportadaYCargaJuegos() {
        // PAL estuvo declarada pero sin datos por mucho tiempo; ahora tiene catálogos de verdad.
        assertTrue(RegionFilter.PAL.supported, "PAL debería estar soportada")
        val genesis = platforms().first { it.id == "sega-genesis" }
        val pal = catalog.entries(genesis, RegionFilter.PAL)
        assertTrue(pal.size > 500, "Genesis PAL trajo solo ${pal.size} juegos")
        // El catálogo PAL une Europa con Australia y Brasil (los Tec Toy en portugués), así que
        // tiene títulos que no existen en la lista americana.
        val usa = catalog.entries(genesis, RegionFilter.NTSC_U).map { it.slug }.toSet()
        assertTrue(pal.any { it.slug !in usa }, "PAL no aporta ningún título propio")
    }

    /**
     * El catálogo de una consola se lee **de corrido**: sus regiones una detrás de otra, en orden
     * (NTSC-U, NTSC-J, PAL). Antes se navegaba con un selector y la lista mostraba una sola.
     *
     * Se verifica sobre los catálogos reales porque lo que puede romperse es el **orden** y que no
     * falte ni sobre ninguna región, y eso depende de cómo esté escrito `platforms.json`.
     */
    @Test
    fun `el catalogo junta todas las regiones en orden`() {
        val catalog = GameCatalog(readAsset)
        val saturn = registry.all().first { it.id == "sega-saturn" }

        val todas = catalog.entriesAllRegions(saturn)
        val usa = catalog.entries(saturn, RegionFilter.NTSC_U)
        val jp = catalog.entries(saturn, RegionFilter.NTSC_J)
        val pal = catalog.entries(saturn, RegionFilter.PAL)

        assertEquals(usa.size + jp.size + pal.size, todas.size, "tienen que estar las tres enteras")
        assertEquals(usa.map { it.slug }, todas.take(usa.size).map { it.slug }, "NTSC-U va primero")
        assertEquals(
            jp.map { it.slug },
            todas.drop(usa.size).take(jp.size).map { it.slug },
            "NTSC-J va segundo",
        )
        // Los bloques salen en el orden de RegionFilter, sin intercalarse.
        assertEquals(
            listOf("NTSC-U", "NTSC-J", "PAL"),
            todas.map { it.region }.distinct(),
            "las regiones no se mezclan entre sí",
        )
    }

    /**
     * Una consola que **no declara** una región no la trae repetida.
     *
     * `catalogFor` tiene respaldo: pedirle PAL a la TurboGrafx devuelve el archivo americano. Al
     * juntar las listas eso metería el catálogo de EE.UU. dos veces, la segunda bajo la bandera
     * equivocada. Por eso se recorren las regiones **declaradas** y no las tres.
     */
    @Test
    fun `una region no declarada no duplica el catalogo`() {
        val catalog = GameCatalog(readAsset)
        val tg = registry.all().first { it.id == "turbografx-16" }

        assertEquals(
            listOf(RegionFilter.NTSC_U, RegionFilter.NTSC_J),
            tg.declaredRegions(),
            "la TurboGrafx no tuvo lanzamiento PAL",
        )
        val todas = catalog.entriesAllRegions(tg)
        assertEquals(
            catalog.entries(tg, RegionFilter.NTSC_U).size + catalog.entries(tg, RegionFilter.NTSC_J).size,
            todas.size,
            "no debe aparecer una tercera copia por el respaldo de catalogFor",
        )
        assertTrue(todas.none { it.region == "PAL" }, "ninguna entrada puede decir PAL")
    }

    /**
     * Buscando, el resultado sale por relevancia sobre **todas** las listas: un exclusivo japonés
     * tiene que aparecer sin que haga falta cambiar de región primero.
     */
    @Test
    fun `cada region trae sus exclusivos y la busqueda se queda en la elegida`() {
        // Al agregar un juego elegís **una** región y ves solo esa lista: estás eligiendo una pieza
        // concreta, no explorando la consola. Antes se buscaba sobre las tres juntas, y eso hacía
        // aparecer la edición japonesa mientras estabas en la americana.
        val catalog = GameCatalog(readAsset)
        val saturn = registry.all().first { it.id == "sega-saturn" }

        val jp = catalog.entries(saturn, RegionFilter.NTSC_J)
        val usa = catalog.entries(saturn, RegionFilter.NTSC_U)
        val soloJp = jp.map { it.slug }.toSet() - usa.map { it.slug }.toSet()
        assertTrue(soloJp.isNotEmpty(), "Saturn tiene exclusivos japoneses")

        val exclusivo = jp.first { it.slug in soloJp }
        assertTrue(
            GameSearch.rank(jp, exclusivo.title, limit = 60) { it.title }.any { it.slug == exclusivo.slug },
            "'${exclusivo.title}' tiene que aparecer buscando en su propia región",
        )
        assertTrue(
            usa.none { it.slug == exclusivo.slug },
            "y no en la americana, que es una lista distinta",
        )
    }

    /**
     * Una consola que solo salió en un mercado tiene su catálogo bajo esa región, y `catalogFile`
     * —que es el americano— queda vacío. Preguntar por él mandaba a la SG-1000 a "cargar a mano"
     * teniendo sus juegos catalogados.
     */
    @Test
    fun `una consola de una sola region resuelve su catalogo`() {
        val sg = registry.all().first { it.id == "sg-1000" }
        assertEquals("", sg.catalogFile, "la SG-1000 no tiene catálogo NTSC-U")
        assertTrue(sg.catalogFor(RegionFilter.NTSC_J).isNotBlank(), "pero sí japonés")
        assertTrue(
            GameCatalog(readAsset).entries(sg, RegionFilter.NTSC_J).isNotEmpty(),
            "y ese catálogo trae juegos",
        )
    }
}
