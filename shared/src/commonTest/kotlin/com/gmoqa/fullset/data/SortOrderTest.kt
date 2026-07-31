package com.gmoqa.fullset.data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Orden de los juegos dentro de un estante (Collection y Backlog). */
class SortOrderTest {

    private var nextId = 1L
    private fun game(
        name: String,
        releaseDate: String = "",
        releaseYear: Int? = null,
        createdAt: Long = nextId,
    ) = Game(
        id = nextId++, name = name, platform = "Sega Genesis",
        coverUrl = "", coverPath = "", playing = false, backlog = false,
        createdAt = createdAt, releaseDate = releaseDate, releaseYear = releaseYear,
    )

    @Test
    fun porTituloIgnoraMayusculas() {
        val games = listOf(game("Zero Tolerance"), game("aladdin"), game("Ecco"))
        assertEquals(
            listOf("aladdin", "Ecco", "Zero Tolerance"),
            games.sortedBy(SortOrder.TITLE).map { it.name },
        )
    }

    @Test
    fun porAgregadoElMasRecientePrimero() {
        val old = game("Viejo", createdAt = 100)
        val new = game("Nuevo", createdAt = 900)
        assertEquals(listOf("Nuevo", "Viejo"), listOf(old, new).sortedBy(SortOrder.ADDED).map { it.name })
    }

    @Test
    fun porLanzamientoUsaLaFechaPrecisa() {
        // El caso que motivó guardar la fecha: tres juegos del mismo año.
        val games = listOf(
            game("Streets of Rage 2", releaseDate = "1992-12-20"),
            game("Kid Chameleon", releaseDate = "1992-04-20"),
            game("Sonic 2", releaseDate = "1992-11-21"),
        )
        assertEquals(
            listOf("Kid Chameleon", "Sonic 2", "Streets of Rage 2"),
            games.sortedBy(SortOrder.RELEASE).map { it.name },
        )
    }

    @Test
    fun porLanzamientoCaeAlAnioSiNoHayFecha() {
        val games = listOf(
            game("Sin fecha, 1994", releaseYear = 1994),
            game("Con fecha, 1991", releaseDate = "1991-06-21"),
        )
        assertEquals(
            listOf("Con fecha, 1991", "Sin fecha, 1994"),
            games.sortedBy(SortOrder.RELEASE).map { it.name },
        )
    }

    @Test
    fun mezclaPrecisionesDentroDelMismoAnio() {
        // "1992" (solo año) ordena antes que "1992-05" porque es el prefijo: no sabemos el mes.
        val games = listOf(
            game("Mayo", releaseDate = "1992-05"),
            game("Solo año", releaseDate = "1992"),
            game("Enero", releaseDate = "1992-01-15"),
        )
        assertEquals(
            listOf("Solo año", "Enero", "Mayo"),
            games.sortedBy(SortOrder.RELEASE).map { it.name },
        )
    }

    @Test
    fun losSinFechaVanAlFinal() {
        val games = listOf(
            game("Sin dato"),
            game("Con dato", releaseDate = "1995"),
            game("Otro sin dato"),
        )
        val names = games.sortedBy(SortOrder.RELEASE).map { it.name }
        assertEquals("Con dato", names.first())
        // Entre los que no tienen fecha, alfabético: el orden no debe quedar al azar.
        assertEquals(listOf("Otro sin dato", "Sin dato"), names.drop(1))
    }

    @Test
    fun aIgualFechaDesempataElTitulo() {
        val games = listOf(
            game("Zool", releaseDate = "1993-01-01"),
            game("Aladdin", releaseDate = "1993-01-01"),
        )
        assertEquals(listOf("Aladdin", "Zool"), games.sortedBy(SortOrder.RELEASE).map { it.name })
    }

    @Test
    fun laPreferenciaSobreviveIdaYVuelta() {
        SortOrder.entries.forEach { assertEquals(it, SortOrder.fromKey(it.key)) }
        assertEquals(SortOrder.ADDED, SortOrder.fromKey(null), "sin preferencia guardada")
        assertEquals(SortOrder.ADDED, SortOrder.fromKey("basura"))
    }
}
