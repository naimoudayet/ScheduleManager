package com.example.schedulemanager.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.schedulemanager.model.TileColors
import com.example.schedulemanager.model.formatMinutes
import com.example.schedulemanager.model.formatTimeRange
import com.example.schedulemanager.ui.common.LocalUse24Hour
import com.example.schedulemanager.ui.theme.LocalAppTokens

/** A dialog that covers the whole screen, in the theme's background color. */
@Composable
fun FullScreenEditorDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = LocalAppTokens.current.background) { content() }
    }
}

@Composable
fun EditorSectionTitle(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalAppTokens.current
    Text(
        text.uppercase(),
        color = tokens.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = modifier,
    )
}

/** A pill you tap to choose; selected = filled with the accent. [swatch] shows a subject's colors. */
@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, swatch: TileColors? = null) {
    val tokens = LocalAppTokens.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) tokens.accent else tokens.surface,
        contentColor = if (selected) tokens.onAccent else tokens.ink,
        border = BorderStroke(1.5.dp, if (selected) tokens.accent else tokens.outline),
        modifier = Modifier.height(42.dp).semantics { this.selected = selected; role = Role.RadioButton },
    ) {
        Row(
            Modifier.padding(start = if (swatch != null) 10.dp else 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (swatch != null) {
                Row(
                    Modifier
                        .size(16.dp)
                        .background(swatch.background, CircleShape)
                        .border(1.5.dp, swatch.foreground, CircleShape)
                ) {}
            }
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** "+ New" with a dashed outline. */
@Composable
fun NewChip(label: String, onClick: () -> Unit, height: Dp = 42.dp, modifier: Modifier = Modifier) {
    val tokens = LocalAppTokens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(height / 2),
        color = Color.Transparent,
        contentColor = tokens.accentText,
        modifier = modifier
            .height(height)
            .drawBehind {
                drawRoundRect(
                    color = tokens.muted.copy(alpha = 0.7f),
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            },
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/**
 * Under the time controls: the length when all is well, or what's wrong — end before start, or an
 * overlap with a one-tap "Move to …" fix.
 */
@Composable
fun TimeStatus(state: EditorState) {
    val tokens = LocalAppTokens.current
    val use24Hour = LocalUse24Hour.current
    val clash = state.clash
    when {
        !state.timesValid -> Text("End time must be after start time", color = tokens.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        clash != null -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.dangerContainer, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = tokens.onDangerContainer, modifier = Modifier.size(18.dp))
                Text(
                    "Overlaps ${clash.subject}, ${formatTimeRange(clash.startMinutes, clash.endMinutes, use24Hour)}",
                    color = tokens.onDangerContainer,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            val suggestion = state.suggestedStart
            if (suggestion != null) {
                Surface(
                    onClick = state::applySuggestion,
                    shape = CircleShape,
                    color = tokens.background,
                    contentColor = tokens.onDangerContainer,
                ) {
                    Text(
                        "Move to ${formatTimeRange(suggestion, suggestion + state.length, use24Hour)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            } else {
                Text("No free time of this length later that day", color = tokens.onDangerContainer, fontSize = 13.sp)
            }
        }
        else -> Text(formatMinutes(state.length), color = tokens.muted, fontSize = 13.sp)
    }
    state.saveError?.let { Text(it, color = tokens.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}
