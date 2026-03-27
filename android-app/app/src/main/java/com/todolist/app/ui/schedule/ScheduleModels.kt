package com.todolist.app.ui.schedule

import java.time.LocalDate
import java.time.LocalTime

/** One schedule entry (sample data until API exists). */
data class ScheduleEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    /** Index into the theme accent palette for the card stripe. */
    val accentIndex: Int = 0,
)
