package com.gmoqa.fullset.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable

/** Un juego encontrado en SteamGridDB, para elegir de la lista antes de ver sus carátulas. */
data class SteamGridGame(val id: Int, val name: String)

/**
 * Cliente de **SteamGridDB** para plataformas sin catálogo (PS5…), donde Libretro no llega. El alta
 * lo usa en dos pasos: [searchGames] (autocomplete → lista de juegos para elegir el correcto) y
 * [coversForGame] (grids verticales 600×900 de ese juego).
 *
 * La API key se **inyecta** (la capa de app la saca de BuildConfig/local.properties); sin ella
 * [isEnabled] es false y no se hace ninguna llamada. KMP-ready: Ktor + kotlinx.serialization, sin
 * dependencias de Android.
 */
class SteamGridDb(
    private val apiKey: String,
) {
    private val client by lazy { createHttpClient() }

    /** Hay key configurada: sin esto, la UI no ofrece el buscador. */
    val isEnabled: Boolean get() = apiKey.isNotBlank()

    /** Juegos que coinciden con [title] (autocomplete). Vacío si no hay match o falla la red. */
    suspend fun searchGames(title: String, limit: Int = 20): List<SteamGridGame> =
        run {
            if (!isEnabled || title.isBlank()) return@run emptyList()
            runCatching {
                val body = getAuthed("$BASE/search/autocomplete/${title.encodeURLPathPart()}")
                AppJson.decodeFromString<Response<Game>>(body).data
                    .filter { it.name.isNotBlank() }
                    .take(limit)
                    .map { SteamGridGame(it.id, it.name) }
            }.getOrDefault(emptyList())
        }

    /** Carátulas (URLs 600×900) del juego [gameId]. Vacío si no tiene o falla la red. */
    suspend fun coversForGame(gameId: Int, limit: Int = 16): List<String> =
        run {
            if (!isEnabled) return@run emptyList()
            runCatching {
                // Solo verticales estáticas (formato carátula), sin NSFW/humor.
                val url = "$BASE/grids/game/$gameId?dimensions=600x900&types=static&nsfw=false&humor=false"
                AppJson.decodeFromString<Response<Grid>>(getAuthed(url)).data.map { it.url }.take(limit)
            }.getOrDefault(emptyList())
        }

    private suspend fun getAuthed(url: String): String =
        client.get(url) { header("Authorization", "Bearer $apiKey") }.bodyAsText()

    @Serializable private data class Response<T>(val success: Boolean = false, val data: List<T> = emptyList())
    @Serializable private data class Game(val id: Int = 0, val name: String = "")
    @Serializable private data class Grid(val url: String = "", val thumb: String = "")

    companion object {
        private const val BASE = "https://www.steamgriddb.com/api/v2"
    }
}
