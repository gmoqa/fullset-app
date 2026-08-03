package com.gmoqa.fullset.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Aspecto reservado para la carátula. Importa porque ese número fija el alto del tile **antes** de
 * que la imagen cargue: si no coincide con la imagen real, el placeholder queda más alto que la
 * carátula y se ve un hueco.
 */
class CoverAspectTest {

    @Test
    fun elMismoJuegoCambiaDeCajaSegunElMercado() {
        // Saturn: jewel case cuadrado en Japón, caja alta y angosta en Estados Unidos. Medido sobre
        // las imágenes reales de cada catálogo.
        val jp = coverAspectRatio("Sega Saturn", "NTSC-J")
        val us = coverAspectRatio("Sega Saturn", "NTSC-U")
        assertEquals(1.00f, jp)
        assertEquals(0.60f, us)
        assertTrue(jp > us, "la japonesa es más ancha que alta respecto de la americana")
    }

    @Test
    fun sinRegionCaeAlValorGeneralDeLaPlataforma() {
        // Los juegos cargados a mano no tienen región: mejor el valor típico que ninguno.
        assertEquals(coverAspectRatio("Sega Genesis"), coverAspectRatio("Sega Genesis", ""))
        assertEquals(0.71f, coverAspectRatio("Sega Genesis"))
    }

    @Test
    fun unaRegionSinDatoUsaElValorDeLaPlataforma() {
        // Genesis no declara aspecto por región (el packaging fue parejo): las tres dan lo mismo.
        val general = coverAspectRatio("Sega Genesis")
        listOf("NTSC-U", "NTSC-J", "PAL").forEach {
            assertEquals(general, coverAspectRatio("Sega Genesis", it))
        }
    }

    @Test
    fun unaPlataformaDesconocidaNoRompe() {
        // Una consola nueva sin estilo declarado: vertical por defecto, nunca 0 (dividiría por cero).
        val fallback = coverAspectRatio("Consola Inventada", "NTSC-U")
        assertTrue(fallback > 0f)
        assertEquals(0.72f, fallback)
    }
}
