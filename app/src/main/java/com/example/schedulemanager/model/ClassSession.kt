package com.example.schedulemanager.model

import androidx.compose.ui.graphics.Color
import java.text.DateFormatSymbols
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
    /** Whose schedule this session is in (parent mode) */
    val profileId: Long = 0,
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

/** Longest classroom / subject name allowed (also enforced when importing a shared schedule). */
const val MAX_NAME_LENGTH = 60

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

/** Latest end time the editor allows (23:59). */
const val LAST_END_MINUTE = 23 * 60 + 59

/**
 * The earliest start, at or after [from], where a session of [length] minutes fits on that day
 * without clashing (ignoring the session with [ignoreId]) and ends by 23:59. Null if nothing fits.
 * Used to offer a one-tap fix when the chosen time overlaps another session.
 */
fun suggestFreeSlot(daySessions: List<ClassSession>, length: Int, from: Int, ignoreId: Long?): Int? {
    if (length <= 0) return null
    val others = daySessions.filter { it.id != ignoreId }
    // A free slot can only start at the requested time or right after another session ends
    val candidates = (listOf(from) + others.map { it.endMinutes }.filter { it >= from }).distinct().sorted()
    return candidates.firstOrNull { start ->
        start + length <= LAST_END_MINUTE && findClash(others, start, start + length, ignoreId = null) == null
    }
}

fun time(hour: Int, minute: Int = 0): Int = hour * 60 + minute

/** "14:05" in 24-hour format, "2:05 PM" in 12-hour format (AM/PM in the device's language). */
fun formatTime(minutes: Int, use24Hour: Boolean): String {
    val hour = minutes / 60
    val minute = minutes % 60
    if (use24Hour) return "%02d:%02d".format(hour, minute)
    val amPm = DateFormatSymbols.getInstance().amPmStrings[if (hour < 12) 0 else 1]
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    return "%d:%02d %s".format(hour12, minute, amPm)
}

/** "56 min", "1 h", "1 h 30 min". */
fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

fun formatTimeRange(start: Int, end: Int, use24Hour: Boolean): String =
    "${formatTime(start, use24Hour)}–${formatTime(end, use24Hour)}"

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
