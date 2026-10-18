package com.example.schedulemanager.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.schedulemanager.model.AppStyle
import com.example.schedulemanager.model.SessionPalette
import com.example.schedulemanager.ui.theme.LocalAppTokens
import com.example.schedulemanager.ui.theme.appTokens

/** Three cards, each a tiny preview drawn in that theme's own colors and fonts (light or dark as set). */
@Composable
fun ThemePicker(selected: AppStyle, onSelect: (AppStyle) -> Unit) {
    val current = LocalAppTokens.current
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppStyle.entries.forEach { style ->
            ThemeCard(style, isSelected = style == selected, dark = current.dark, onClick = { onSelect(style) })
        }
    }
}

@Composable
private fun ThemeCard(style: AppStyle, isSelected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val current = LocalAppTokens.current
    val t = appTokens(style, dark)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = current.surface,
        contentColor = current.ink,
        border = BorderStroke(if (isSelected) 2.5.dp else 1.dp, if (isSelected) current.accent else current.outline),
        modifier = Modifier.fillMaxWidth().semantics { this.selected = isSelected; role = Role.RadioButton },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Mini preview: the theme's background with one session drawn its way
            Box(
                Modifier.size(width = 96.dp, height = 72.dp).clip(RoundedCornerShape(14.dp)).background(t.background).padding(8.dp),
            ) {
                when (style) {
                    AppStyle.TIMELINE -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        PreviewBar(SessionPalette.Blue.background, 0.9f, 18.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(10.dp).height(2.dp).background(t.live))
                            PreviewBar(SessionPalette.Lemon.background, 1f, 22.dp)
                        }
                        PreviewBar(SessionPalette.Lilac.background, 0.8f, 12.dp)
                    }
                    AppStyle.TODAY -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(SessionPalette.Lemon.background).padding(6.dp)) {
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(0.4f).height(4.dp).clip(CircleShape).background(SessionPalette.Lemon.foreground))
                        }
                        PreviewBar(t.surface, 1f, 14.dp)
                    }
                    AppStyle.NIGHT -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("15:34", color = t.ink, fontFamily = t.displayFont, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(Modifier.size(width = 22.dp, height = 10.dp).clip(CircleShape).background(t.accent))
                            repeat(3) { Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(t.surface)) }
                        }
                        PreviewBar(SessionPalette.Lemon.background, 1f, 14.dp)
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Each name in its own theme's display font
                Text(style.label, fontFamily = t.displayFont, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(style.description, color = current.muted, fontSize = 13.sp)
            }
            // Radio dot
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) current.accent else Color.Transparent)
                    .then(if (isSelected) Modifier else Modifier.background(current.outline.copy(alpha = 0.5f), CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (isSelected) current.onAccent else current.surface))
            }
        }
    }
}

@Composable
private fun PreviewBar(color: Color, widthFraction: Float, height: Dp) {
    Box(Modifier.fillMaxWidth(widthFraction).height(height).clip(RoundedCornerShape(5.dp)).background(color))
}
