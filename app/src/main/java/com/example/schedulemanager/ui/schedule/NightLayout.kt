package com.example.schedulemanager.ui.schedule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.dayNames
import com.example.schedulemanager.model.formatMinutes
import com.example.schedulemanager.model.formatTime
import com.example.schedulemanager.ui.common.LocalUse24Hour
import com.example.schedulemanager.ui.theme.LocalAppTokens

/** C · Night bold: a big live clock, a stretchy day picker and bold cards; only "now" is in full color. */
@Composable
fun NightSchedule(args: ScheduleLayoutArgs) {
    val tokens = LocalAppTokens.current
    val use24Hour = LocalUse24Hour.current
    val selected = args.pagerState.currentPage

    Column(Modifier.fillMaxSize().background(tokens.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                dayNames[args.now.day].uppercase(),
                color = tokens.muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                modifier = Modifier.weight(1f),
            )
            MoreMenuButton(args, tint = tokens.ink)
        }

        // The live clock, with the AM/PM marker kept small in 12-hour mode
        val clock = formatTime(args.now.minutes, use24Hour)
        Text(
            buildAnnotatedString {
                val split = clock.indexOf(' ')
                if (split < 0) {
                    append(clock)
                } else {
                    append(clock.substring(0, split))
                    withStyle(SpanStyle(fontSize = 24.sp, color = tokens.muted)) { append(" " + clock.substring(split + 1)) }
                }
            },
            color = tokens.ink,
            fontFamily = tokens.displayFont,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.5).sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            statusLine(args),
            color = tokens.muted,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
        )

        // Day picker: the selected day stretches into a pill with its short name
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dayNames.forEachIndexed { index, name ->
                val isSelected = index == selected
                val weight by animateFloatAsState(if (isSelected) 2.2f else 1f, label = "day-weight")
                Surface(
                    onClick = { args.onSelectDay(index) },
                    shape = RoundedCornerShape(if (isSelected) 23.dp else 14.dp),
                    color = if (isSelected) tokens.accent else tokens.surface,
                    contentColor = if (isSelected) tokens.onAccent else tokens.ink,
                    modifier = Modifier.weight(weight).height(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (isSelected) name.take(3) else name.take(1), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                }
            }
        }

        HorizontalPager(state = args.pagerState, modifier = Modifier.fillMaxSize()) { page ->
            RefreshableDayList(
                dayIndex = page,
                sessions = args.sessionsOn(page),
                scrollTarget = { list -> list.indexOfFirst { it.phase(args.now) != Phase.DONE } },
            ) { sessions ->
                sessions.forEach { s -> item(key = s.id) { NightCard(s, s.phase(args.now), onClick = { args.onSessionClick(s) }) } }
            }
        }
    }
}

@Composable
fun NightAddButton(onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = tokens.accent,
        contentColor = tokens.onAccent,
        shape = RoundedCornerShape(30.dp),
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text("Add session", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
    )
}

/** "Chemistry ends in 56 min" / "Next: Art & Design in 1 h 26 min" / "Nothing else today". */
private fun statusLine(args: ScheduleLayoutArgs): String {
    val today = args.sessionsOn(args.now.day) ?: return " "
    today.firstOrNull { it.phase(args.now) == Phase.NOW }?.let {
        return "${it.subject} ends in ${formatMinutes(it.endMinutes - args.now.minutes)}"
    }
    today.firstOrNull { it.phase(args.now) == Phase.UPCOMING }?.let {
        return "Next: ${it.subject} in ${formatMinutes(it.startMinutes - args.now.minutes)}"
    }
    return if (today.isEmpty()) "No classes today" else "Nothing else today"
}

@Composable
private fun NightCard(session: ClassSession, phase: Phase, onClick: () -> Unit) {
    val tokens = LocalAppTokens.current
    val use24Hour = LocalUse24Hour.current
    val isNow = phase == Phase.NOW
    // The card that's on now takes its subject color, and its time block flips to the ink color
    val cardColor = if (isNow) session.backgroundColor else tokens.surface
    val cardInk = if (isNow) session.foregroundColor else tokens.ink
    val blockColor = if (isNow) session.foregroundColor else session.backgroundColor
    val blockInk = if (isNow) session.backgroundColor else session.foregroundColor

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = cardInk),
        modifier = Modifier.fillMaxWidth().alpha(if (phase == Phase.DONE) 0.45f else 1f),
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                Modifier.size(62.dp).clip(RoundedCornerShape(16.dp)).background(blockColor),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val small = !use24Hour
                Text(formatTime(session.startMinutes, use24Hour), color = blockInk, fontSize = if (small) 12.sp else 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(formatTime(session.endMinutes, use24Hour), color = blockInk.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(session.subject, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(session.classroom, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(0.7f))
            }
            if (isNow) NowPill(background = session.foregroundColor, content = session.backgroundColor)
        }
    }
}
