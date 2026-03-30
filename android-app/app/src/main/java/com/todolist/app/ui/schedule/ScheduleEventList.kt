package com.todolist.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.todolist.app.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MonthRailWidth = 28.dp
private val DateSidebarWidth = 56.dp
private val EventStripeWidth = 4.dp
private const val MaxVisibleEvents = 3

@Composable
private fun scheduleAccentColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return listOf(
        scheme.primary,
        scheme.tertiary,
        scheme.secondary,
        scheme.outline,
    )
}

/**
 * Agenda layout: left month rail + date sidebar + event cards (reference-style).
 * Only dates that have events appear. Opens near today.
 */
@Composable
fun ScheduleEventList(
    events: List<ScheduleEvent>,
    today: LocalDate,
    scrollToDate: LocalDate?,
    onScrollToDateConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val dayGroups =
        remember(events) {
            events
                .groupBy { it.date }
                .toSortedMap()
        }
    val days = remember(dayGroups) { dayGroups.keys.toList() }

    val anchorIndex =
        remember(days, today) {
            if (days.isEmpty()) {
                0
            } else {
                val firstTodayOrFuture = days.indexOfFirst { d -> !d.isBefore(today) }
                if (firstTodayOrFuture == -1) {
                    days.lastIndex
                } else {
                    firstTodayOrFuture
                }
            }
        }

    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = anchorIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0)),
        )

    LaunchedEffect(scrollToDate, days) {
        val target = scrollToDate ?: return@LaunchedEffect
        if (days.isEmpty()) return@LaunchedEffect
        val idx = days.indexOf(target)
        if (idx >= 0) {
            listState.scrollToItem(idx)
        }
        onScrollToDateConsumed()
    }

    if (days.isEmpty()) {
        Text(
            text = stringResource(R.string.schedule_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(24.dp),
        )
        return
    }

    val accents = scheduleAccentColors()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        itemsIndexed(days, key = { _, d -> d.toString() }) { index, date ->
            val prev = days.getOrNull(index - 1)
            val showMonthYear =
                prev == null || YearMonth.from(prev) != YearMonth.from(date)
            val dayEvents = dayGroups[date].orEmpty()
            ScheduleDayBlock(
                date = date,
                events = dayEvents,
                today = today,
                showMonthYear = showMonthYear,
                locale = locale,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                accents = accents,
            )
            if (index < days.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun ScheduleDayBlock(
    date: LocalDate,
    events: List<ScheduleEvent>,
    today: LocalDate,
    showMonthYear: Boolean,
    locale: Locale,
    accents: List<Color>,
    modifier: Modifier = Modifier,
) {
    val ym = YearMonth.from(date)
    val weekday =
        remember(date, locale) {
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        }
    val isToday = date == today

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        MonthYearRail(
            yearMonth = ym,
            visible = showMonthYear,
            locale = locale,
            modifier = Modifier.width(MonthRailWidth),
        )
        DateSidebar(
            weekdayLabel = weekday,
            dayOfMonth = date.dayOfMonth,
            selected = isToday,
            modifier = Modifier.width(DateSidebarWidth),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val visible = events.take(MaxVisibleEvents)
            val hiddenCount = events.size - visible.size
            visible.forEachIndexed { i, ev ->
                ScheduleEventCard(
                    event = ev,
                    stripeColor = accents[ev.accentIndex.mod(accents.size)],
                    timeLabel = formatTimeRange(ev, locale),
                )
            }
            if (hiddenCount > 0) {
                ScheduleMoreRow(
                    hiddenEvents = events.drop(MaxVisibleEvents),
                    hiddenCount = hiddenCount,
                    accents = accents,
                )
            }
        }
    }
}

@Composable
private fun MonthYearRail(
    yearMonth: YearMonth,
    visible: Boolean,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val label =
        remember(yearMonth, locale) {
            yearMonth.format(DateTimeFormatter.ofPattern("yyyy MMM", locale))
        }
    Box(
        modifier =
            modifier
                .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (visible) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                maxLines = 1,
                modifier =
                    Modifier
                        .padding(top = 24.dp)
                        .rotate(-90f),
            )
        }
    }
}

@Composable
private fun DateSidebar(
    weekdayLabel: String,
    dayOfMonth: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = weekdayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (selected) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = "$dayOfMonth",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        } else {
            Text(
                text = "$dayOfMonth",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp),
            )
        }
    }
}

@Composable
private fun ScheduleEventCard(
    event: ScheduleEvent,
    stripeColor: Color,
    timeLabel: String,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(EventStripeWidth)
                        .height(56.dp)
                        .background(
                            stripeColor,
                            RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
                        ),
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ScheduleMoreRow(
    hiddenEvents: List<ScheduleEvent>,
    hiddenCount: Int,
    accents: List<Color>,
) {
    Row(
        modifier = Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        hiddenEvents.take(4).forEach { ev ->
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .background(
                            accents[ev.accentIndex.mod(accents.size)],
                            CircleShape,
                        ),
            )
        }
        Text(
            text = stringResource(R.string.schedule_more, hiddenCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTimeRange(
    event: ScheduleEvent,
    locale: Locale,
): String {
    val tf = DateTimeFormatter.ofPattern("HH:mm", locale)
    return "${event.start.format(tf)} → ${event.end.format(tf)}"
}
