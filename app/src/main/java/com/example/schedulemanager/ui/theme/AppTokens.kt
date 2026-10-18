package com.example.schedulemanager.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.example.schedulemanager.model.AppStyle

/**
 * Everything a theme decides beyond Material's color scheme: its fonts and the handful of
 * colors the custom layouts use. Read it with `LocalAppTokens.current`.
 */
@Immutable
data class AppTokens(
    val style: AppStyle,
    val dark: Boolean,
    /** Screen background */
    val background: Color,
    /** Cards and sheets */
    val surface: Color,
    /** Unselected chips, tracks, quiet buttons */
    val surfaceVariant: Color,
    val ink: Color,
    val muted: Color,
    val outline: Color,
    /** Filled buttons, selected chips */
    val accent: Color,
    val onAccent: Color,
    /** Accent used as text on [background] (yellow fills need a darker text color in light mode) */
    val accentText: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    /** The "now" line and live markers */
    val live: Color,
    val displayFont: FontFamily,
    val bodyFont: FontFamily,
)

val LocalAppTokens = staticCompositionLocalOf { appTokens(AppStyle.TIMELINE, dark = false) }

fun appTokens(style: AppStyle, dark: Boolean): AppTokens = when (style) {
    // A · Timeline: calm, light-first, navy accent
    AppStyle.TIMELINE -> if (!dark) {
        AppTokens(
            style, dark,
            background = Color(0xFFF7F8FC), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEEF0F6),
            ink = Color(0xFF141826), muted = Color(0xFF5D6478), outline = Color(0xFFD9DDEA),
            accent = Color(0xFF1B3A75), onAccent = Color(0xFFFFFFFF), accentText = Color(0xFF1B3A75),
            danger = Color(0xFFC2342A), dangerContainer = Color(0xFFFDE9E7), onDangerContainer = Color(0xFF8C1D14),
            live = Color(0xFFD93A2B),
            displayFont = PlusJakartaSans, bodyFont = PlusJakartaSans,
        )
    } else {
        AppTokens(
            style, dark,
            background = Color(0xFF0F1320), surface = Color(0xFF1A2030), surfaceVariant = Color(0xFF242B3D),
            ink = Color(0xFFE8EBF4), muted = Color(0xFF98A0B6), outline = Color(0xFF2E3649),
            accent = Color(0xFFA9C2FF), onAccent = Color(0xFF0F1A35), accentText = Color(0xFFA9C2FF),
            danger = Color(0xFFFF8A7E), dangerContainer = Color(0xFF3A1714), onDangerContainer = Color(0xFFFFD2CC),
            live = Color(0xFFFF6B5E),
            displayFont = PlusJakartaSans, bodyFont = PlusJakartaSans,
        )
    }

    // B · Today first: bright cards, indigo accent, expressive display face
    AppStyle.TODAY -> if (!dark) {
        AppTokens(
            style, dark,
            background = Color(0xFFF2F4F8), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE6E9F0),
            ink = Color(0xFF10131C), muted = Color(0xFF5A6072), outline = Color(0xFFD5D9E3),
            accent = Color(0xFF2F3A8F), onAccent = Color(0xFFFFFFFF), accentText = Color(0xFF2F3A8F),
            danger = Color(0xFFD63B2F), dangerContainer = Color(0xFFFDE9E7), onDangerContainer = Color(0xFF8C1D14),
            live = Color(0xFFD63B2F),
            displayFont = BricolageGrotesque, bodyFont = Figtree,
        )
    } else {
        AppTokens(
            style, dark,
            background = Color(0xFF0D0F15), surface = Color(0xFF1A1D27), surfaceVariant = Color(0xFF262A36),
            ink = Color(0xFFECEEF5), muted = Color(0xFF9298AB), outline = Color(0xFF30354A),
            accent = Color(0xFF9EA8FF), onAccent = Color(0xFF141A4D), accentText = Color(0xFF9EA8FF),
            danger = Color(0xFFFF8A7E), dangerContainer = Color(0xFF3A1714), onDangerContainer = Color(0xFFFFD2CC),
            live = Color(0xFFFF6B5E),
            displayFont = BricolageGrotesque, bodyFont = Figtree,
        )
    }

    // C · Night bold: dark-first, yellow highlight, wide display face for the clock
    AppStyle.NIGHT -> if (dark) {
        AppTokens(
            style, dark,
            background = Color(0xFF0E1017), surface = Color(0xFF191C26), surfaceVariant = Color(0xFF262A36),
            ink = Color(0xFFEEF0F6), muted = Color(0xFF8C93A8), outline = Color(0xFF2C3140),
            accent = Color(0xFFFFE45C), onAccent = Color(0xFF0E1017), accentText = Color(0xFFFFE45C),
            danger = Color(0xFFFF8A7E), dangerContainer = Color(0xFF3A1714), onDangerContainer = Color(0xFFFFD2CC),
            live = Color(0xFFFFE45C),
            displayFont = Unbounded, bodyFont = Manrope,
        )
    } else {
        AppTokens(
            style, dark,
            background = Color(0xFFF3F4F7), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE4E6EC),
            ink = Color(0xFF0E1017), muted = Color(0xFF5C6275), outline = Color(0xFFD3D6DF),
            // Yellow stays the highlight fill; text that needs the accent uses a dark ochre for contrast
            accent = Color(0xFFFFE45C), onAccent = Color(0xFF0E1017), accentText = Color(0xFF7A6000),
            danger = Color(0xFFC2342A), dangerContainer = Color(0xFFFDE9E7), onDangerContainer = Color(0xFF8C1D14),
            live = Color(0xFF0E1017),
            displayFont = Unbounded, bodyFont = Manrope,
        )
    }
}
