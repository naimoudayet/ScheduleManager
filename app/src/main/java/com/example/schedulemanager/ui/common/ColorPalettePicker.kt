package com.example.schedulemanager.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.schedulemanager.model.SessionPalette
import com.example.schedulemanager.model.TileColors

/** The preset color pairs as tappable swatches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPalettePicker(
    selected: TileColors,
    onSelect: (TileColors) -> Unit,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 44.dp,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SessionPalette.all.forEach { pair ->
            ColorSwatch(pair, selected = pair == selected, size = swatchSize) { onSelect(pair) }
        }
    }
}

/** A circle filled with the background color, showing "Aa" in the foreground color. */
@Composable
private fun ColorSwatch(colors: TileColors, selected: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), CircleShape)
                else Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
            )
            .padding(if (selected) 5.dp else 0.dp)
            .clip(CircleShape)
            .background(colors.background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = colors.foreground)
        } else {
            Text("Aa", color = colors.foreground, style = MaterialTheme.typography.labelMedium)
        }
    }
}
