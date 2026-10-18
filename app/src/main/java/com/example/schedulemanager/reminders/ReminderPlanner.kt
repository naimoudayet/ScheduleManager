package com.example.schedulemanager.reminders

import com.example.schedulemanager.model.ClassSession

/** How long before a session starts its reminder is shown. */
const val REMINDER_LEAD_MINUTES = 5

private const val MINUTES_PER_DAY = 24 * 60
private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

data class NextReminder(val session: ClassSession, val minutesFromNow: Int)

/**
 * Finds the next reminder to show after now ([nowDay]: Monday = 0, [nowMinute]: minutes since midnight).
 * The timetable repeats every week, so this wraps from Sunday to Monday, and a session at 00:03
 * gets its reminder at 23:58 the day before. A reminder due at this very minute counts as already
 * shown, so the one after it is returned.
 */
fun nextReminder(
    sessions: List<ClassSession>,
    nowDay: Int,
    nowMinute: Int,
    leadMinutes: Int = REMINDER_LEAD_MINUTES,
): NextReminder? {
    val now = nowDay * MINUTES_PER_DAY + nowMinute
    return sessions
        .map { session ->
            val remindAt = Math.floorMod(session.weekMinute() - leadMinutes, MINUTES_PER_WEEK)
            val delta = Math.floorMod(remindAt - now, MINUTES_PER_WEEK)
            NextReminder(session, if (delta == 0) MINUTES_PER_WEEK else delta)
        }
        .minByOrNull { it.minutesFromNow }
}

/**
 * Minutes from now until [session] starts: positive if it is upcoming, zero or negative
 * if it has already started (e.g. when a reminder arrives late).
 */
fun minutesUntilStart(session: ClassSession, nowDay: Int, nowMinute: Int): Int {
    val diff = Math.floorMod(session.weekMinute() - (nowDay * MINUTES_PER_DAY + nowMinute), MINUTES_PER_WEEK)
    // Map into (-half a week, +half a week] so "started 2 minutes ago" is -2, not +10078
    return if (diff > MINUTES_PER_WEEK / 2) diff - MINUTES_PER_WEEK else diff
}

private fun ClassSession.weekMinute() = dayOfWeek * MINUTES_PER_DAY + startMinutes
