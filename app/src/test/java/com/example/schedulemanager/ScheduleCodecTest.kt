package com.example.schedulemanager

import com.example.schedulemanager.model.time
import com.example.schedulemanager.share.Base45
import com.example.schedulemanager.share.InvalidScheduleCodeException
import com.example.schedulemanager.share.ScheduleCodec
import com.example.schedulemanager.share.SharedSchedule
import com.example.schedulemanager.share.SharedSession
import com.example.schedulemanager.share.SharedSubject
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleCodecTest {

    private val blue = 0xFFDCE8FF.toInt()
    private val navy = 0xFF1B3A75.toInt()

    /** Shaped like the app's sample data: 30 sessions, 11 subjects, 11 classrooms. */
    private val sample = run {
        val subjects = listOf(
            "Mathematics", "Physics", "English Literature", "Computer Science", "Art & Design", "Chemistry",
            "History", "Biology", "Physical Education", "Music", "Study Group",
        ).map { SharedSubject(it, blue, navy) }
        val classrooms = listOf(
            "Room A-101", "Lab B-204", "Room C-012", "Computer Lab 3", "Art Studio", "Lab B-110",
            "Room D-305", "Gym Hall", "Lab B-215", "Music Room", "Library",
        )
        // 5 sessions a day, Monday to Saturday, 1h30 each with 15 min breaks
        val sessions = (0..5).flatMap { day ->
            (0 until 5).map { slot ->
                val start = time(8) + slot * 105
                SharedSession(day, start, start + 90, (day + slot) % 11, (day * 2 + slot) % 11)
            }
        }
        SharedSchedule(subjects, classrooms, sessions)
    }

    @Test
    fun `round trip keeps everything`() {
        assertEquals(sample, ScheduleCodec.decode(ScheduleCodec.encode(sample)))
    }

    @Test
    fun `round trip keeps non-ASCII names`() {
        val schedule = SharedSchedule(
            subjects = listOf(SharedSubject("Mathématiques — 数学", blue, navy)),
            classrooms = listOf("Salle Ü-1"),
            sessions = listOf(SharedSession(0, time(8), time(9), 0, 0)),
        )
        assertEquals(schedule, ScheduleCodec.decode(ScheduleCodec.encode(schedule)))
    }

    @Test
    fun `a full week fits in a QR code that scans easily from a screen`() {
        val text = ScheduleCodec.encode(sample)
        val qr = Encoder.encode(text, ErrorCorrectionLevel.M)
        println("30 sessions -> ${text.length} chars, QR version ${qr.version.versionNumber}")
        assertTrue("QR version ${qr.version.versionNumber} is too dense", qr.version.versionNumber <= 20)
    }

    @Test
    fun `the code only uses QR alphanumeric characters`() {
        assertTrue(ScheduleCodec.encode(sample).all { it in "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:" })
    }

    @Test
    fun `base45 matches the RFC 9285 examples`() {
        assertEquals("BB8", Base45.encode("AB".toByteArray()))
        assertEquals("%69 VD92EX0", Base45.encode("Hello!!".toByteArray()))
        assertEquals("UJCLQE7W581", Base45.encode("base-45".toByteArray()))
        assertArrayEquals("ietf!".toByteArray(), Base45.decode("QED8WEX0"))
    }

    @Test
    fun `rejects other QR codes`() {
        assertMessage("This QR code isn't a shared schedule") { ScheduleCodec.decode("WIFI:T:WPA;S:Home;P:secret;;") }
        assertMessage("This QR code isn't a shared schedule") { ScheduleCodec.decode("https://example.com") }
    }

    @Test
    fun `rejects damaged codes`() {
        val text = ScheduleCodec.encode(sample)
        assertMessage(DAMAGED) { ScheduleCodec.decode(text.dropLast(10)) }
        assertMessage(DAMAGED) { ScheduleCodec.decode("SCHEDULE:not base45 ~~~") }
        assertMessage(DAMAGED) { ScheduleCodec.decode("SCHEDULE:" + Base45.encode(byteArrayOf(1, 2, 3))) }
    }

    @Test
    fun `rejects out of range values`() {
        fun withSession(session: SharedSession) = sample.copy(sessions = listOf(session))
        assertMessage(DAMAGED) { roundTrip(withSession(SharedSession(7, time(8), time(9), 0, 0))) } // no 8th day
        assertMessage(DAMAGED) { roundTrip(withSession(SharedSession(0, time(9), time(8), 0, 0))) } // ends before start
        assertMessage(DAMAGED) { roundTrip(withSession(SharedSession(0, time(8), time(9), 99, 0))) } // no such subject
        assertMessage(DAMAGED) { roundTrip(withSession(SharedSession(0, time(8), time(25), 0, 0))) } // past midnight
    }

    @Test
    fun `rejects overlapping sessions`() {
        val overlapping = sample.copy(
            sessions = listOf(SharedSession(1, time(8), time(10), 0, 0), SharedSession(1, time(9), time(11), 1, 1))
        )
        assertMessage("This schedule has overlapping sessions") { roundTrip(overlapping) }
    }

    @Test
    fun `rejects duplicate and overlong names`() {
        val duplicate = sample.copy(classrooms = listOf("Lab", "lab"), sessions = emptyList())
        assertMessage("This schedule has a duplicate classroom") { roundTrip(duplicate) }

        val longName = sample.copy(subjects = listOf(SharedSubject("x".repeat(61), blue, navy)), sessions = emptyList())
        assertMessage(DAMAGED) { roundTrip(longName) }
    }

    @Test
    fun `rejects schedules that are too large`() {
        val tooMany = sample.copy(
            sessions = List(ScheduleCodec.MAX_SESSIONS + 1) { SharedSession(it % 7, it, it + 1, 0, 0) },
        )
        assertMessage("This schedule is too large to import") { roundTrip(tooMany) }
    }

    private fun roundTrip(schedule: SharedSchedule) = ScheduleCodec.decode(ScheduleCodec.encode(schedule))

    private fun assertMessage(expected: String, block: () -> Unit) {
        val error = assertThrows(InvalidScheduleCodeException::class.java) { block() }
        assertEquals(expected, error.message)
    }

    private companion object {
        const val DAMAGED = "This schedule code is damaged or incomplete"
    }
}
