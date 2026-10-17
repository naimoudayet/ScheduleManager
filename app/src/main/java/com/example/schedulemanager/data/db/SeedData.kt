package com.example.schedulemanager.data.db

import androidx.compose.ui.graphics.toArgb
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.schedulemanager.model.SessionPalette
import com.example.schedulemanager.model.TileColors
import com.example.schedulemanager.model.time

/** Sample timetable inserted once, when the database is first created. Sunday is left empty. */
object SeedData {

    private val subjectColors: Map<String, TileColors> = mapOf(
        "Mathematics" to SessionPalette.Blue,
        "Physics" to SessionPalette.Peach,
        "English Literature" to SessionPalette.Mint,
        "Computer Science" to SessionPalette.Navy,
        "Art & Design" to SessionPalette.Lilac,
        "Chemistry" to SessionPalette.Lemon,
        "History" to SessionPalette.Sand,
        "Biology" to SessionPalette.Teal,
        "Physical Education" to SessionPalette.Rose,
        "Music" to SessionPalette.Plum,
        "Study Group" to SessionPalette.Forest,
    )

    private class Seed(val day: Int, val start: Int, val end: Int, val classroom: String, val subject: String)

    private val sessions = listOf(
        // Monday
        Seed(0, time(8, 0), time(9, 30), "Room A-101", "Mathematics"),
        Seed(0, time(9, 45), time(11, 15), "Lab B-204", "Physics"),
        Seed(0, time(11, 30), time(12, 30), "Room C-012", "English Literature"),
        Seed(0, time(14, 0), time(15, 30), "Computer Lab 3", "Computer Science"),
        Seed(0, time(15, 45), time(17, 0), "Art Studio", "Art & Design"),
        // Tuesday
        Seed(1, time(8, 30), time(10, 0), "Lab B-110", "Chemistry"),
        Seed(1, time(10, 15), time(11, 45), "Room D-305", "History"),
        Seed(1, time(13, 0), time(14, 30), "Room A-101", "Mathematics"),
        Seed(1, time(14, 45), time(16, 15), "Gym Hall", "Physical Education"),
        // Wednesday
        Seed(2, time(8, 0), time(9, 0), "Lab B-215", "Biology"),
        Seed(2, time(9, 15), time(10, 45), "Computer Lab 3", "Computer Science"),
        Seed(2, time(11, 0), time(12, 30), "Room C-012", "English Literature"),
        Seed(2, time(14, 0), time(15, 0), "Music Room", "Music"),
        Seed(2, time(15, 15), time(16, 45), "Lab B-204", "Physics"),
        Seed(2, time(17, 0), time(18, 0), "Library", "Study Group"),
        // Thursday
        Seed(3, time(9, 0), time(10, 30), "Room A-101", "Mathematics"),
        Seed(3, time(10, 45), time(12, 15), "Lab B-110", "Chemistry"),
        Seed(3, time(13, 30), time(15, 0), "Art Studio", "Art & Design"),
        Seed(3, time(15, 15), time(16, 15), "Room D-305", "History"),
        // Friday
        Seed(4, time(8, 0), time(9, 30), "Lab B-204", "Physics"),
        Seed(4, time(9, 45), time(11, 0), "Lab B-215", "Biology"),
        Seed(4, time(11, 15), time(12, 45), "Computer Lab 3", "Computer Science"),
        Seed(4, time(14, 0), time(15, 30), "Gym Hall", "Physical Education"),
        Seed(4, time(15, 45), time(16, 45), "Music Room", "Music"),
        // Saturday
        Seed(5, time(9, 0), time(10, 30), "Room A-101", "Mathematics"),
        Seed(5, time(10, 45), time(12, 0), "Room C-012", "English Literature"),
        Seed(5, time(13, 0), time(14, 30), "Library", "Study Group"),
        Seed(5, time(15, 0), time(16, 30), "Lab B-110", "Chemistry"),
        Seed(5, time(17, 0), time(18, 30), "Art Studio", "Art & Design"),
        Seed(5, time(19, 0), time(20, 0), "Gym Hall", "Physical Education"),
    )

    fun insertInto(db: SupportSQLiteDatabase) {
        val classroomIds = sessions.map { it.classroom }.distinct()
            .withIndex()
            .associate { (index, name) -> name to (index + 1L) }
        classroomIds.forEach { (name, id) ->
            db.execSQL("INSERT INTO classrooms (id, name) VALUES (?, ?)", arrayOf(id, name))
        }
        val subjectIds = sessions.map { it.subject }.distinct()
            .withIndex()
            .associate { (index, name) -> name to (index + 1L) }
        subjectIds.forEach { (name, id) ->
            val colors = subjectColors.getValue(name)
            db.execSQL(
                "INSERT INTO subjects (id, name, background_color, foreground_color) VALUES (?, ?, ?, ?)",
                arrayOf(id, name, colors.background.toArgb(), colors.foreground.toArgb()),
            )
        }

        sessions.forEach { s ->
            db.execSQL(
                """
                INSERT INTO sessions (day_of_week, start_minutes, end_minutes, subject_id, classroom_id)
                VALUES (?, ?, ?, ?, ?)
                """,
                arrayOf(s.day, s.start, s.end, subjectIds.getValue(s.subject), classroomIds.getValue(s.classroom)),
            )
        }
    }
}
