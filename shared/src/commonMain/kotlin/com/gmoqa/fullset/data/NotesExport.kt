package com.gmoqa.fullset.data

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Export de las notas de UN juego a JSON. La idea: que puedas **compartir tus notas y hacer lo que
 * quieras con ellas** — pasárselas a un LLM para que arme el relato de tu partida, un resumen, lo que
 * sea. Tus datos, tu historia. Formato legible (pretty) y autoexplicativo, listo para pegar.
 */
@Serializable
data class GameNotesExport(
    val game: String,
    val platform: String,
    val year: Int? = null,
    val genre: String = "",
    val condition: String = "",
    val notes: List<NoteExport>,
)

@Serializable
data class NoteExport(
    /** Fecha de la nota, ISO (YYYY-MM-DD). */
    val date: String,
    /** "voice" (transcripción) o "text" (escrita a mano). */
    val type: String,
    val text: String,
)

private val PrettyJson = Json { prettyPrint = true }

/** Arma el JSON de las notas de un juego, ordenadas por fecha. */
fun DiaryRepository.gameNotesJson(gameId: Long): String {
    val g = games().firstOrNull { it.id == gameId } ?: return "{}"
    val notes = notes(gameId)
        .filter { it.text.isNotBlank() }
        .sortedBy { it.createdAt }
        .map {
            NoteExport(
                date = Instant.fromEpochMilliseconds(it.createdAt)
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
                type = if (it.isVoice || it.durationMs > 0) "voice" else "text",
                text = it.text,
            )
        }
    return PrettyJson.encodeToString(
        GameNotesExport(
            game = g.name, platform = g.platform, year = g.releaseYear,
            genre = g.genre, condition = g.condition, notes = notes,
        )
    )
}

/**
 * Las notas de un juego como **texto legible** (para leer, mandar por chat o pegar en un LLM como
 * lenguaje natural). Encabezado con el juego + una línea por nota con su fecha.
 */
fun DiaryRepository.gameNotesText(gameId: Long): String {
    val g = games().firstOrNull { it.id == gameId } ?: return ""
    val header = buildString {
        append(g.name)
        val meta = listOfNotNull(
            g.platform.ifBlank { null },
            g.releaseYear?.toString(),
            g.genre.ifBlank { null },
        ).joinToString(" · ")
        if (meta.isNotBlank()) append(" — ").append(meta)
    }
    val lines = notes(gameId)
        .filter { it.text.isNotBlank() }
        .sortedBy { it.createdAt }
        .map {
            val date = Instant.fromEpochMilliseconds(it.createdAt)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            val voice = if (it.isVoice || it.durationMs > 0) " (voice note)" else ""
            "- $date$voice: ${it.text}"
        }
    return buildString {
        append(header)
        if (lines.isNotEmpty()) {
            append("\n\nNotes:\n")
            append(lines.joinToString("\n"))
        }
    }
}
