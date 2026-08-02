package com.gmoqa.fullset.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Submuestreo al decodificar una foto. La regla que importa: **nunca dejar la imagen por debajo del
 * objetivo**, porque agrandarla después la vería borrosa.
 */
class ImageResizeTest {

    @Test
    fun unaFotoDeCamaraSeDecodificaSubmuestreada() {
        // 12 MP típicos (4000×3000) hacia 1600: 4000/2 = 2000 sigue arriba, 4000/4 = 1000 no.
        assertEquals(2, sampleSizeFor(4000, 3000, 1600))
    }

    @Test
    fun unaImagenChicaNoSeSubmuestrea() {
        assertEquals(1, sampleSizeFor(800, 600, 1600))
        assertEquals(1, sampleSizeFor(1600, 1200, 1600))
    }

    @Test
    fun usaElLadoLargoSinImportarLaOrientacion() {
        // La misma foto en vertical debe submuestrearse igual que en horizontal.
        assertEquals(sampleSizeFor(4000, 3000, 1600), sampleSizeFor(3000, 4000, 1600))
    }

    @Test
    fun nuncaBajaDelObjetivo() {
        // La garantía central: tras submuestrear, el lado largo sigue siendo >= maxEdge (o la imagen
        // ya era más chica que el objetivo, y entonces no se toca).
        val maxEdge = 1600
        for (longest in listOf(1601, 2000, 3200, 3201, 4000, 6400, 12000)) {
            val sample = sampleSizeFor(longest, longest / 2, maxEdge)
            assertTrue(
                longest / sample >= maxEdge,
                "con $longest px y sample $sample quedaría en ${longest / sample}, por debajo de $maxEdge",
            )
        }
    }

    @Test
    fun siempreEsPotenciaDeDos() {
        // BitmapFactory redondea a la potencia de 2 más cercana; calcularla nosotros evita sorpresas.
        for (longest in listOf(1000, 2500, 5000, 9000, 20000)) {
            val sample = sampleSizeFor(longest, longest, 1600)
            assertTrue(sample > 0 && (sample and (sample - 1)) == 0, "sample $sample no es potencia de 2")
        }
    }

    @Test
    fun tolerandoEntradasInvalidas() {
        // Si BitmapFactory no pudo leer los bordes devuelve 0/-1: no dividir por cero.
        assertEquals(1, sampleSizeFor(0, 0, 1600))
        assertEquals(1, sampleSizeFor(-1, 100, 1600))
        assertEquals(1, sampleSizeFor(4000, 3000, 0))
    }

    @Test
    fun elObjetivoPorDefectoEsRazonable() {
        // 1600 da el doble de la resolución que muestra la tarjeta del diario (220dp a 3x ≈ 1100px).
        assertTrue(IMAGE_MAX_EDGE >= 1200, "muy chico: se vería blando al ampliar")
        assertTrue(IMAGE_MAX_EDGE <= 2400, "muy grande: vuelve el problema de peso")
        assertTrue(IMAGE_QUALITY in 75..90, "calidad JPEG fuera del rango útil")
    }
}
