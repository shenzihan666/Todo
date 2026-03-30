package com.todolist.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun ScheduleMonthCalendar(
    visibleMonth: YearMonth,
    today: LocalDate,
    eventDates: Set<LocalDate>,
    onDayWithEventClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val monthTitle =
        remember(visibleMonth, locale) {
            visibleMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        }
    val weekDays =
        remember(locale) {
            val first = WeekFields.of(locale).firstDayOfWeek
            (0..6).map { i ->
                first.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale)
            }
        }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = monthTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            weekDays.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        val firstOfMonth = visibleMonth.atDay(1)
        val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
        val leadingBlank =
            (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val daysInMonth = visibleMonth.lengthOfMonth()
        val cells =
            remember(visibleMonth, today, leadingBlank, daysInMonth) {
                buildList {
                    repeat(leadingBlank) { add(CalendarCell.Blank) }
                    for (d in 1..daysInMonth) {
                        val date = visibleMonth.atDay(d)
                        add(CalendarCell.Day(date, isToday = date == today))
                    }
                    while (size % 7 != 0) {
                        add(CalendarCell.Blank)
                    }
                }
            }

        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cells.chunked(7).forEach { weekRow ->
                Row(
                    Modifier.fillMaxWidth().weight(1f, fill = true),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    weekRow.forEach { cell ->
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .aspectRatio(1f, matchHeightConstraintsFirst = false),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (cell) {
                                is CalendarCell.Blank -> {
                                    Box(Modifier.fillMaxSize())
                                }
                                is CalendarCell.Day -> {
                                    val hasEvent = cell.date in eventDates
                                    val surface =
                                        if (cell.isToday) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else if (hasEvent) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
                                        }
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(surface)
                                                .then(
                                                    if (hasEvent) {
                                                        Modifier.clickable {
                                                            onDayWithEventClick(cell.date)
                                                        }
                                                    } else {
                                                        Modifier
                                                    },
                                                )
                                                .padding(4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "${cell.date.dayOfMonth}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                                            color =
                                                if (cell.isToday) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class CalendarCell {
    data object Blank : CalendarCell()

    data class Day(
        val date: LocalDate,
        val isToday: Boolean,
    ) : CalendarCell()
}
