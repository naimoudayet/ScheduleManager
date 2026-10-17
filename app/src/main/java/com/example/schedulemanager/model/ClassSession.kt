package com.example.schedulemanager.model

import androidx.compose.ui.graphics.Color
import java.util.Calendar

val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/**
 * A single class in the schedule. Its colors come from its subject.
 * [dayOfWeek]: Monday = 0 ... Sunday = 6.
 * Times are stored as minutes since midnight (e.g. 8:30 -> 510).
 */
data class ClassSession(
    val id: Long,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val classroomId: Long,
    val classroom: String,
    val subjectId: Long,
    val subject: String,
    val backgroundColor: Color,
    val foregroundColor: Color,
) {
    fun isOngoingAt(minutes: Int): Boolean = minutes in startMinutes until endMinutes
}

/** A session being created ([id] is null) or edited, before it is saved. */
data class SessionDraft(
    val id: Long?,
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val classroomId: Long,
    val subjectId: Long,
)

/** The two kinds of named things a session refers to. */
enum class ItemKind(val singular: String, val plural: String) {
    CLASSROOM("Classroom", "Classrooms"),
    SUBJECT("Subject", "Subjects"),
}

/** A classroom or subject, with how many sessions use it. [colors] is only set for subjects. */
data class NamedItem(
    val id: Long,
    val name: String,
    val sessionCount: Int,
    val colors: TileColors? = null,
)

/**
 * Returns the first session in [daySessions] that overlaps [start]..[end], ignoring the session
 * with [ignoreId] (the one being edited). Back-to-back sessions (one ends when the other starts)
 * do not clash.
 */
fun findClash(daySessions: List<ClassSession>, start: Int, end: Int, ignoreId: Long?): ClassSession? =
    daySessions.firstOrNull { it.id != ignoreId && it.startMinutes < end && it.endMinutes > start }

fun time(hour: Int, minute: Int = 0): Int = hour * 60 + minute

fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/** Today's index in the week (Monday = 0 ... Sunday = 6). */
fun currentDayIndex(): Int {
    // Calendar.DAY_OF_WEEK: Sunday = 1 ... Saturday = 7
    return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7
}

/** Minutes since midnight right now. */
fun currentMinutes(): Int {
    val now = Calendar.getInstance()
    return time(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
}
