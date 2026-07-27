package com.gmoqa.diariogamer.data

import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPath

/**
 * Resuelve la URL de la carátula de un juego en Libretro Thumbnails a partir del título.
 * Libretro nombra los archivos con la convención No-Intro, p. ej. "Super Mario World (USA).png".
 * Como la región varía (sobre todo en Genesis), se prueban varios sufijos.
 *
 * KMP-ready: usa Ktor (no HttpURLConnection) y codificación de URL propia (no android.net.Uri).
 * El engine HTTP se inyecta con expect/actual (createHttpClient): OkHttp en Android, Darwin en iOS.
 */
object CoverArt {

    private const val BASE = "https://raw.githubusercontent.com/libretro-thumbnails"

    // Orden de preferencia de sufijos de región a probar (USA primero).
    private val REGIONS = listOf(
        "(USA)",
        "(USA, Europe)",
        "(World)",
        "(USA) (Rev 1)",
        "(USA) (En,Ja)",
        "(USA) (En,Fr)",
        "(USA, Europe, Korea)",
        "(Japan, USA)",
        "(Europe)",
        "(World) (En,Ja)",
    )

    // Artículos iniciales que No-Intro mueve al final: "The Revenge of Shinobi" -> "Revenge of Shinobi, The".
    private val ARTICLES = listOf("The", "A", "An")

    // Caracteres que Libretro reemplaza por '_' en los nombres de archivo.
    private val ILLEGAL = Regex("[&*/:`<>?\\\\|\"]")

    private val client by lazy { createHttpClient() }

    private fun normalize(title: String): String = ILLEGAL.replace(title, "_").trim()

    /**
     * Variantes del título a probar, en orden de preferencia. Libretro nombra los archivos con
     * convenciones que el título del catálogo no siempre respeta: subtítulo con " - " (no ":"),
     * artículo al final, y capitaliza palabras internas ("the" -> "The"). Se prueban todas.
     */
    private fun titleVariants(title: String): List<String> {
        val variants = LinkedHashSet<String>()
        variants += title
        // "Phantasy Star IV: The End..." -> "Phantasy Star IV - The End..." (subtítulo No-Intro).
        if (":" in title) variants += title.replace(": ", " - ").replace(":", " -")
        // ...y sin el subtítulo: "Phantasy Star IV".
        if (":" in title) variants += title.substringBefore(":").trim()
        // "The Revenge of Shinobi" -> "Revenge of Shinobi, The".
        for (art in ARTICLES) {
            if (title.startsWith("$art ")) variants += "${title.removePrefix("$art ")}, $art"
        }
        // "Sonic the Hedgehog 3" -> "Sonic The Hedgehog 3" (Libretro capitaliza el artículo interno).
        variants += title.replace(" the ", " The ")
        return variants.toList()
    }

    private fun urlFor(platform: Platform, fileTitle: String, region: String): String {
        val file = "${normalize(fileTitle)} $region.png"
        return "$BASE/${platform.libretroRepo}/master/Named_Boxarts/${file.encodeURLPath()}"
    }

    /** URL más probable (sin comprobar en red). Útil para miniaturas de la lista. */
    fun primaryUrl(platform: Platform, title: String): String =
        urlFor(platform, title, REGIONS.first())

    /**
     * Devuelve la primera URL que exista realmente (petición HEAD), probando cada variante del
     * título contra cada sufijo de región. Null si ninguna existe. El bucle externo es la variante
     * para preferir el nombre "correcto" con su mejor región antes de degradar a otro nombre.
     */
    suspend fun resolve(platform: Platform, title: String): String? {
        for (variant in titleVariants(title)) {
            for (region in REGIONS) {
                val url = urlFor(platform, variant, region)
                val ok = try {
                    client.head(url).status == HttpStatusCode.OK
                } catch (_: Exception) {
                    false
                }
                if (ok) return url
            }
        }
        return null
    }
}
