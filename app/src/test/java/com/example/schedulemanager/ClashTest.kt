package com.example.schedulemanager

import androidx.compose.ui.graphics.Color
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.findClash
import com.example.schedulemanager.model.time
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClashTest {

    private fun session(id: Long, start: Int, end: Int) = ClassSession(
        id = id, dayOfWeek = 0, startMinutes = start, endMinutes = end,
        classroomId = 1, classroom = "Room", subjectId = id, subject = "Subject $id",
        backgroundColor = Color.White, foregroundColor = Color.Black,
    )

    // 09:00–10:00 and 11:00–12:00
    private val day = listOf(session(1, time(9), time(10)), session(2, time(11), time(12)))

    @Test
    fun `partial overlap clashes`() {
        assertEquals(1L, findClash(day, time(9, 30), time(10, 30), ignoreId = null)?.id)
    }

    @Test
    fun `new session inside an existing one clashes`() {
        assertEquals(2L, findClash(day, time(11, 15), time(11, 45), ignoreId = null)?.id)
    }

    @Test
    fun `new session covering an existing one clashes`() {
        assertEquals(1L, findClash(day, time(8), time(10, 30), ignoreId = null)?.id)
    }

    @Test
    fun `back to back sessions do not clash`() {
        assertNull(findClash(day, time(10), time(11), ignoreId = null))
        assertNull(findClash(day, time(8), time(9), ignoreId = null))
        assertNull(findClash(day, time(12), time(13), ignoreId = null))
    }

    @Test
    fun `editing a session does not clash with itself`() {
        assertNull(findClash(day, time(9, 15), time(10), ignoreId = 1))
    }

    @Test
    fun `editing a session still clashes with others`() {
        assertEquals(2L, findClash(day, time(9), time(11, 30), ignoreId = 1)?.id)
    }

    @Test
    fun `empty day never clashes`() {
        assertNull(findClash(emptyList(), time(9), time(10), ignoreId = null))
    }
}
