package com.example.schedulemanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.schedulemanager.model.AppStyle

/**
 * Applies one of the app's three themes ([style]) in light or dark. Material components
 * (dialogs, switches, pickers…) get a matching color scheme, type and shapes; the custom
 * layouts read [LocalAppTokens].
 */
@Composable
fun ScheduleManagerTheme(
    style: AppStyle = AppStyle.TIMELINE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = remember(style, darkTheme) { appTokens(style, darkTheme) }
    val colorScheme = remember(tokens) {
        val base = if (tokens.dark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = tokens.accent,
            onPrimary = tokens.onAccent,
            primaryContainer = tokens.surfaceVariant,
            onPrimaryContainer = tokens.ink,
            secondary = tokens.accent,
            onSecondary = tokens.onAccent,
            secondaryContainer = tokens.surfaceVariant,
            onSecondaryContainer = tokens.ink,
            tertiary = tokens.accentText,
            background = tokens.background,
            onBackground = tokens.ink,
            surface = tokens.background,
            onSurface = tokens.ink,
            surfaceVariant = tokens.surfaceVariant,
            onSurfaceVariant = tokens.muted,
            surfaceContainerLowest = tokens.surface,
            surfaceContainerLow = tokens.surface,
            surfaceContainer = tokens.surface,
            surfaceContainerHigh = tokens.surface,
            surfaceContainerHighest = tokens.surfaceVariant,
            outline = tokens.outline,
            outlineVariant = tokens.outline,
            error = tokens.danger,
            errorContainer = tokens.dangerContainer,
            onErrorContainer = tokens.onDangerContainer,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = remember(tokens) { typographyFor(tokens.displayFont, tokens.bodyFont) },
        shapes = remember(style) { shapesFor(style) },
    ) {
        CompositionLocalProvider(LocalAppTokens provides tokens, content = content)
    }
}

private fun typographyFor(display: FontFamily, body: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.with(family: FontFamily) = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.with(display),
        displayMedium = base.displayMedium.with(display),
        displaySmall = base.displaySmall.with(display),
        headlineLarge = base.headlineLarge.with(display),
        headlineMedium = base.headlineMedium.with(display),
        headlineSmall = base.headlineSmall.with(display),
        titleLarge = base.titleLarge.with(display),
        titleMedium = base.titleMedium.with(body),
        titleSmall = base.titleSmall.with(body),
        bodyLarge = base.bodyLarge.with(body),
        bodyMedium = base.bodyMedium.with(body),
        bodySmall = base.bodySmall.with(body),
        labelLarge = base.labelLarge.with(body),
        labelMedium = base.labelMedium.with(body),
        labelSmall = base.labelSmall.with(body),
    )
}

/** Night bold is the roundest, Timeline the calmest. */
private fun shapesFor(style: AppStyle): Shapes {
    val scale = when (style) {
        AppStyle.TIMELINE -> 1f
        AppStyle.TODAY -> 1.25f
        AppStyle.NIGHT -> 1.4f
    }
    return Shapes(
        extraSmall = RoundedCornerShape((6 * scale).dp),
        small = RoundedCornerShape((10 * scale).dp),
        medium = RoundedCornerShape((14 * scale).dp),
        large = RoundedCornerShape((20 * scale).dp),
        extraLarge = RoundedCornerShape((28 * scale).dp),
    )
}
