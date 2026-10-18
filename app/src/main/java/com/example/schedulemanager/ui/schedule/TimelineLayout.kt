package com.example.schedulemanager.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.dayNames
import com.example.schedulemanager.model.formatTime
import com.example.schedulemanager.ui.common.LocalUse24Hour
import com.example.schedulemanager.ui.theme.LocalAppTokens

/** A · Timeline: the day on a time ruler, tile height follows the session length, live now line. */
@Composable
fun TimelineSchedule(args: ScheduleLayoutArgs) {
    val tokens = LocalAppTokens.current
    val selected = args.pagerState.currentPage
    val daysWithClasses = args.week.orEmpty().map { it.dayOfWeek }.toSet()

    Column(Modifier.fillMaxSize().background(tokens.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (selected == args.now.day) "TODAY" else " ",
                    color = tokens.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                )
                Text(dayNames[selected], color = tokens.ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                Text(args.summary(selected), color = tokens.muted, fontSize = 14.sp)
            }
            MoreMenuButton(args, tint = tokens.ink)
        }

        // Day letters in seven equal shares of the width; a dot marks days that have classes
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            dayNames.forEachIndexed { index, name ->
                val isSelected = index == selected
                Surface(
                    onClick = { args.onSelectDay(index) },
                    shape = RoundedCornerShape(22.dp),
                    color = if (isSelected) tokens.accent else Color.Transparent,
                    contentColor = if (isSelected) tokens.onAccent else tokens.ink,
                    modifier = Modifier.weight(1f).height(60.dp).semantics { contentDescription = name },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(name.take(1), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier.size(5.dp).background(
                                when {
                                    index !in daysWithClasses -> Color.Transparent
                                    isSelected -> tokens.onAccent
                                    else -> tokens.muted.copy(alpha = 0.6f)
                                },
                                CircleShape,
                            )
                        )
                    }
                }
            }
        }

        HorizontalPager(state = args.pagerState, modifier = Modifier.fillMaxSize()) { page ->
            RefreshableDayList(
                dayIndex = page,
                sessions = args.sessionsOn(page),
                scrollTarget = { list -> list.indexOfFirst { it.phase(args.now) != Phase.DONE } },
                horizontalPadding = 16.dp,
            ) { sessions ->
                sessions.forEach { session ->
                    item(key = session.id) {
                        TimelineRow(session, args.now, onClick = { args.onSessionClick(session) })
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineAddButton(onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = tokens.accent,
        contentColor = tokens.onAccent,
        shape = RoundedCornerShape(18.dp),
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text("Add session", fontWeight = FontWeight.Bold) },
    )
}

@Composable
private fun TimelineRow(session: ClassSession, now: Now, onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    val use24Hour = LocalUse24Hour.current
    val phase = session.phase(now)
    val duration = session.endMinutes - session.startMinutes
    // Longer sessions get taller tiles, within limits so short and long ones stay usable
    val height = (duration * 0.95f).coerceIn(68f, 150f).dp

    Box(Modifier.fillMaxWidth().height(height).alpha(if (phase == Phase.DONE) 0.5f else 1f)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.width(56.dp).fillMaxSize().padding(vertical = 4.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatTime(session.startMinutes, use24Hour), color = tokens.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(formatTime(session.endMinutes, use24Hour), color = tokens.muted, fontSize = 12.sp, maxLines = 1)
            }
            Card(
                onClick = onClick,
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = session.backgroundColor, contentColor = session.foregroundColor),
                modifier = Modifier.weight(1f).fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            session.subject,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        when (phase) {
                            Phase.NOW -> NowPill(background = session.foregroundColor, content = session.backgroundColor)
                            Phase.DONE -> Icon(Icons.Default.Check, contentDescription = "Finished", modifier = Modifier.size(18.dp))
                            Phase.UPCOMING -> Unit
                        }
                    }
                    Text(session.classroom, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(0.8f))
                }
            }
        }

        if (phase == Phase.NOW) {
            // The now line, placed at how far into the session we are
            val fraction = (now.minutes - session.startMinutes).toFloat() / duration
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = (height * fraction - 9.dp).coerceAtLeast(0.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatTime(now.minutes, use24Hour),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.background(tokens.live, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
                )
                Box(Modifier.weight(1f).height(2.dp).background(tokens.live))
            }
        }
    }
}
