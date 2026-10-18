package com.example.schedulemanager.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.schedulemanager.model.AppStyle
import com.example.schedulemanager.model.currentDayIndex
import com.example.schedulemanager.model.dayNames
import com.example.schedulemanager.ui.session.EditorTarget
import com.example.schedulemanager.ui.session.SessionEditor
import com.example.schedulemanager.ui.family.FamilyBar
import com.example.schedulemanager.ui.family.RightNowStrip
import com.example.schedulemanager.ui.share.ImportActions
import com.example.schedulemanager.ui.share.ImportFlowDialogs
import com.example.schedulemanager.ui.share.rememberImportFlowState
import com.example.schedulemanager.ui.theme.LocalAppTokens
import kotlinx.coroutines.launch

/**
 * The main screen. The data, the day pager, the ⋮ menu, the editor and importing are shared;
 * the header, day picker, list and add button come from the active theme.
 */
@Composable
fun ScheduleScreen(
    onOpenSettings: () -> Unit,
    onShare: () -> Unit,
    viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory),
) {
    val tokens = LocalAppTokens.current
    val pagerState = rememberPagerState(initialPage = currentDayIndex()) { dayNames.size }
    val scope = rememberCoroutineScope()
    val week by viewModel.week.collectAsState()
    val classrooms by viewModel.classrooms.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val now by rememberNow()
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val showMessage: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
    val importFlow = rememberImportFlowState()
    val parentMode by viewModel.parentMode.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val everyone by viewModel.everyone.collectAsState()

    // Switching person closes the editor, so it can never save into the wrong week
    LaunchedEffect(activeProfileId) { editorTarget = null }

    val addSession = { editorTarget = EditorTarget.New(pagerState.currentPage) }
    val args = ScheduleLayoutArgs(
        pagerState = pagerState,
        now = now,
        week = week,
        onSelectDay = { day -> scope.launch { pagerState.animateScrollToPage(day) } },
        onSessionClick = { editorTarget = EditorTarget.Edit(it) },
        onAdd = addSession,
        onScan = { importFlow.scan(context, showMessage) },
        onShare = onShare,
        onSettings = onOpenSettings,
    )

    Scaffold(
        containerColor = tokens.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButtonPosition = if (tokens.style == AppStyle.NIGHT) FabPosition.Center else FabPosition.End,
        floatingActionButton = {
            when (tokens.style) {
                AppStyle.TIMELINE -> TimelineAddButton(addSession)
                AppStyle.TODAY -> TodayAddButton(addSession)
                AppStyle.NIGHT -> NightAddButton(addSession)
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            // Parent mode: whose week this is, and where everyone is right now
            val activeId = activeProfileId
            if (parentMode && activeId != null) {
                FamilyBar(
                    profiles = profiles,
                    activeId = activeId,
                    onSelect = viewModel::setActiveProfile,
                    onAddChild = importFlow::startAddChild,
                )
                if (profiles.size >= 2) RightNowStrip(profiles, everyone, now, onSelect = viewModel::setActiveProfile)
            }
            Box(Modifier.weight(1f)) {
                when (tokens.style) {
                    AppStyle.TIMELINE -> TimelineSchedule(args)
                    AppStyle.TODAY -> TodaySchedule(args)
                    AppStyle.NIGHT -> NightSchedule(args)
                }
            }
        }
    }

    ImportFlowDialogs(
        state = importFlow,
        actions = ImportActions(
            parentMode = parentMode,
            profiles = profiles,
            importIntoActive = viewModel::importSchedule,
            importInto = viewModel::importInto,
            importAsNewChild = viewModel::importAsNewChild,
            addChild = viewModel::addChild,
            activate = viewModel::setActiveProfile,
        ),
        onMessage = showMessage,
    )

    editorTarget?.let { target ->
        SessionEditor(
            target = target,
            week = week.orEmpty(),
            classrooms = classrooms,
            subjects = subjects,
            onSave = viewModel::saveSession,
            onDelete = viewModel::deleteSession,
            onAddItem = viewModel::addItem,
            onDismiss = { editorTarget = null },
        )
    }
}
