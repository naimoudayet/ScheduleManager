package com.example.schedulemanager.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.currentDayIndex
import com.example.schedulemanager.model.currentMinutes
import com.example.schedulemanager.model.dayNames
import com.example.schedulemanager.ui.ScheduleTile
import com.example.schedulemanager.ui.session.EditorTarget
import com.example.schedulemanager.ui.session.SessionEditorSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onOpenSettings: () -> Unit,
    viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory),
) {
    val pagerState = rememberPagerState(initialPage = currentDayIndex()) { dayNames.size }
    val scope = rememberCoroutineScope()
    val classrooms by viewModel.classrooms.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    val now by rememberNow()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editorTarget = EditorTarget.New(pagerState.currentPage) }) {
                Icon(Icons.Default.Add, contentDescription = "Add session")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
                dayNames.forEachIndexed { index, day ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(day) },
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val sessions by viewModel.sessions(page).collectAsState()
                DayScreen(
                    dayIndex = page,
                    sessions = sessions,
                    now = now,
                    onSessionClick = { editorTarget = EditorTarget.Edit(it) },
                )
            }
        }
    }

    editorTarget?.let { target ->
        SessionEditorSheet(
            target = target,
            classrooms = classrooms,
            subjects = subjects,
            onSave = viewModel::saveSession,
            onDelete = viewModel::deleteSession,
            onAddItem = viewModel::addItem,
            onDismiss = { editorTarget = null },
        )
    }
}

/** [sessions] is null while the day is still loading from the database. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayScreen(
    dayIndex: Int,
    sessions: List<ClassSession>?,
    now: Now,
    onSessionClick: (ClassSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var reloadCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Once the day has loaded, and after every pull-to-refresh, jump to the ongoing session
    // (or stay at the top if nothing is on right now).
    val loaded = sessions != null
    LaunchedEffect(loaded, reloadCount) {
        val list = sessions ?: return@LaunchedEffect
        val index = ongoingIndex(dayIndex, list)
        if (reloadCount == 0) listState.scrollToItem(index) else listState.animateScrollToItem(index)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            // Data is live from the database, so refreshing only re-centres on the current session
            scope.launch {
                isRefreshing = true
                delay(400)
                reloadCount++
                isRefreshing = false
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        // The list is always present (even when empty) so the pull gesture works on every day
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Extra bottom space so the FAB never covers the last tile
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                sessions == null -> Unit
                sessions.isEmpty() -> item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No classes on ${dayNames[dayIndex]} 🎉\nTap + to add one",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> items(sessions, key = { it.id }) { session ->
                    ScheduleTile(
                        session = session,
                        isNow = dayIndex == now.day && session.isOngoingAt(now.minutes),
                        onClick = { onSessionClick(session) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** The current day of the week (Monday = 0) and minute of the day. */
private data class Now(val day: Int, val minutes: Int)

/**
 * The current time, updated at the start of every minute while the screen is visible,
 * and refreshed straight away when the app comes back to the foreground.
 */
@Composable
private fun rememberNow(): State<Now> {
    val now = remember { mutableStateOf(Now(currentDayIndex(), currentMinutes())) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now.value = Now(currentDayIndex(), currentMinutes())
                delay(60_000 - System.currentTimeMillis() % 60_000)
            }
        }
    }
    return now
}

/** Index of the session happening right now (today's tab only), or 0 (the top) if there is none. */
private fun ongoingIndex(dayIndex: Int, sessions: List<ClassSession>): Int {
    if (dayIndex != currentDayIndex()) return 0
    val now = currentMinutes()
    return sessions.indexOfFirst { it.isOngoingAt(now) }.coerceAtLeast(0)
}
