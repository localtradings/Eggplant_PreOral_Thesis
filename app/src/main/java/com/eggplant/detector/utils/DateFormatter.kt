package com.eggplant.detector.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateFormatter {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a")
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun format(value: LocalDateTime): String = value.format(dateTimeFormatter)

    fun groupLabel(value: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String = when {
        value.toLocalDate() == now.toLocalDate() -> "Today"
        value.toLocalDate() == now.toLocalDate().minusDays(1) -> "Yesterday"
        else -> value.format(monthFormatter)
    }
}
