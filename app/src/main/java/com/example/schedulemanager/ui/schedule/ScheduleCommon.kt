package com.example.schedulemanager.ui.schedule

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.currentDayIndex
import com.example.schedulemanager.model.currentMinutes
import com.example.schedulemanager.model.dayNames
import com.example.schedulemanager.ui.theme.LocalAppTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The current day of the week (Monday = 0) and minute of the day. */
@Immutable
data class Now(val day: Int, val minutes: Int)

/** Where a session stands relative to now. Sessions on other days are always [UPCOMING]. */
enum class Phase { DONE, NOW, UPCOMING }

fun ClassSession.phase(now: Now): Phase = when {
    dayOfWeek != now.day -> Phase.UPCOMING
    now.minutes >= endMinutes -> Phase.DONE
    isOngoingAt(now.minutes) -> Phase.NOW
    else -> Phase.UPCOMING
}

/** Everything a theme's schedule layout needs from the shared screen. */
class ScheduleLayoutArgs(
    val pagerState: PagerState,
    val now: Now,
    /** All sessions of the week, null while loading. */
    val week: List<ClassSession>?,
    val onSelectDay: (Int) -> Unit,
    val onSessionClick: (ClassSession) -> Unit,
    val onAdd: () -> Unit,
    val onScan: () -> Unit,
    val onShare: () -> Unit,
    val onSettings: () -> Unit,
) {
    fun sessionsOn(day: Int): List<ClassSession>? =
        week?.filter { it.dayOfWeek == day }?.sortedBy { it.startMinutes }

    /** "6 classes · 2 left" (the "left" part only for today). */
    fun summary(day: Int): String {
        val sessions = sessionsOn(day) ?: return ""
        if (sessions.isEmpty()) return "No classes"
        val count = if (sessions.size == 1) "1 class" else "${sessions.size} classes"
        if (day != now.day) return count
        val left = sessions.count { it.phase(now) != Phase.DONE }
        return if (left == 0) "$count · all done" else "$count · $left left"
    }
}

/**
 * The current time, updated at the start of every minute while the screen is visible,
 * and refreshed straight away when the app comes back to the foreground.
 */
@Composable
fun rememberNow(): State<Now> {
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

/** ⋮ with Scan / Share / Settings. */
@Composable
fun MoreMenuButton(args: ScheduleLayoutArgs, tint: Color, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = tint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Scan") },
                leadingIcon = { Icon(Icons.Outlined.AddCircle, contentDescription = null) },
                onClick = { open = false; args.onScan() },
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                onClick = { open = false; args.onShare() },
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = { open = false; args.onSettings() },
            )
        }
    }
}

/**
 * A day's list with pull-to-refresh. Once the day has loaded, and after each refresh, it scrolls to
 * [scrollTarget] (e.g. the session that's on now). Shows [EmptyDay] when there's nothing that day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableDayList(
    dayIndex: Int,
    sessions: List<ClassSession>?,
    scrollTarget: (List<ClassSession>) -> Int,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    spacing: Dp = 10.dp,
    content: LazyListScope.(List<ClassSession>) -> Unit,
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var reloadCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val loaded = sessions != null
    LaunchedEffect(loaded, reloadCount) {
        val list = sessions ?: return@LaunchedEffect
        val index = scrollTarget(list).coerceAtLeast(0)
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
            // Extra bottom space so the add button never covers the last item
            contentPadding = PaddingValues(start = horizontalPadding, top = 4.dp, end = horizontalPadding, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            when {
                sessions == null -> Unit
                sessions.isEmpty() -> item { EmptyDay(dayIndex, Modifier.fillParentMaxSize()) }
                else -> content(sessions)
            }
        }
    }
}

/** "● NOW" pill with a softly pulsing dot. */
@Composable
fun NowPill(background: Color, content: Color, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "now-pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "now-dot",
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .graphicsLayer { alpha = dotAlpha }
                .background(content, CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text("NOW", color = content, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
    }
}

@Composable
fun EmptyDay(dayIndex: Int, modifier: Modifier = Modifier) {
    val tokens = LocalAppTokens.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "No classes on ${dayNames[dayIndex]}\nTap + to add one",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.muted,
            modifier = Modifier.padding(bottom = 80.dp),
        )
    }
}
