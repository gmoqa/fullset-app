package com.gmoqa.fullset.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fechas de precisión variable: releaseDate del catálogo y firstPlayed del diario. */
class FormatTest {

    // ------------------------------------------------------ formatReleaseDate

    @Test
    fun soloAnio() = assertEquals("1994", formatReleaseDate("1994"))

    @Test
    fun anioYMes() = assertEquals("Jun 1994", formatReleaseDate("1994-06"))

    @Test
    fun fechaCompleta() = assertEquals("Jun 8, 1994", formatReleaseDate("1994-06-08"))

    @Test
    fun vacioCaeAlAnioDeRespaldo() = assertEquals("1993", formatReleaseDate("", fallbackYear = 1993))

    @Test
    fun vacioSinRespaldo() = assertEquals("—", formatReleaseDate(""))

    @Test
    fun mesInvalidoDegradaAlAnio() = assertEquals("1994", formatReleaseDate("1994-13"))

    // ------------------------------------------------------------- partialIso

    @Test
    fun isoSoloAnio() = assertEquals("1994", partialIso(1994, null, null))

    @Test
    fun isoAnioYMes() = assertEquals("1994-06", partialIso(1994, 6, null))

    @Test
    fun isoCompleto() = assertEquals("1994-06-08", partialIso(1994, 6, 8))

    @Test
    fun diaSinMesSeIgnora() = assertEquals("1994", partialIso(1994, null, 8))

    // ------------------------------------------------------------ daysInMonth

    @Test
    fun meses() {
        assertEquals(31, daysInMonth(1994, 1))
        assertEquals(30, daysInMonth(1994, 4))
        assertEquals(31, daysInMonth(1994, 12))
    }

    @Test
    fun febreroComun() = assertEquals(28, daysInMonth(1995, 2))

    @Test
    fun febreroBisiesto() = assertEquals(29, daysInMonth(1996, 2))

    @Test
    fun sigloNoEsBisiesto() = assertEquals(28, daysInMonth(1900, 2))

    @Test
    fun cadaCuatroSiglosSiEsBisiesto() = assertEquals(29, daysInMonth(2000, 2))

    // ------------------------------------------------------------------ todayIso

    @Test
    fun hoyEnIsoConLaFormaQueEsperaElResto() {
        // No se puede afirmar *qué* día es, pero sí su forma: el Timeline corta las posiciones 5..7
        // y 8..10 para sacar mes y día, y `formatReleaseDate` parsea sobre lo mismo. Si esto dejara
        // de ser "AAAA-MM-DD" —por ejemplo con un año de 5 dígitos o sin relleno de ceros— el
        // Timeline mostraría basura en vez de fallar.
        val hoy = todayIso()
        assertEquals(10, hoy.length, "esperaba AAAA-MM-DD, vino '$hoy'")
        assertTrue(hoy[4] == '-' && hoy[7] == '-', "separadores fuera de lugar en '$hoy'")
        val (anio, mes, dia) = hoy.split("-").map { it.toInt() }
        assertTrue(anio in 2020..2100, "año fuera de rango: $anio")
        assertTrue(mes in 1..12, "mes inválido: $mes")
        assertTrue(dia in 1..daysInMonth(anio, mes), "día inválido: $dia")
        // Y tiene que poder mostrarse: es el mismo camino que usa el chip.
        assertTrue(formatReleaseDate(hoy).isNotBlank())
    }
}
