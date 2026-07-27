package com.gmoqa.diariogamer.ui

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

/** Duración de una nota de voz como "m:ss" (p. ej. 1:07). */
fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
