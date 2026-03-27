package com.todolist.app.ui.schedule

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.todolist.app.R
import java.time.DayOfWeek
import java.time.LocalDate
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
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val events by viewModel.events.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 2 })
    val today = remember { LocalDate.now() }
    val visibleMonth = remember { YearMonth.from(today) }

    val eventDates = remember(events) { events.map { it.date }.toSet() }

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
                        events = events,
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

        if (isLoading && events.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
            )
        }
    }
}
