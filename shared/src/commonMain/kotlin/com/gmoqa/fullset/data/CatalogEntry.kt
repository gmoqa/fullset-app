package com.gmoqa.fullset.data

/** Una entrada del catálogo de una plataforma. */
data class CatalogEntry(
    val title: String,
    val subtitle: String,
    val slug: String,
    val region: String = "",
    val year: Int? = null,
    /** Fecha ISO de precisión variable ("1999" | "1999-09" | "1999-09-09"). Vacío → solo hay `year`. */
    val releaseDate: String = "",
    val publisher: String = "",
    val genre: String = "",
    /** Catalog number impreso en el cartucho/disco (SCUS-94163, MK-01077-00…). */
    val serial: String = "",
    /** URL de carátula horneada en el catálogo (Libretro). Vacío → se deriva del título. */
    val coverUrl: String = "",
)
