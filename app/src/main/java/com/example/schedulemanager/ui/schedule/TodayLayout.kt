package com.example.schedulemanager.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.dayNames
import com.example.schedulemanager.model.formatMinutes
import com.example.schedulemanager.model.formatTime
import com.example.schedulemanager.model.formatTimeRange
import com.example.schedulemanager.ui.common.LocalUse24Hour
import com.example.schedulemanager.ui.theme.LocalAppTokens

/** B · Today first: what's on now and what's next lead the screen; the rest follows in compact rows. */
@Composable
fun TodaySchedule(args: ScheduleLayoutArgs) {
    val tokens = LocalAppTokens.current
    val selected = args.pagerState.currentPage

    Column(Modifier.fillMaxSize().background(tokens.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (selected == args.now.day) "Today" else dayNames[selected],
                color = tokens.ink,
                fontFamily = tokens.displayFont,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.weight(1f),
            )
            MoreMenuButton(args, tint = tokens.ink)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dayNames.forEachIndexed { index, name ->
                val isSelected = index == selected
                Surface(
                    onClick = { args.onSelectDay(index) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) tokens.ink else tokens.surface,
                    contentColor = if (isSelected) tokens.background else tokens.ink,
                    modifier = Modifier.weight(1f).height(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(name.take(3), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        HorizontalPager(state = args.pagerState, modifier = Modifier.fillMaxSize()) { page ->
            var showEarlier by rememberSaveable(page) { mutableStateOf(false) }
            RefreshableDayList(
                dayIndex = page,
                sessions = args.sessionsOn(page),
                scrollTarget = { 0 },
                spacing = 12.dp,
            ) { sessions ->
                if (page == args.now.day) {
                    todayContent(sessions, args, showEarlier, onToggleEarlier = { showEarlier = !showEarlier })
                } else {
                    sessions.forEach { s -> item(key = s.id) { CompactRow(s, onClick = { args.onSessionClick(s) }) } }
                }
            }
        }
    }
}

private fun LazyListScope.todayContent(
    sessions: List<ClassSession>,
    args: ScheduleLayoutArgs,
    showEarlier: Boolean,
    onToggleEarlier: () -> Unit,
) {
    val now = args.now
    val done = sessions.filter { it.phase(now) == Phase.DONE }
    val current = sessions.firstOrNull { it.phase(now) == Phase.NOW }
    val upcoming = sessions.filter { it.phase(now) == Phase.UPCOMING }

    // The lead card: what's on now, else what's next, else "all done"
    item(key = "hero") {
        when {
            current != null -> HeroCard(current, now, isNow = true, onClick = { args.onSessionClick(current) })
            upcoming.isNotEmpty() -> HeroCard(upcoming.first(), now, isNow = false, onClick = { args.onSessionClick(upcoming.first()) })
            else -> AllDoneCard(done.size)
        }
    }

    val rest = if (current != null) upcoming else upcoming.drop(1)
    if (current != null && rest.isNotEmpty()) {
        item(key = "next-label") { SectionLabel("Up next") }
        val next = rest.first()
        item(key = next.id) { CompactRow(next, trailing = "in ${formatMinutes(next.startMinutes - now.minutes)}", onClick = { args.onSessionClick(next) }) }
    }
    val later = if (current != null) rest.drop(1) else rest
    if (later.isNotEmpty()) {
        item(key = "later-label") { SectionLabel("Later") }
        later.forEach { s -> item(key = s.id) { CompactRow(s, onClick = { args.onSessionClick(s) }) } }
    }

    if (done.isNotEmpty()) {
        item(key = "earlier") { EarlierToggle(done, expanded = showEarlier, onClick = onToggleEarlier) }
        if (showEarlier) {
            done.forEach { s -> item(key = s.id) { CompactRow(s, dimmed = true, onClick = { args.onSessionClick(s) }) } }
        }
    }
}

@Composable
fun TodayAddButton(onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    FloatingActionButton(
        onClick = onClick,
        containerColor = tokens.accent,
        contentColor = tokens.onAccent,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.size(64.dp),
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add session", modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun HeroCard(session: ClassSession, now: Now, isNow: Boolean, onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    val use24Hour = LocalUse24Hour.current
    val ink = session.foregroundColor
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = session.backgroundColor, contentColor = ink),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isNow) {
                    NowPill(background = ink, content = session.backgroundColor)
                } else {
                    Text(
                        "NEXT",
                        color = session.backgroundColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.clip(CircleShape).background(ink).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (isNow) "${formatMinutes(session.endMinutes - now.minutes)} left" else "in ${formatMinutes(session.startMinutes - now.minutes)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    session.subject,
                    fontFamily = tokens.displayFont,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatTimeRange(session.startMinutes, session.endMinutes, use24Hour)} · ${session.classroom}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (isNow) {
                val progress = (now.minutes - session.startMinutes).toFloat() / (session.endMinutes - session.startMinutes)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = 0.16f))
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }
                ) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(ink))
                }
            }
        }
    }
}

@Composable
private fun AllDoneCard(finished: Int) {
    val tokens = LocalAppTokens.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(tokens.surface).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("All done for today", color = tokens.ink, fontFamily = tokens.displayFont, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(if (finished == 1) "1 class finished" else "$finished classes finished", color = tokens.muted, fontSize = 15.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    val tokens = LocalAppTokens.current
    Text(
        text.uppercase(),
        color = tokens.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
    )
}

@Composable
private fun CompactRow(session: ClassSession, onClick: () -> Unit, trailing: String? = null, dimmed: Boolean = false) {
    val tokens = LocalAppTokens.current
    val use24Hour = LocalUse24Hour.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tokens.surface, contentColor = tokens.ink),
        modifier = Modifier.fillMaxWidth().alpha(if (dimmed) 0.55f else 1f),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(session.backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    formatTime(session.startMinutes, use24Hour),
                    color = session.foregroundColor,
                    fontSize = if (use24Hour) 13.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(session.subject, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${session.classroom} · until ${formatTime(session.endMinutes, use24Hour)}",
                    color = tokens.muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                Text(trailing, color = tokens.accentText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EarlierToggle(done: List<ClassSession>, expanded: Boolean, onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        contentColor = tokens.muted,
        border = BorderStroke(1.5.dp, tokens.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Overlapping color dots, one per finished session
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                done.take(4).forEach { s ->
                    Box(
                        Modifier
                            .size(22.dp)
                            .border(2.dp, tokens.background, CircleShape)
                            .padding(2.dp)
                            .background(s.backgroundColor, CircleShape)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Earlier today · ${done.size} finished",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Hide finished" else "Show finished",
            )
        }
    }
}
