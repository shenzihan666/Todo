package com.todolist.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.todolist.app.R
import com.todolist.app.TodoListApplication
import com.todolist.app.navigation.MainTab
import com.todolist.app.ui.bills.BillsScreen
import com.todolist.app.ui.components.TodoListAppBar
import com.todolist.app.ui.home.HomeContent
import com.todolist.app.ui.home.SpeechViewModel
import com.todolist.app.ui.schedule.ScheduleScreen
import com.todolist.app.ui.settings.SettingsRoute

@Composable
fun MainRoute(
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as TodoListApplication
    val prefs = app.container.userPreferencesRepository
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, prefs) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            prefs.isLoggedIn.collect { loggedIn ->
                if (!loggedIn) {
                    onNavigateToAuth()
                }
            }
        }
    }

    val speechViewModel: SpeechViewModel = viewModel(factory = app.speechViewModelFactory())
    MainScreen(
        modifier = modifier,
        speechViewModel = speechViewModel,
        onLoggedOut = onNavigateToAuth,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    speechViewModel: SpeechViewModel,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = MainTab.entries[tabIndex]

    val title =
        when (selectedTab) {
            MainTab.Home -> stringResource(R.string.home_title)
            MainTab.Schedule -> stringResource(R.string.schedule_title)
            MainTab.Bills -> stringResource(R.string.bills_title)
            MainTab.Settings -> stringResource(R.string.settings)
        }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TodoListAppBar(title = title)
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, tab ->
                    val selected = index == tabIndex
                    val (label, desc, icon) =
                        when (tab) {
                            MainTab.Home ->
                                Triple(
                                    stringResource(R.string.nav_home),
                                    stringResource(R.string.content_desc_nav_home),
                                    Icons.Filled.Home,
                                )
                            MainTab.Schedule ->
                                Triple(
                                    stringResource(R.string.nav_schedule),
                                    stringResource(R.string.content_desc_nav_schedule),
                                    Icons.Filled.CalendarMonth,
                                )
                            MainTab.Bills ->
                                Triple(
                                    stringResource(R.string.nav_bills),
                                    stringResource(R.string.content_desc_nav_bills),
                                    Icons.Filled.AccountBalanceWallet,
                                )
                            MainTab.Settings ->
                                Triple(
                                    stringResource(R.string.nav_settings),
                                    stringResource(R.string.content_desc_nav_settings),
                                    Icons.Filled.Settings,
                                )
                        }
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tabIndex = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(label) },
                        modifier = Modifier.semantics { contentDescription = desc },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when (selectedTab) {
                MainTab.Home ->
                    HomeContent(
                        speechViewModel = speechViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                MainTab.Schedule -> ScheduleScreen(modifier = Modifier.fillMaxSize())
                MainTab.Bills -> BillsScreen(modifier = Modifier.fillMaxSize())
                MainTab.Settings ->
                    SettingsRoute(
                        onNavigateBack = {},
                        onLoggedOut = onLoggedOut,
                        showNavigationBack = false,
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }
    }
}
