package com.gmoqa.fullset.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Elección de catálogo por región, con fallback a NTSC-U (catalogFile). */
class PlatformTest {

    private fun platform(catalogs: Map<String, String> = emptyMap()) = Platform(
        id = "genesis",
        name = "Sega Genesis",
        catalogFile = "catalogs/genesis-usa.json",
        libretroRepo = "Sega_-_Mega_Drive_-_Genesis",
        enabled = true,
        catalogs = catalogs,
    )

    @Test
    fun regionConCatalogoPropio() {
        val p = platform(
            mapOf(
                "NTSC-U" to "catalogs/genesis-usa.json",
                "NTSC-J" to "catalogs/genesis-jp.json",
            )
        )
        assertEquals("catalogs/genesis-jp.json", p.catalogFor(RegionFilter.NTSC_J))
        assertEquals("catalogs/genesis-usa.json", p.catalogFor(RegionFilter.NTSC_U))
    }

    @Test
    fun regionSinCatalogoCaeANtscU() {
        val p = platform(mapOf("NTSC-U" to "catalogs/genesis-usa.json"))
        assertEquals("catalogs/genesis-usa.json", p.catalogFor(RegionFilter.NTSC_J))
        assertEquals("catalogs/genesis-usa.json", p.catalogFor(RegionFilter.PAL))
    }

    @Test
    fun formatoLegacySinMapa() {
        // Plataformas todavía con `"catalog": "..."` a secas: el mapa queda vacío.
        val p = platform()
        assertEquals("catalogs/genesis-usa.json", p.catalogFor(RegionFilter.NTSC_U))
        assertEquals("catalogs/genesis-usa.json", p.catalogFor(RegionFilter.NTSC_J))
    }

    @Test
    fun entradaVaciaNoCuenta() {
        // Un "" en el mapa (config a medio migrar) no debe elegirse: cae al default.
        val p = platform(mapOf("NTSC-J" to ""))
        assertEquals("catalogs/genesis-usa.json", p.catalogFor(RegionFilter.NTSC_J))
    }

    @Test
    fun consolaDeUnaSolaRegionUsaSuUnicoCatalogo() {
        // La SG-1000 solo salió en Japón: en modo NTSC-U mostramos su catálogo japonés, no vacío.
        val jpOnly = Platform(
            id = "sg-1000", name = "SG-1000", catalogFile = "",
            libretroRepo = "Sega_-_SG-1000", enabled = true,
            catalogs = mapOf("NTSC-J" to "catalogs/sg-1000-jp.json"),
        )
        assertEquals("catalogs/sg-1000-jp.json", jpOnly.catalogFor(RegionFilter.NTSC_J))
        assertEquals("catalogs/sg-1000-jp.json", jpOnly.catalogFor(RegionFilter.NTSC_U))
    }

    @Test
    fun sinNingunCatalogoDevuelveVacio() {
        // Plataformas modernas (PS5): se cargan a mano, no tienen lista.
        val none = Platform(
            id = "playstation-5", name = "PlayStation 5", catalogFile = "",
            libretroRepo = "", enabled = true,
        )
        assertEquals("", none.catalogFor(RegionFilter.NTSC_U))
    }

    @Test
    fun elConteoSaleDelConfigYNoDelCatalogo() {
        // Contarlo abriendo el catálogo congelaba la grilla de alta: quince JSON, 9,4 MB, en el
        // hilo principal. Viene precalculado, y sin conteo (la PS5) devuelve null —no cero— para
        // que la UI pueda decir "se carga a mano" en vez de "0 games".
        val genesis = Platform(
            id = "sega-genesis", name = "Sega Genesis", catalogFile = "catalogs/genesis-usa.json",
            libretroRepo = "", enabled = true,
            counts = mapOf("NTSC-U" to 711, "NTSC-J" to 435, "PAL" to 747),
        )
        assertEquals(711, genesis.countFor(RegionFilter.NTSC_U))
        assertEquals(435, genesis.countFor(RegionFilter.NTSC_J))

        val ps5 = Platform(
            id = "playstation-5", name = "PlayStation 5", catalogFile = "",
            libretroRepo = "", enabled = true,
        )
        assertNull(ps5.countFor(RegionFilter.NTSC_U))
    }

    @Test
    fun soloTieneCatalogoLaQueTraeAlgunaLista() {
        // `hasCatalog` es lo que decide si una consola aparece en el alta de la colección. Una con
        // lista en una sola región cuenta: si mirara solo la región activa, la SG-1000 desaparecería
        // de la grilla al pasar a NTSC-U.
        val jpOnly = Platform(
            id = "sg-1000", name = "SG-1000", catalogFile = "",
            libretroRepo = "Sega_-_SG-1000", enabled = true,
            catalogs = mapOf("NTSC-J" to "catalogs/sg-1000-jp.json"),
        )
        val ps5 = Platform(
            id = "playstation-5", name = "PlayStation 5", catalogFile = "",
            libretroRepo = "", enabled = true,
        )
        assertTrue(jpOnly.hasCatalog)
        assertFalse(ps5.hasCatalog)
    }

    @Test
    fun declaraSusRegionesEnOrden() {
        val sega = platform(
            mapOf(
                "NTSC-U" to "catalogs/genesis-usa.json",
                "NTSC-J" to "catalogs/genesis-jp.json",
                "PAL" to "catalogs/genesis-eu.json",
            )
        )
        assertEquals(
            listOf(RegionFilter.NTSC_U, RegionFilter.NTSC_J, RegionFilter.PAL),
            sega.declaredRegions(),
            "las tres, en el orden del enum: es el orden en que se leen las listas",
        )
    }

    @Test
    fun soloDeclaraLasRegionesQueTiene() {
        // Lo que importa acá es que **no haya respaldo**: `catalogFor` sí lo tiene —pedirle PAL a
        // una consola que no lo tuvo devuelve el americano— y al juntar las listas eso metería el
        // mismo catálogo dos veces, la segunda bajo una bandera que nunca existió.
        val soloJp = platform(mapOf("NTSC-J" to "catalogs/sg-1000-jp.json"))
        assertEquals(listOf(RegionFilter.NTSC_J), soloJp.declaredRegions())
        // `catalogFor(PAL)` igual devuelve algo —el `catalogFile`, que es el americano— aunque esta
        // consola no haya tenido PAL. Ese respaldo está bien para no dejar una vista vacía, y es
        // exactamente por lo que no sirve para juntar listas: metería el mismo archivo dos veces.
        assertEquals("catalogs/genesis-usa.json", soloJp.catalogFor(RegionFilter.PAL))
        assertTrue(platform().declaredRegions().isEmpty(), "formato legacy sin mapa")
    }
}
