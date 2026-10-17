package com.example.schedulemanager.model

import androidx.compose.ui.graphics.Color

/** A background / foreground color pair for a session tile. */
data class TileColors(val background: Color, val foreground: Color)

/** Preset color pairs offered in the session editor. Each pair is chosen to stay readable. */
object SessionPalette {
    val Blue = TileColors(Color(0xFFDCE8FF), Color(0xFF1B3A75))
    val Peach = TileColors(Color(0xFFFFE3D3), Color(0xFF7A2E0B))
    val Mint = TileColors(Color(0xFFE2F5E1), Color(0xFF1E5A27))
    val Navy = TileColors(Color(0xFF2B2D42), Color(0xFFEDF2F4))
    val Lilac = TileColors(Color(0xFFF3E1FF), Color(0xFF5B1A85))
    val Lemon = TileColors(Color(0xFFFFF4C2), Color(0xFF6B5200))
    val Sand = TileColors(Color(0xFFF5E6DA), Color(0xFF5C3B23))
    val Teal = TileColors(Color(0xFFD4F4EF), Color(0xFF0F5A50))
    val Rose = TileColors(Color(0xFFFFDDE4), Color(0xFF8A1C38))
    val Plum = TileColors(Color(0xFF3D2C5E), Color(0xFFF1E9FF))
    val Forest = TileColors(Color(0xFF0B6E4F), Color(0xFFE6FFF5))
    val Coral = TileColors(Color(0xFFE85D4A), Color(0xFFFFF3F0))

    val all = listOf(Blue, Peach, Mint, Navy, Lilac, Lemon, Sand, Teal, Rose, Plum, Forest, Coral)
}
