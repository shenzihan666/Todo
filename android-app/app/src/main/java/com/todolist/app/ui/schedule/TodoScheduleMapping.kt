package com.todolist.app.ui.schedule

import com.todolist.app.data.network.dto.TodoReadDto
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

internal fun TodoReadDto.toScheduleEvent(): ScheduleEvent {
    val instant = scheduled_at?.takeIf { it.isNotBlank() } ?: created_at
    val (date, start, end) = parseScheduleInstant(isoString = instant)
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

private fun parseScheduleInstant(isoString: String): Triple<LocalDate, LocalTime, LocalTime> {
    return try {
        val odt = OffsetDateTime.parse(isoString)
        val ldt = odt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        val date = ldt.toLocalDate()
        val start = ldt.toLocalTime()
        val end = start.plusMinutes(30)
        Triple(date, start, end)
    } catch (_: DateTimeParseException) {
        val localDate = LocalDate.now()
        val start = LocalTime.of(9, 0)
        Triple(localDate, start, start.plusMinutes(30))
    }
}
