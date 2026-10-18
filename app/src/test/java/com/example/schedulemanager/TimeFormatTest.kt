package com.example.schedulemanager

import com.example.schedulemanager.model.formatTime
import com.example.schedulemanager.model.formatTimeRange
import com.example.schedulemanager.model.time
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class TimeFormatTest {

    private lateinit var originalLocale: Locale

    @Before
    fun useEnglish() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    @Test
    fun `24-hour format`() {
        assertEquals("00:00", formatTime(time(0), use24Hour = true))
        assertEquals("08:05", formatTime(time(8, 5), use24Hour = true))
        assertEquals("23:59", formatTime(time(23, 59), use24Hour = true))
    }

    @Test
    fun `12-hour format`() {
        assertEquals("12:00 AM", formatTime(time(0), use24Hour = false))
        assertEquals("8:05 AM", formatTime(time(8, 5), use24Hour = false))
        assertEquals("12:30 PM", formatTime(time(12, 30), use24Hour = false))
        assertEquals("11:59 PM", formatTime(time(23, 59), use24Hour = false))
    }

    @Test
    fun `time range`() {
        assertEquals("09:45–11:15", formatTimeRange(time(9, 45), time(11, 15), use24Hour = true))
        assertEquals("9:45 AM–11:15 AM", formatTimeRange(time(9, 45), time(11, 15), use24Hour = false))
    }
}
