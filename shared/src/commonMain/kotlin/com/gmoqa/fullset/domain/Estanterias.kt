package com.gmoqa.fullset.domain

import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.SortOrder
import com.gmoqa.fullset.data.sortedBy

/**
 * Los juegos repartidos en estanterías, una por consola.
 *
 * La agrupación por consola **no depende del orden elegido**: el orden manda dentro de cada estante,
 * no entre estantes. Es la diferencia entre "ordename la colección" y "ordename cada estante", y es
 * lo que hace que cambiar de orden no te mueva las consolas de lugar.
 */
fun estanterias(juegos: List<Game>, orden: SortOrder): Map<String, List<Game>> =
    juegos.groupBy { it.platform }.mapValues { (_, lista) -> lista.sortedBy(orden) }
