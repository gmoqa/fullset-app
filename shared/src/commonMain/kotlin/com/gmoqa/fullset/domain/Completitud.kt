package com.gmoqa.fullset.domain

import com.gmoqa.fullset.data.CatalogEntry
import com.gmoqa.fullset.data.Game
import com.gmoqa.fullset.data.coverModel

/**
 * Reglas de la app, fuera de Compose.
 *
 * Acá vive lo que la app **decide**, separado de cómo lo dibuja. El repo ya lo hacía con
 * `diaryFeed`, `GameSearch.filter` y `groupedByGeneration`; esta es la misma idea aplicada a las
 * reglas que se habían quedado adentro de un `remember`.
 *
 * La ganancia concreta no es estética: la completitud de una consola —ese "148 of 1893" que mide
 * cuánto te falta— no se podía probar sin levantar Compose, y es de lo más delicado que hay porque
 * cruza dos fuentes que se nombran distinto.
 */

/** Una fila del catálogo de la consola, ya resuelta a "poseído o no". */
data class PlatformRow(
    val key: String,
    val title: String,
    val year: Int?,
    /** Fecha ISO de precisión variable del catálogo ("" si el juego no está en el catálogo). */
    val releaseDate: String = "",
    val subtitle: String,
    val coverModel: Any?,
    /** Non-null → lo tenés (abre su detalle). Null → falta (en gris, con botón para agregar). */
    val ownedId: Long?,
    /** La entrada del catálogo, para poder agregarla si falta. Null en juegos fuera del catálogo. */
    val entry: CatalogEntry? = null,
)

/** El catálogo de una consola cruzado con tu colección, más cuánto de él tenés. */
data class Completitud(
    val filas: List<PlatformRow>,
    /**
     * Juegos **distintos** que tenés, no filas.
     *
     * Desde que las listas de las tres regiones se muestran juntas, una misma copia matchea por
     * slug en la lista americana **y** en la japonesa. Contando filas, "3 of 381" pasaba a decir
     * "6 of 381" sin que hubieras agregado nada.
     */
    val poseidos: Int,
)

/**
 * Fusiona el catálogo de una consola con tu colección.
 *
 * Cada entrada del catálogo sabe si la tenés (match por `slug`), y tus juegos que **no** están en el
 * catálogo —altas a mano, o un slug que no matchea— se agregan igual: son tuyos aunque la lista no
 * los nombre. Si la consola no trae catálogo (la PS5), quedan solo esos.
 *
 * El orden es **cronológico real**, por la fecha precisa: el ISO ordena cronológicamente por sí
 * solo, así que "1991-06" cae antes que "1991-12" dentro del mismo año. Sin fecha se usa el año, y
 * sin nada la fila va al final. Desempata el título.
 */
fun completitudDe(catalogo: List<CatalogEntry>, coleccion: List<Game>): Completitud {
    val porSlug = coleccion.filter { it.slug.isNotBlank() }.associateBy { it.slug }

    val delCatalogo = catalogo.map { e ->
        val mio = porSlug[e.slug]
        PlatformRow(
            key = "cat:${e.slug.ifBlank { e.title }}",
            // Si lo tenés, manda **tu** título: pudiste haberlo renombrado.
            title = mio?.name ?: e.title,
            year = e.year ?: mio?.releaseYear,
            releaseDate = e.releaseDate,
            subtitle = listOfNotNull(e.genre.ifBlank { null }, e.publisher.ifBlank { null })
                .joinToString(" · "),
            coverModel = mio?.coverModel ?: e.coverUrl.ifBlank { null },
            ownedId = mio?.id,
            entry = e,
        )
    }

    val slugsDelCatalogo = catalogo.mapNotNull { it.slug.ifBlank { null } }.toSet()
    val fueraDelCatalogo = coleccion
        .filter { it.slug.isBlank() || it.slug !in slugsDelCatalogo }
        .map { g ->
            PlatformRow(
                key = "own:${g.id}",
                title = g.name,
                year = g.releaseYear,
                subtitle = listOfNotNull(g.genre.ifBlank { null }, g.publisher.ifBlank { null })
                    .joinToString(" · "),
                coverModel = g.coverModel,
                ownedId = g.id,
            )
        }

    val filas = (delCatalogo + fueraDelCatalogo).sortedWith(
        compareBy(
            { it.releaseDate.ifBlank { it.year?.toString() ?: "9999" } },
            { it.title.lowercase() },
        ),
    )
    return Completitud(filas = filas, poseidos = filas.mapNotNull { it.ownedId }.distinct().size)
}
