package com.gmoqa.fullset.domain

import com.gmoqa.fullset.data.Game

/**
 * Búsqueda difusa sobre la colección. Pensada para escribir poco y encontrar rápido:
 *
 *  - ignora mayúsculas, acentos y puntuación → "pokemon" encuentra "Pokémon", "zelda a link"
 *    encuentra "The Legend of Zelda: A Link to the Past";
 *  - acepta las palabras en cualquier orden → "rage streets" encuentra "Streets of Rage";
 *  - tolera un error de tipeo por palabra → "metroyd" encuentra "Super Metroid";
 *  - entiende siglas por subsecuencia → "ffvii" encuentra "Final Fantasy VII";
 *  - también busca por plataforma → "genesis" lista los de Sega Genesis.
 *
 * Devuelve los juegos ordenados por relevancia. Código puro (sin Android ni APIs de JVM), para
 * que sirva igual en un futuro target de iOS.
 */
object GameSearch {

    fun filter(games: List<Game>, query: String): List<Game> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return games
        return rankBy(games, { score(it, normalizedQuery) }, { it.name })
    }

    /**
     * Ordena cualquier lista por relevancia contra [query] mirando el texto que devuelva [text],
     * y descarta lo que no coincide. Lo usa el buscador de títulos al agregar un juego.
     */
    fun <T> rank(
        items: List<T>,
        query: String,
        limit: Int = Int.MAX_VALUE,
        text: (T) -> String,
    ): List<T> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return items.take(limit)
        return rankBy(items, { scoreText(normalize(text(it)), normalizedQuery) }, text).take(limit)
    }

    /** A igual relevancia, alfabético: así el orden no baila entre búsquedas parecidas. */
    private fun <T> rankBy(items: List<T>, score: (T) -> Int, label: (T) -> String): List<T> =
        items.map { it to score(it) }
            .filter { (_, s) -> s > 0 }
            .sortedWith(compareByDescending<Pair<T, Int>> { it.second }.thenBy { label(it.first) })
            .map { it.first }

    /** Relevancia de un juego para la consulta ya normalizada. 0 = no coincide. */
    private fun score(game: Game, query: String): Int {
        val title = normalize(game.name)
        val titleScore = scoreText(title, query)
        if (titleScore > 0) return titleScore

        // La plataforma pesa menos: "genesis" debe listarlos, pero nunca ganarle a un título.
        val platformScore = scoreText(normalize(game.platform), query)
        return if (platformScore > 0) platformScore / 4 else 0
    }

    private fun scoreText(text: String, query: String): Int {
        if (text.isBlank()) return 0

        // 1) Coincidencia literal: lo más fuerte, y mejor cuanto más al principio.
        val index = text.indexOf(query)
        if (index >= 0) {
            var score = 1000 - index.coerceAtMost(100)
            if (index == 0) score += 400            // empieza igual
            else if (text[index - 1] == ' ') score += 250 // arranca una palabra
            return score
        }

        // 2) Todas las palabras de la consulta aparecen (en cualquier orden), permitiendo un typo.
        val queryWords = query.split(' ').filter { it.isNotEmpty() }
        val textWords = text.split(' ').filter { it.isNotEmpty() }
        if (queryWords.isNotEmpty()) {
            var matched = 0
            var wordScore = 0
            for (word in queryWords) {
                val hit = textWords.firstOrNull { it.startsWith(word) }
                    ?: textWords.firstOrNull { it.contains(word) }
                    ?: textWords.firstOrNull { word.length >= 4 && isNearMatch(it, word) }
                if (hit != null) {
                    matched++
                    wordScore += if (hit.startsWith(word)) 120 else 80
                }
            }
            if (matched == queryWords.size) return 500 + wordScore
        }

        // 3) Subsecuencia: cubre siglas ("ffvii") y abreviaturas ("sotn").
        return subsequenceScore(text, query.replace(" ", ""))
    }

    /**
     * Puntúa la consulta como subsecuencia del texto: premia letras consecutivas y las que caen
     * al inicio de una palabra (que es lo que hace que "ffvii" encuentre "Final Fantasy VII").
     */
    private fun subsequenceScore(text: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var score = 0
        var needleIndex = 0
        var previousMatch = -2

        for (i in text.indices) {
            if (needleIndex >= needle.length) break
            if (text[i] != needle[needleIndex]) continue

            var gain = 10
            if (i == 0 || text[i - 1] == ' ') gain += 25 // inicial de palabra
            if (i == previousMatch + 1) gain += 15       // letras seguidas
            score += gain
            previousMatch = i
            needleIndex++
        }
        // Solo cuenta si aparecieron todas las letras, en orden.
        return if (needleIndex == needle.length) score else 0
    }

    /** true si difieren en como mucho una edición (typo simple). */
    private fun isNearMatch(candidate: String, word: String): Boolean {
        if (kotlin.math.abs(candidate.length - word.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < candidate.length && j < word.length) {
            if (candidate[i] == word[j]) {
                i++; j++
                continue
            }
            if (++edits > 1) return false
            when {
                candidate.length > word.length -> i++   // sobra una letra
                candidate.length < word.length -> j++   // falta una letra
                else -> { i++; j++ }                    // letra cambiada
            }
        }
        return edits + (candidate.length - i) + (word.length - j) <= 1
    }

    /** Minúsculas, sin acentos y con la puntuación convertida en separador. */
    private fun normalize(raw: String): String {
        val builder = StringBuilder(raw.length)
        for (char in raw.lowercase()) {
            val plain = ACCENTS[char] ?: char
            builder.append(if (plain.isLetterOrDigit()) plain else ' ')
        }
        return builder.toString().trim().replace(SPACES, " ")
    }

    private val SPACES = Regex("\\s+")

    private val ACCENTS: Map<Char, Char> = buildMap {
        "áàâäã".forEach { put(it, 'a') }
        "éèêë".forEach { put(it, 'e') }
        "íìîï".forEach { put(it, 'i') }
        "óòôöõ".forEach { put(it, 'o') }
        "úùûü".forEach { put(it, 'u') }
        put('ñ', 'n')
        put('ç', 'c')
    }
}
