package com.gmoqa.fullset.data

import java.io.File
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
                // Las modernas (PS5) no tienen lista: se cargan a mano.
                if (file.isBlank()) continue
                val entries = catalog.entries(platform, region)
                assertTrue(
                    entries.isNotEmpty(),
                    "${platform.name} / ${region.label}: '$file' no cargó ninguna entrada",
                )
            }
        }
    }

    @Test
    fun cadaCatalogoDeclaraLaPlataformaYRegionQueLeCorresponde() {
        for (platform in platforms()) {
            for ((label, file) in platform.catalogs) {
                val text = readAsset(file)
                assertTrue(text != null, "${platform.name}: falta el archivo '$file'")
                val entries = catalog.entries(
                    platform,
                    RegionFilter.entries.first { it.label == label },
                )
                // `region` sale del propio catálogo; si no coincide, el archivo está mal mapeado.
                val regions = entries.map { it.region }.toSet()
                assertEquals(setOf(label), regions, "$file está mapeado como $label")
            }
        }
    }

    @Test
    fun losSlugsSonUnicosDentroDeCadaCatalogo() {
        // El slug es la identidad del juego: si se repite, los overrides y el merge de backup
        // apuntarían al juego equivocado.
        for (platform in platforms()) {
            for ((label, _) in platform.catalogs) {
                val region = RegionFilter.entries.first { it.label == label }
                val slugs = catalog.entries(platform, region).map { it.slug }.filter { it.isNotBlank() }
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
            val expected =
                if (platform.id == "sg-1000") setOf("NTSC-J") else setOf("NTSC-U", "NTSC-J", "PAL")
            assertEquals(expected, platform.catalogs.keys, "regiones de ${platform.name}")
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
     * Cambiar de región en "Add game" tiene que cambiar la lista, no solo el contador: buscás en el
     * mercado que elegiste. Se verifica sobre los catálogos reales, con y sin texto de búsqueda,
     * porque la lista vacía toma otro camino (`rank` devuelve los primeros N sin puntuar).
     */
    @Test
    fun `la busqueda del catalogo se limita a la region elegida`() {
        val catalog = GameCatalog(readAsset)
        val saturn = registry.all().first { it.id == "sega-saturn" }

        val usa = catalog.search(saturn, RegionFilter.NTSC_U, "")
        val jp = catalog.search(saturn, RegionFilter.NTSC_J, "")
        assertTrue(usa.isNotEmpty() && jp.isNotEmpty(), "ambas regiones deben traer juegos")
        assertTrue(usa.map { it.slug } != jp.map { it.slug }, "la lista sin buscar debe cambiar de región")

        // Un exclusivo japonés no puede aparecer buscando en el catálogo americano.
        val soloJp = catalog.entries(saturn, RegionFilter.NTSC_J).map { it.slug }.toSet() -
            catalog.entries(saturn, RegionFilter.NTSC_U).map { it.slug }.toSet()
        assertTrue(soloJp.isNotEmpty(), "Saturn tiene exclusivos japoneses")
        val titulo = catalog.entries(saturn, RegionFilter.NTSC_J).first { it.slug in soloJp }.title
        assertTrue(
            catalog.search(saturn, RegionFilter.NTSC_U, titulo).none { it.title == titulo },
            "buscar '$titulo' en NTSC-U no debe encontrar un exclusivo de NTSC-J",
        )
        assertTrue(
            catalog.search(saturn, RegionFilter.NTSC_J, titulo).any { it.title == titulo },
            "y en NTSC-J sí debe encontrarlo",
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
