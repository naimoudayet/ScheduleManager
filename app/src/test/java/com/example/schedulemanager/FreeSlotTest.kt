package com.example.schedulemanager

import androidx.compose.ui.graphics.Color
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.suggestFreeSlot
import com.example.schedulemanager.model.time
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreeSlotTest {

    private fun session(id: Long, start: Int, end: Int) = ClassSession(
        id = id, dayOfWeek = 5, startMinutes = start, endMinutes = end,
        classroomId = 1, classroom = "Room", subjectId = 1, subject = "Subject $id",
        backgroundColor = Color.White, foregroundColor = Color.Black,
    )

    // 13:00–14:30, 15:00–16:30, 17:00–18:30
    private val day = listOf(session(1, time(13), time(14, 30)), session(2, time(15), time(16, 30)), session(3, time(17), time(18, 30)))

    @Test
    fun `keeps the requested time when it is free`() {
        assertEquals(time(10), suggestFreeSlot(day, 60, time(10), ignoreId = null))
    }

    @Test
    fun `moves to right after the session it overlaps`() {
        // 16:00–17:00 overlaps Chemistry (15:00–16:30); 16:30–17:30 would overlap 17:00, so the next gap is 18:30
        assertEquals(time(18, 30), suggestFreeSlot(day, 60, time(16), ignoreId = null))
        // A 30-minute session fits in the 16:30–17:00 gap
        assertEquals(time(16, 30), suggestFreeSlot(day, 30, time(16), ignoreId = null))
    }

    @Test
    fun `ignores the session being edited`() {
        assertEquals(time(15, 30), suggestFreeSlot(day, 60, time(15, 30), ignoreId = 2))
    }

    @Test
    fun `returns null when nothing fits before midnight`() {
        assertNull(suggestFreeSlot(day, 6 * 60, time(16), ignoreId = null))
    }
}
