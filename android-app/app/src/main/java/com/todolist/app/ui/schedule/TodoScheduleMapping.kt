package com.todolist.app.ui.schedule

import com.todolist.app.data.network.dto.TodoReadDto
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

private const val DEFAULT_DURATION_MINUTES = 30L

internal fun TodoReadDto.toScheduleEvent(): ScheduleEvent {
    val instant = scheduled_at?.takeIf { it.isNotBlank() } ?: created_at
    val durationMinutes = estimated_minutes?.toLong() ?: DEFAULT_DURATION_MINUTES
    val (date, start, end) = parseScheduleInstant(isoString = instant, durationMinutes = durationMinutes)
    val titleDisplay =
        if (completed) {
            "✓ $title"
        } else {
            title
        }
    return ScheduleEvent(
        id = id.toString(),
        title = titleDisplay,
        date = date,
        start = start,
        end = end,
        accentIndex = kotlin.math.abs(id % 4),
    )
}

private fun parseScheduleInstant(
    isoString: String,
    durationMinutes: Long,
): Triple<LocalDate, LocalTime, LocalTime> {
    return try {
        val odt = OffsetDateTime.parse(isoString)
        val ldt = odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        val date = ldt.toLocalDate()
        val start = ldt.toLocalTime()
        val end = start.plusMinutes(durationMinutes)
        Triple(date, start, end)
    } catch (_: DateTimeParseException) {
        val localDate = LocalDate.now()
        val start = LocalTime.of(9, 0)
        Triple(localDate, start, start.plusMinutes(durationMinutes))
    }
}
