package com.gmoqa.fullset.domain

import com.gmoqa.fullset.data.CatalogEntry
import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.SortOrder
import com.gmoqa.fullset.data.TrackingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Las cuatro reglas que decidían qué ves y vivían dentro de un `remember`. */
class ReglasDeListaTest {

    private fun juego(
        id: Long, nombre: String, plataforma: String = "Sega Genesis",
        backlog: Boolean = false, digital: Boolean = false, jugadoEn: String = "",
    ) = Game(
        id = id, name = nombre, platform = plataforma, coverUrl = "", coverPath = "",
        playing = false, backlog = backlog, createdAt = 0, digital = digital, firstPlayed = jugadoEn,
    )

    // ---------------------------------------------------------------- estanterías

    @Test
    fun agrupaPorConsolaYOrdenaDentroDeCadaEstante() {
        val e = estanterias(
            listOf(
                juego(1, "Zero Tolerance"),
                juego(2, "Alien Soldier"),
                juego(3, "Panzer Dragoon", plataforma = "Sega Saturn"),
            ),
            SortOrder.TITLE,
        )
        assertEquals(setOf("Sega Genesis", "Sega Saturn"), e.keys)
        assertEquals(listOf("Alien Soldier", "Zero Tolerance"), e["Sega Genesis"]!!.map { it.name })
    }

    @Test
    fun elOrdenNoMueveLasConsolasDeLugar() {
        // El orden manda **dentro** de cada estante, no entre estantes: cambiar de orden no puede
        // reacomodarte las consolas.
        val juegos = listOf(juego(1, "B"), juego(2, "A", plataforma = "Sega Saturn"))
        assertEquals(
            estanterias(juegos, SortOrder.TITLE).keys.toList(),
            estanterias(juegos, SortOrder.DEFAULT).keys.toList(),
        )
    }

    // ---------------------------------------------------------------- backlog

    @Test
    fun conColeccionElBacklogEsSoloDeLoQuePosees() {
        val todos = listOf(juego(1, "Físico", backlog = true), juego(2, "Digital", backlog = true, digital = true))
        val fisicos = todos.filter { !it.digital }
        val r = pendientes(todos, fisicos, TrackingMode.COLLECTION_AND_DIARY)
        assertEquals(listOf("Físico"), r.map { it.name })
    }

    @Test
    fun enModoDiarioEntranTodosPorqueTodosSonDigitales() {
        // En "Diary only" los juegos se cargan como digitales: filtrar por físicos dejaría el
        // Backlog **siempre vacío**, que es el defecto que esta regla evita.
        val todos = listOf(juego(1, "A", backlog = true, digital = true), juego(2, "B", digital = true))
        val r = pendientes(todos, fisicos = emptyList(), modo = TrackingMode.DIARY_ONLY)
        assertEquals(listOf("A"), r.map { it.name })
        assertTrue(pendientes(todos, emptyList(), TrackingMode.COLLECTION_AND_DIARY).isEmpty())
    }

    // ---------------------------------------------------------------- línea de tiempo

    @Test
    fun agrupaElDiarioPorAnioYLoQueSoloTieneAnioAbreElAnio() {
        // Orden lexicográfico sobre ISO de precisión variable: "1994" < "1994-06".
        val l = lineaDeTiempo(
            listOf(
                juego(1, "Con mes", jugadoEn = "1994-06"),
                juego(2, "Solo año", jugadoEn = "1994"),
                juego(3, "Sin fecha"),
            ),
        )
        assertEquals(listOf("Solo año", "Con mes"), l.porAnio["1994"]!!.map { it.name })
        assertEquals(2, l.total, "el que no tiene fecha no entra en el diario")
        assertTrue(l.conMes)
    }

    @Test
    fun sinNingunMesLaColumnaDeFechaNoHaceFalta() {
        val l = lineaDeTiempo(listOf(juego(1, "A", jugadoEn = "1994"), juego(2, "B", jugadoEn = "1995")))
        assertEquals(false, l.conMes, "son 46dp de margen muerto en un teléfono angosto")
    }

    // ---------------------------------------------------------------- alta de juego

    @Test
    fun loRegistradoSeEncuentraPorSlugYTambienPorTitulo() {
        // Por título además del slug porque los juegos importados del Excel pueden no tenerlo.
        val i = indiceDeMarcas(
            listOf(CatalogMark("Sega Genesis", "sonic", "Sonic the Hedgehog", "Owned")),
            "Sega Genesis",
        )
        assertEquals("Owned", i["sonic"]?.label)
        assertEquals("Owned", i["sonic the hedgehog"]?.label)
    }

    @Test
    fun laMarcaQueBloqueaLeGanaALaQueNo() {
        // Un juego en la colección **y** en la wishlist tiene dos marcas: manda la del destino en el
        // que estás parado, que es la que bloquea.
        val i = indiceDeMarcas(
            listOf(
                CatalogMark("Sega Genesis", "sonic", "Sonic", "Added", blocks = false),
                CatalogMark("Sega Genesis", "sonic", "Sonic", "Owned", blocks = true),
            ),
            "Sega Genesis",
        )
        assertEquals("Owned", i["sonic"]?.label)
    }

    @Test
    fun ignoraLasMarcasDeOtraConsola() {
        val i = indiceDeMarcas(listOf(CatalogMark("Sega Saturn", "nights", "NiGHTS", "Owned")), "Sega Genesis")
        assertTrue(i.isEmpty())
    }

    @Test
    fun elCorteMarcaDondeArrancaCadaRegion() {
        val entradas = listOf(
            CatalogEntry("A", "", "a", region = "NTSC-U"),
            CatalogEntry("B", "", "b", region = "NTSC-U"),
            CatalogEntry("C", "", "c", region = "NTSC-J"),
        )
        assertEquals(mapOf(0 to ("NTSC-U" to 2), 2 to ("NTSC-J" to 1)), cortesPorRegion(entradas))
    }
}
