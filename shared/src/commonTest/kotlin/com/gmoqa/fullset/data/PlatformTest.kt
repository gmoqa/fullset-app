package com.gmoqa.fullset.data

import com.gmoqa.fullset.ui.selectableRegions
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun ofreceElegirRegionSoloSiHayMasDeUna() {
        val sega = platform(
            mapOf(
                "NTSC-U" to "catalogs/genesis-usa.json",
                "NTSC-J" to "catalogs/genesis-jp.json",
                "PAL" to "catalogs/genesis-eu.json",
            )
        )
        assertEquals(
            listOf(RegionFilter.NTSC_U, RegionFilter.NTSC_J, RegionFilter.PAL),
            sega.selectableRegions(),
            "las tres, en el orden del enum",
        )
    }

    @Test
    fun sinAlternativaNoSeOfreceElector() {
        // Una consola de una sola región (SG-1000) o con el formato viejo de un único catálogo:
        // mostrar un selector sería mentir, porque todas las opciones darían la misma lista.
        assertTrue(platform(mapOf("NTSC-J" to "catalogs/sg-1000-jp.json")).selectableRegions().isEmpty())
        assertTrue(platform().selectableRegions().isEmpty(), "formato legacy sin mapa")
    }
}
