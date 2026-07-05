package com.eggplant.detector.core.formatting

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DateFormatter {
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun format(value: LocalDateTime): String = value.format(
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault()),
    )

    fun groupLabel(value: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String = when {
        value.toLocalDate() == now.toLocalDate() -> "Today"
        value.toLocalDate() == now.toLocalDate().minusDays(1) -> "Yesterday"
        else -> value.format(monthFormatter)
    }
}
