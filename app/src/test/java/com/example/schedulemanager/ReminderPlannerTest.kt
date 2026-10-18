package com.example.schedulemanager

import androidx.compose.ui.graphics.Color
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.time
import com.example.schedulemanager.reminders.minutesUntilStart
import com.example.schedulemanager.reminders.nextReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderPlannerTest {

    private fun session(id: Long, day: Int, start: Int) = ClassSession(
        id = id, dayOfWeek = day, startMinutes = start, endMinutes = start + 60,
        classroomId = 1, classroom = "Room", subjectId = 1, subject = "Subject",
        backgroundColor = Color.White, foregroundColor = Color.Black,
    )

    private val monday9 = session(1, day = 0, start = time(9))
    private val monday14 = session(2, day = 0, start = time(14))
    private val wednesday10 = session(3, day = 2, start = time(10))
    private val week = listOf(wednesday10, monday14, monday9)

    @Test
    fun `picks the next session later today`() {
        val next = nextReminder(week, nowDay = 0, nowMinute = time(8))!!
        assertEquals(1L, next.session.id)
        assertEquals(55, next.minutesFromNow) // 08:00 -> 08:55
    }

    @Test
    fun `skips sessions whose reminder already passed today`() {
        val next = nextReminder(week, nowDay = 0, nowMinute = time(9, 30))!!
        assertEquals(2L, next.session.id)
        assertEquals(time(4, 25), next.minutesFromNow) // 09:30 -> 13:55
    }

    @Test
    fun `the reminder due this minute counts as shown`() {
        val next = nextReminder(week, nowDay = 0, nowMinute = time(8, 55))!!
        assertEquals(2L, next.session.id)
    }

    @Test
    fun `wraps from the end of the week to next Monday`() {
        val next = nextReminder(week, nowDay = 6, nowMinute = time(20))!! // Sunday 20:00
        assertEquals(1L, next.session.id)
        assertEquals(time(12, 55), next.minutesFromNow) // -> Monday 08:55
    }

    @Test
    fun `a session just after midnight is reminded the evening before`() {
        val earlyTuesday = session(9, day = 1, start = time(0, 3))
        val next = nextReminder(listOf(earlyTuesday), nowDay = 0, nowMinute = time(23, 50))!!
        assertEquals(8, next.minutesFromNow) // Monday 23:58
    }

    @Test
    fun `a single session repeats next week once its reminder has passed`() {
        val next = nextReminder(listOf(monday9), nowDay = 0, nowMinute = time(9))!!
        assertEquals(7 * 24 * 60 - 5, next.minutesFromNow)
    }

    @Test
    fun `no sessions means no reminder`() {
        assertNull(nextReminder(emptyList(), nowDay = 0, nowMinute = time(8)))
    }

    @Test
    fun `minutes until start is negative once a session has begun`() {
        assertEquals(5, minutesUntilStart(monday9, nowDay = 0, nowMinute = time(8, 55)))
        assertEquals(0, minutesUntilStart(monday9, nowDay = 0, nowMinute = time(9)))
        assertEquals(-2, minutesUntilStart(monday9, nowDay = 0, nowMinute = time(9, 2)))
    }
}
