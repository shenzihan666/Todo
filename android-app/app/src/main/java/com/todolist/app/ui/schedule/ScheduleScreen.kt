package com.todolist.app.ui.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.todolist.app.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Schedule: page 1 = chronological event list (only days with items); page 0 = month calendar.
 * Only rows with events are shown, in time order. The list opens near today.
 * Pull down (scroll toward the top of the list) to see earlier history.
 * Swipe right on the list to open the month grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 2 })
    val today = remember { LocalDate.now() }
    val weekStart =
        remember(today) {
            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
    val visibleMonth = remember { YearMonth.from(today) }

    val sampleMeeting = stringResource(R.string.schedule_sample_meeting)
    val sampleFocus = stringResource(R.string.schedule_sample_focus)
    val samplePast = stringResource(R.string.schedule_sample_past)
    val sampleDesign = stringResource(R.string.schedule_sample_design_review)
    val sampleLunch = stringResource(R.string.schedule_sample_lunch)
    val sampleRelease = stringResource(R.string.schedule_sample_app_release)
    val sampleStandup = stringResource(R.string.schedule_sample_standup)
    val sampleEvening = stringResource(R.string.schedule_sample_evening_sync)
    val sampleEvents =
        remember(
            weekStart,
            sampleMeeting,
            sampleFocus,
            samplePast,
            sampleDesign,
            sampleLunch,
            sampleRelease,
            sampleStandup,
            sampleEvening,
        ) {
            val tuesday = weekStart.plusDays(1)
            listOf(
                ScheduleEvent(
                    id = "0",
                    title = samplePast,
                    date = weekStart.minusDays(7),
                    start = LocalTime.of(9, 30),
                    end = LocalTime.of(10, 0),
                    accentIndex = 0,
                ),
                ScheduleEvent(
                    id = "1a",
                    title = sampleMeeting,
                    date = tuesday,
                    start = LocalTime.of(9, 0),
                    end = LocalTime.of(9, 45),
                    accentIndex = 0,
                ),
                ScheduleEvent(
                    id = "1b",
                    title = sampleDesign,
                    date = tuesday,
                    start = LocalTime.of(10, 30),
                    end = LocalTime.of(11, 30),
                    accentIndex = 1,
                ),
                ScheduleEvent(
                    id = "1c",
                    title = sampleLunch,
                    date = tuesday,
                    start = LocalTime.of(12, 0),
                    end = LocalTime.of(13, 0),
                    accentIndex = 2,
                ),
                ScheduleEvent(
                    id = "1d",
                    title = sampleRelease,
                    date = tuesday,
                    start = LocalTime.of(14, 0),
                    end = LocalTime.of(16, 30),
                    accentIndex = 1,
                ),
                ScheduleEvent(
                    id = "1e",
                    title = sampleStandup,
                    date = tuesday,
                    start = LocalTime.of(17, 0),
                    end = LocalTime.of(17, 15),
                    accentIndex = 3,
                ),
                ScheduleEvent(
                    id = "1f",
                    title = sampleEvening,
                    date = tuesday,
                    start = LocalTime.of(18, 0),
                    end = LocalTime.of(18, 30),
                    accentIndex = 0,
                ),
                ScheduleEvent(
                    id = "2",
                    title = sampleFocus,
                    date = weekStart.plusDays(4),
                    start = LocalTime.of(14, 30),
                    end = LocalTime.of(16, 0),
                    accentIndex = 2,
                ),
            )
        }

    val eventDates = remember(sampleEvents) { sampleEvents.map { it.date }.toSet() }

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 ->
                    ScheduleMonthCalendar(
                        visibleMonth = visibleMonth,
                        today = today,
                        eventDates = eventDates,
                        modifier = Modifier.fillMaxSize(),
                    )
                1 ->
                    ScheduleEventList(
                        events = sampleEvents,
                        today = today,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp),
                    )
            }
        }

        if (pagerState.currentPage == 0) {
            Text(
                text = stringResource(R.string.schedule_swipe_hint_calendar),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
            )
        }
    }
}
