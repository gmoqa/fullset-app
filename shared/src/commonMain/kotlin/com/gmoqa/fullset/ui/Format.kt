package com.gmoqa.fullset.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.toLocalDateTime

// Formatos multiplataforma (kotlinx-datetime): "Jul 20, 2026" y "Jul 20, 2026 · 16:30".
private val datePart = LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    chars(" ")
    dayOfMonth(Padding.NONE)
    chars(", ")
    year()
}

private val dateTimeFmt = LocalDateTime.Format {
    date(datePart)
    chars(" · ")
    hour()
    chars(":")
    minute()
}

private fun localDateTime(epochMillis: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())

fun formatDateTime(epochMillis: Long): String = dateTimeFmt.format(localDateTime(epochMillis))

fun formatDate(epochMillis: Long): String = datePart.format(localDateTime(epochMillis).date)

internal val MONTH_ABBR = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/**
 * Fecha de lanzamiento ISO de **precisión variable** a texto legible:
 * `"1999"` → "1999" · `"1999-09"` → "Sep 1999" · `"1999-09-09"` → "Sep 9, 1999".
 * Si no hay fecha, cae a [fallbackYear] (o "—").
 */
fun formatReleaseDate(iso: String, fallbackYear: Int? = null): String {
    val parts = iso.split("-")
    val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 }?.let { MONTH_ABBR[it - 1] }
    val day = parts.getOrNull(2)?.toIntOrNull()
    return when {
        month != null && day != null -> "$month $day, ${parts[0]}"
        month != null -> "$month ${parts[0]}"
        iso.isNotBlank() -> parts[0]                    // solo año (o formato inesperado)
        else -> fallbackYear?.toString() ?: "—"
    }
}

/** ISO de precisión variable: "1994" | "1994-06" | "1994-06-08" (el día requiere mes). */
internal fun partialIso(year: Int, month: Int?, day: Int?): String = buildString {
    append(year)
    if (month != null) {
        append('-').append(month.toString().padStart(2, '0'))
        if (day != null) append('-').append(day.toString().padStart(2, '0'))
    }
}

/** Días del mes (bisiestos incluidos), para validar el selector de "First played". */
internal fun daysInMonth(year: Int, month: Int): Int = when (month) {
    4, 6, 9, 11 -> 30
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    else -> 31
}

/** Duración de una nota de voz como "m:ss" (p. ej. 1:07). */
fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
