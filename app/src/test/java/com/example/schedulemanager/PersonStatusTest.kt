package com.example.schedulemanager

import androidx.compose.ui.graphics.Color
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.time
import com.example.schedulemanager.ui.family.PersonStatus
import com.example.schedulemanager.ui.family.personStatus
import com.example.schedulemanager.ui.schedule.Now
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonStatusTest {

    private fun session(id: Long, subject: String, start: Int, end: Int) = ClassSession(
        id = id, dayOfWeek = 5, startMinutes = start, endMinutes = end,
        classroomId = 1, classroom = "Lab B-110", subjectId = id, subject = subject,
        backgroundColor = Color.White, foregroundColor = Color.Black,
    )

    private val today = listOf(
        session(1, "Chemistry", time(15), time(16, 30)),
        session(2, "Physics", time(17), time(18)),
    )

    @Test
    fun `in a class shows the class and when it ends`() {
        assertEquals(
            PersonStatus("Chemistry", "Lab B-110 · until 16:30"),
            personStatus(today, Now(day = 5, minutes = time(15, 34)), use24Hour = true),
        )
    }

    @Test
    fun `between classes shows what's next and when`() {
        assertEquals(
            PersonStatus("Free", "Physics at 17:00 · in 26 min"),
            personStatus(today, Now(day = 5, minutes = time(16, 34)), use24Hour = true),
        )
    }

    @Test
    fun `after the last class says so`() {
        assertEquals(PersonStatus("Done", "No more classes today"), personStatus(today, Now(5, time(19)), use24Hour = true))
    }

    @Test
    fun `no classes at all today`() {
        assertEquals(PersonStatus("Free", "No classes today"), personStatus(emptyList(), Now(5, time(10)), use24Hour = true))
    }
}
