package com.gmoqa.fullset.data

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
