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
    private val assets = File("../app/src/main/assets")
    private val readAsset: (String) -> String? = { path ->
        File(assets, path).takeIf { it.isFile }?.readText()
    }

    private val registry = PlatformRegistry(readAsset)
    private val catalog = GameCatalog(readAsset)

    private fun platforms() = registry.all().also {
        assertTrue(it.isNotEmpty(), "no se pudo leer config/platforms.json desde ${assets.absolutePath}")
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
    fun lasConsolasSegaTienenAmbasRegiones() {
        // Lo que acabamos de cerrar: 8 consolas Sega con NTSC-U y NTSC-J (la SG-1000 solo salió
        // en Japón, así que va con su única lista).
        val sega = platforms().filter { it.name.startsWith("Sega") || it.name in setOf("Dreamcast", "SG-1000") }
        assertEquals(8, sega.size, "se esperaban 8 consolas Sega, hay ${sega.map { it.name }}")
        for (platform in sega) {
            assertTrue(platform.enabled, "${platform.name} debería estar habilitada")
            val expected = if (platform.id == "sg-1000") setOf("NTSC-J") else setOf("NTSC-U", "NTSC-J")
            assertEquals(expected, platform.catalogs.keys, "regiones de ${platform.name}")
        }
    }
}
