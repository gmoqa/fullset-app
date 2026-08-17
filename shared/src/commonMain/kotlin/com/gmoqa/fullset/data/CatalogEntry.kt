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
    /** Quién lo **desarrolló**. Distinto de [publisher]: Sonic Team hizo *Sonic*, Sega lo publicó. */
    val developer: String = "",
    /** Quién lo **publicó** en esta región. Puede cambiar de mercado a mercado; el developer no. */
    val publisher: String = "",
    val genre: String = "",
    /** Catalog number impreso en el cartucho/disco (SCUS-94163, MK-01077-00…). */
    val serial: String = "",
    /** Clasificación por edad (ESRB, CERO…) tal como la publica la fuente. Vacío = sin dato. */
    val rating: String = "",
    /** URL de carátula horneada en el catálogo (Libretro). Vacío → se deriva del título. */
    val coverUrl: String = "",
)
