package com.gmoqa.fullset.data

/**
 * Un juego del que llevamos diario. Datos puros (multiplataforma). La carátula a mostrar
 * (`coverModel`, que en Android es un `java.io.File`/URL para Coil) vive como extensión por
 * plataforma.
 */
data class Game(
    val id: Long,
    val name: String,
    val platform: String,
    val coverUrl: String,
    val coverPath: String,
    val playing: Boolean,
    val backlog: Boolean,
    val createdAt: Long,
    val region: String = "",
    val releaseYear: Int? = null,
    val genre: String = "",
    val condition: String = "",
    /** Identificador del juego en el catálogo oficial de su plataforma (vacío si no está). */
    val slug: String = "",
    val publisher: String = "",
    /** Código de producto de la copia física (SCUS-94163…); vacío si la base no lo tiene. */
    val serial: String = "",
    /** true = juego digital (no lo poseés): no aparece en Collection. Ver migración 10.sqm. */
    val digital: Boolean = false,
    /** Primera vez que lo jugaste: ISO de precisión variable ("1994" | "1994-06" | "1994-06-08"). */
    val firstPlayed: String = "",
    val noteCount: Int = 0,
    val photoCount: Int = 0,
) {
    /** Estado de conservación normalizado (o null si no está cargado). */
    val conditionState: Condition? get() = Condition.fromRaw(condition)
}

/**
 * Estado de conservación de una copia física, en 4 niveles. En la BD se guarda la [key] canónica;
 * [fromRaw] es tolerante para leer también lo legacy/Excel ("CIB", "Suelto", "Con caja y manual"…),
 * así el badge funciona aunque el dato todavía no esté normalizado. [dot] = color del punto (ARGB).
 */
enum class Condition(val key: String, val label: String, val dot: Long) {
    LOOSE("loose", "Loose", 0xFF9E9E9E),                        // gris   — solo el juego
    LOOSE_MANUAL("loose_manual", "Loose + manual", 0xFFFFB300), // ámbar  — juego + manual, sin caja
    BOXED("boxed", "Boxed", 0xFF42A5F5),                        // azul   — con caja, sin manual
    COMPLETE("complete", "Complete", 0xFF66BB6A);               // verde  — caja + manual (CIB)

    companion object {
        fun fromRaw(raw: String?): Condition? {
            val s = raw?.trim()?.lowercase()?.ifBlank { null } ?: return null
            return when {
                s == "complete" || s == "completo" || s == "cib" || "caja y manual" in s -> COMPLETE
                s == "boxed" || ("caja" in s && "sin manual" in s) -> BOXED
                s == "loose_manual" || ("suelto" in s && "manual" in s) -> LOOSE_MANUAL
                s == "loose" || s == "suelto" -> LOOSE
                else -> null
            }
        }
    }
}

/** Una nota (entrada de texto) asociada a un juego. */
data class Note(
    val id: Long,
    val gameId: Long,
    val text: String,
    val createdAt: Long,
    /** Ruta del WAV si es nota de voz; vacío si se escribió a mano. */
    val audioPath: String = "",
    val durationMs: Long = 0,
) {
    val isVoice: Boolean get() = audioPath.isNotBlank()
}

/** Una foto asociada a un juego. `path` apunta a un archivo en almacenamiento interno. */
data class Photo(
    val id: Long,
    val gameId: Long,
    val path: String,
    val caption: String,
    val createdAt: Long,
)

/** Un juego deseado (wishlist): plataforma + juego + fecha. */
data class WishlistItem(
    val id: Long,
    val platform: String,
    val game: String,
    val slug: String,
    val coverUrl: String,
    val addedAt: Long,
)

/** Modo de tema elegido por el usuario. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Región por defecto de la colección. Por ahora solo NTSC-U (listas USA) está soportada; NTSC-J
 * (Japón) y PAL (Europa) quedan listas para cuando existan los catálogos normalizados por región.
 */
enum class RegionFilter(val key: String, val label: String, val supported: Boolean) {
    NTSC_U("ntsc", "NTSC-U", true),      // América (se mantiene la key "ntsc" por compatibilidad)
    NTSC_J("ntsc-j", "NTSC-J", true),    // Japón
    PAL("pal", "PAL", true);             // Europa + Australia + Brasil (PAL-M)

    companion object {
        fun fromKey(key: String?): RegionFilter = entries.firstOrNull { it.key == key } ?: NTSC_U
    }
}
