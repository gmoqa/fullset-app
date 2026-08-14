package com.gmoqa.fullset.domain

import com.gmoqa.fullset.data.Game

/** El diario agrupado por año, con lo que hace falta para dibujarlo. */
data class LineaDeTiempo(
    val porAnio: Map<String, List<Game>>,
    val total: Int,
    /**
     * Si **algún** juego tiene mes cargado. Si ninguno lo tiene —lo normal al principio, donde uno
     * solo recuerda el año— la columna de la fecha queda vacía en todas las filas y son 46dp de
     * margen muerto en un teléfono angosto.
     */
    val conMes: Boolean,
)

/**
 * Arma la línea de tiempo con los juegos que tienen "primera vez que lo jugué".
 *
 * El orden es **lexicográfico sobre la fecha ISO**, y con precisión variable eso hace justo lo que
 * se quiere: "1994" queda antes que "1994-06", o sea que lo que solo sabemos que fue ese año abre
 * el año.
 */
fun lineaDeTiempo(juegos: List<Game>): LineaDeTiempo {
    val porAnio = juegos.filter { it.firstPlayed.isNotBlank() }
        .sortedBy { it.firstPlayed }
        .groupBy { it.firstPlayed.take(4) }
    return LineaDeTiempo(
        porAnio = porAnio,
        total = porAnio.values.sumOf { it.size },
        conMes = juegos.any { it.firstPlayed.length >= 7 },
    )
}
