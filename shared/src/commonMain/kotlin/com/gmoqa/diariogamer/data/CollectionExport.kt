package com.gmoqa.diariogamer.data

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Exporta la colección (biblioteca) a CSV para abrir en Excel/Sheets.
 * Columnas normalizadas, alineadas con el esquema de las listas JSON
 * (title/platform/region/year/genre + condition) más el estado (playing/backlog) y la fecha.
 * Código puro (sin Android): la UI escribe el String al archivo elegido.
 * Se antepone un BOM UTF-8 para que Excel muestre bien los acentos.
 */
fun DiaryRepository.collectionCsv(): String {
    val header = listOf(
        "Title", "Platform", "Region", "Year", "Genre", "Condition", "Playing", "Backlog", "Added",
    )
    val sb = StringBuilder("﻿") // BOM UTF-8
    sb.appendCsvRow(header)

    // Mismo orden que la biblioteca (más reciente primero).
    for (g in games()) {
        sb.appendCsvRow(
            listOf(
                g.name,
                g.platform,
                g.region,
                g.releaseYear?.toString().orEmpty(),
                g.genre,
                g.condition,
                if (g.playing) "Yes" else "No",
                if (g.backlog) "Yes" else "No",
                isoDate(g.createdAt),
            )
        )
    }
    return sb.toString()
}

/** Fecha ISO `yyyy-MM-dd` (ordenable y estable en Excel). */
private fun isoDate(epochMillis: Long): String =
    Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date.toString()

private fun StringBuilder.appendCsvRow(fields: List<String>) {
    append(fields.joinToString(",") { csvField(it) })
    append("\r\n") // CRLF: lo esperado por Excel
}

/** Escapa un campo CSV (RFC 4180): comillas dobladas y entrecomillado si hay `,` `"` o saltos. */
private fun csvField(value: String): String {
    val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    val escaped = value.replace("\"", "\"\"")
    return if (needsQuoting) "\"$escaped\"" else escaped
}
