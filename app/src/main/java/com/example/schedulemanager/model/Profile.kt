package com.example.schedulemanager.model

import androidx.compose.ui.graphics.Color

/** One person's schedule: "Me", or a child in parent mode. */
data class Profile(
    val id: Long,
    val name: String,
    val color: Color,
    val isMe: Boolean,
    val reminders: Boolean,
    /** Epoch millis of the last change to this person's schedule */
    val updatedAt: Long,
    val sessionCount: Int,
) {
    /** "Me" shows as is; others as their first letter. */
    val initials: String get() = if (isMe) "Me" else name.take(1).uppercase()
}

/** Avatar colors for people in parent mode. */
object ProfilePalette {
    val Gray = Color(0xFF6B7280)
    val all = listOf(
        Color(0xFF2D9CDB), // blue
        Color(0xFFF2994A), // orange
        Color(0xFF27AE60), // green
        Color(0xFF9B51E0), // purple
        Color(0xFFE0559B), // pink
        Color(0xFF0F9D9A), // teal
    )
}
