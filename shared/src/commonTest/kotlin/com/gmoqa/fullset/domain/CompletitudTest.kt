package com.gmoqa.fullset.domain

import com.gmoqa.fullset.data.CatalogEntry
import com.gmoqa.fullset.data.Game
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * La completitud de una consola: el "148 of 1893" que dice cuánto te falta.
 *
 * Hasta que la regla salió de `PlatformScreen` esto no se podía probar sin levantar Compose, y es de
 * lo más delicado que hay: cruza dos fuentes que se nombran distinto y decide qué contás como tuyo.
 */
class CompletitudTest {

    private fun entrada(titulo: String, slug: String, año: Int? = null, fecha: String = "") =
        CatalogEntry(title = titulo, subtitle = "", slug = slug, year = año, releaseDate = fecha)

    private fun juego(id: Long, nombre: String, slug: String = "", año: Int? = null) = Game(
        id = id, name = nombre, platform = "Sega Genesis", coverUrl = "", coverPath = "",
        playing = false, backlog = false, createdAt = 0, slug = slug, releaseYear = año,
    )

    @Test
    fun marcaComoTuyoLoQueCoincidePorSlug() {
        val r = completitudDe(
            catalogo = listOf(entrada("Sonic the Hedgehog", "sonic-the-hedgehog", 1991)),
            coleccion = listOf(juego(7, "Sonic the Hedgehog", "sonic-the-hedgehog")),
        )
        assertEquals(1, r.filas.size, "no debe duplicar: es el mismo juego")
        assertEquals(7L, r.filas[0].ownedId)
        assertEquals(1, r.poseidos)
    }

    @Test
    fun elTituloTuyoLePisaAlDelCatalogo() {
        // Si lo tenés, pudiste haberlo renombrado: manda el tuyo.
        val r = completitudDe(
            catalogo = listOf(entrada("Sonic the Hedgehog", "sonic-the-hedgehog")),
            coleccion = listOf(juego(7, "Sonic 1 (mi copia japonesa)", "sonic-the-hedgehog")),
        )
        assertEquals("Sonic 1 (mi copia japonesa)", r.filas[0].title)
    }

    @Test
    fun tusJuegosFueraDelCatalogoAparecenIgual() {
        // Un alta a mano, o un slug que no matchea, sigue siendo tuyo aunque la lista no lo nombre.
        val r = completitudDe(
            catalogo = listOf(entrada("Sonic the Hedgehog", "sonic-the-hedgehog")),
            coleccion = listOf(juego(9, "Un homebrew que compré", slug = "")),
        )
        assertEquals(2, r.filas.size)
        val suelto = r.filas.single { it.ownedId == 9L }
        assertNull(suelto.entry, "no viene del catálogo, así que no se puede 'agregar'")
        assertEquals(1, r.poseidos)
    }

    @Test
    fun sinCatalogoQuedanSoloLosTuyos() {
        // El caso de la PS5: no hay lista contra la cual medirse.
        val r = completitudDe(catalogo = emptyList(), coleccion = listOf(juego(1, "Astro Bot")))
        assertEquals(1, r.filas.size)
        assertEquals(1, r.poseidos)
    }

    @Test
    fun ordenaPorFechaPrecisaDentroDelMismoAnio() {
        val r = completitudDe(
            catalogo = listOf(
                entrada("Diciembre", "d", 1991, "1991-12"),
                entrada("Junio", "j", 1991, "1991-06"),
            ),
            coleccion = emptyList(),
        )
        assertEquals(listOf("Junio", "Diciembre"), r.filas.map { it.title })
    }

    @Test
    fun loQueNoTieneFechaVaAlFinal() {
        val r = completitudDe(
            catalogo = listOf(
                entrada("Sin fecha", "s"),
                entrada("Con fecha", "c", 1995, "1995-03"),
            ),
            coleccion = emptyList(),
        )
        assertEquals(listOf("Con fecha", "Sin fecha"), r.filas.map { it.title })
    }

    @Test
    fun unaCopiaQueMatcheaEnDosRegionesCuentaUnaSolaVez() {
        // Desde que las tres regiones se muestran juntas, el mismo slug aparece en la lista
        // americana **y** en la japonesa. Contando filas, "1 of 2" decía "2 of 2" sin que hubieras
        // agregado nada.
        val r = completitudDe(
            catalogo = listOf(
                entrada("Aero Blasters", "aero-blasters", 1991).copy(region = "NTSC-U"),
                entrada("Aero Blasters", "aero-blasters", 1990).copy(region = "NTSC-J"),
            ),
            coleccion = listOf(juego(3, "Aero Blasters", "aero-blasters")),
        )
        assertEquals(2, r.filas.size, "las dos ediciones son piezas distintas y se listan las dos")
        assertEquals(1, r.poseidos, "pero tenés una sola copia")
        assertEquals(2, r.filas.count { it.ownedId == 3L }, "y se marca en las dos")
    }

    @Test
    fun lasFilasNuncaRepitenClave() {
        // `LazyColumn` **tira una excepción** si dos ítems comparten clave, así que esto es un
        // crash, no un detalle: *Pilotwings 64* está en las tres regiones con el mismo slug.
        val r = completitudDe(
            catalogo = listOf(
                entrada("Pilotwings 64", "pilotwings-64", 1996).copy(region = "NTSC-U"),
                entrada("Pilotwings 64", "pilotwings-64", 1996).copy(region = "NTSC-J"),
                entrada("Pilotwings 64", "pilotwings-64", 1997).copy(region = "PAL"),
            ),
            coleccion = listOf(juego(1, "Pilotwings 64", "pilotwings-64")),
        )
        val claves = r.filas.map { it.key }
        assertEquals(claves.size, claves.distinct().size, "claves repetidas: $claves")
    }

    // ------------------------------------------------------------------ subtítulo

    @Test
    fun elSubtituloMuestraDesarrolladoraYEditoraCuandoSonDistintas() {
        // Es el caso que justifica tener dos campos: a *Blackthorne* lo hizo Interplay y lo
        // publicó Sega.
        assertEquals("Platform · Interplay · Sega", subtitulo("Platform", "Interplay", "Sega"))
    }

    @Test
    fun siLaMismaEmpresaHizoYPublicoSeMuestraUnaSolaVez() {
        // Pasa en el 29% de los catálogos; repetirla sería ruido.
        assertEquals("Action · Nintendo", subtitulo("Action", "Nintendo", "Nintendo"))
        assertEquals("Action · Nintendo", subtitulo("Action", "nintendo", "Nintendo"))
    }

    @Test
    fun elSubtituloSaltaLoQueFalta() {
        assertEquals("Sega", subtitulo("", "", "Sega"))
        assertEquals("Interplay", subtitulo("", "Interplay", ""))
        assertEquals("", subtitulo("", "", ""))
    }

    @Test
    fun laEntradaDelCatalogoViajaParaPoderAgregarla() {
        val r = completitudDe(
            catalogo = listOf(entrada("Gunstar Heroes", "gunstar-heroes")),
            coleccion = emptyList(),
        )
        assertNull(r.filas[0].ownedId, "no lo tenés")
        assertNotNull(r.filas[0].entry, "pero se puede agregar desde acá")
        assertEquals(0, r.poseidos)
    }
}
