package com.gmoqa.fullset.domain

import com.gmoqa.fullset.data.CatalogEntry

/**
 * Algo que **ya está registrado** —en la colección o en la wishlist— y que se marca en la lista del
 * catálogo para no agregarlo dos veces. [dot] es el color del punto (estado de conservación); si es
 * null el punto va neutro, porque lo que importa es que ya lo tenés anotado.
 */
data class CatalogMark(
    val platform: String,
    val slug: String,
    val title: String,
    val label: String,
    val dot: Long? = null,
    /** Si además impide volver a tocar la fila (duplicaría el registro). */
    val blocks: Boolean = true,
    /** Región de la copia que tenés. Vacío = sin dato (cargas viejas o altas a mano). */
    val region: String = "",
)

/**
 * Lo ya registrado de una consola, buscable por `slug` **y por título**.
 *
 * Por título además del slug porque los juegos viejos importados del Excel pueden no tenerlo, y sin
 * esa segunda llave aparecerían como "no lo tenés" en su propia lista.
 *
 * **Las que bloquean se indexan últimas, a propósito**: si un juego está en la colección y en la
 * wishlist, tiene dos marcas, y la que tiene que ganar es la del destino en el que estás parado.
 */
fun indiceDeMarcas(marcas: List<CatalogMark>, plataforma: String): Map<String, CatalogMark> {
    val indice = HashMap<String, CatalogMark>()
    marcas.filter { it.platform == plataforma }.sortedBy { it.blocks }.forEach { marca ->
        marca.slug.ifBlank { null }?.let { indice[it] = marca }
        indice[marca.title.lowercase()] = marca
    }
    return indice
}
