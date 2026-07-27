package com.gmoqa.diariogamer.data

/** Una entrada del catálogo de una plataforma. */
data class CatalogEntry(
    val title: String,
    val subtitle: String,
    val slug: String,
    val region: String = "",
    val year: Int? = null,
    val publisher: String = "",
    val genre: String = "",
    /** URL de carátula horneada en el catálogo (Libretro). Vacío → se deriva del título. */
    val coverUrl: String = "",
)
