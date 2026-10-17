package com.example.schedulemanager.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.SessionPalette
import com.example.schedulemanager.model.formatTime
import com.example.schedulemanager.model.time
import com.example.schedulemanager.ui.theme.ScheduleManagerTheme

@Composable
fun ScheduleTile(
    session: ClassSession,
    modifier: Modifier = Modifier,
    isNow: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val fg = session.foregroundColor
    val shape = RoundedCornerShape(24.dp)
    val colors = CardDefaults.cardColors(containerColor = session.backgroundColor, contentColor = fg)

    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) {
            TileContent(session, isNow)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) {
            TileContent(session, isNow)
        }
    }
}

@Composable
private fun TileContent(session: ClassSession, isNow: Boolean) {
    val fg = session.foregroundColor
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Time column: start on top, a dotted rail, end at the bottom
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SingleLineText(
                text = formatTime(session.startMinutes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .width(2.dp)
                    .weight(1f)
                    .clip(CircleShape)
                    .background(fg.copy(alpha = 0.25f))
            )
            SingleLineText(
                text = formatTime(session.endMinutes),
                style = MaterialTheme.typography.bodyMedium,
                color = fg.copy(alpha = 0.7f),
            )
        }

        Spacer(Modifier.width(16.dp))

        // Divider between time and details
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(fg.copy(alpha = 0.85f))
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = session.subject,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isNow) {
                    Spacer(Modifier.width(8.dp))
                    NowBadge(background = fg, content = session.backgroundColor)
                }
            }
            Spacer(Modifier.height(10.dp))
            Pill(color = fg) {
                SingleLineText(
                    text = session.classroom,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/** "NOW" pill in the tile's colors reversed, with a softly pulsing dot. */
@Composable
private fun NowBadge(background: Color, content: Color) {
    val pulse = rememberInfiniteTransition(label = "now-pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "now-dot",
    )
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer { alpha = dotAlpha }
                .background(content, CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "NOW",
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

/** Text that never wraps; it is cut with an ellipsis if there is really no room. */
@Composable
private fun SingleLineText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Small translucent chip tinted with the tile's foreground color. */
@Composable
private fun Pill(color: Color, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

private val previewSessions = listOf(
    Triple("Mathematics", "Room A-101", SessionPalette.Blue),
    Triple("Physics", "Lab B-204", SessionPalette.Peach),
    Triple("Computer Science", "Computer Lab 3", SessionPalette.Navy),
).mapIndexed { i, (subject, room, colors) ->
    ClassSession(
        id = i.toLong(), dayOfWeek = 0,
        startMinutes = time(8 + i * 2), endMinutes = time(9 + i * 2, 30),
        classroomId = i.toLong(), classroom = room, subjectId = i.toLong(), subject = subject,
        backgroundColor = colors.background, foregroundColor = colors.foreground,
    )
}

@Preview(showBackground = true, name = "Normal width")
@Preview(showBackground = true, name = "Narrow width", widthDp = 300)
@Composable
private fun ScheduleTilePreview() {
    ScheduleManagerTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            previewSessions.forEachIndexed { i, session -> ScheduleTile(session, isNow = i == 1) }
        }
    }
}
