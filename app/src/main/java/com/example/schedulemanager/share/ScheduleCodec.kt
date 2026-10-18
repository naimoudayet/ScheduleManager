package com.example.schedulemanager.share

import com.example.schedulemanager.model.MAX_NAME_LENGTH
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/** A timetable as it travels inside a QR code. Sessions point at subjects/classrooms by list index. */
data class SharedSchedule(
    val subjects: List<SharedSubject>,
    val classrooms: List<String>,
    val sessions: List<SharedSession>,
)

/** Colors are ARGB ints. */
data class SharedSubject(val name: String, val backgroundColor: Int, val foregroundColor: Int)

data class SharedSession(
    val dayOfWeek: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val subjectIndex: Int,
    val classroomIndex: Int,
)

/** A scanned code that can't be imported; [message] is shown to the user. */
class InvalidScheduleCodeException(message: String) : Exception(message)

/**
 * Turns a [SharedSchedule] into QR text and back:
 * `SCHEDULE:` + Base45( raw deflate( version byte + subjects + classrooms + sessions ) ).
 *
 * Decoding never trusts the code: anyone can make a QR code, so every value is checked.
 */
object ScheduleCodec {
    private const val PREFIX = "SCHEDULE:"
    private const val FORMAT_VERSION = 1

    const val MAX_NAMED_ITEMS = 300
    const val MAX_SESSIONS = 500
    private const val MAX_DECOMPRESSED_BYTES = 64 * 1024
    private const val LAST_MINUTE_OF_DAY = 24 * 60

    fun encode(schedule: SharedSchedule): String {
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).use { out ->
            out.writeByte(FORMAT_VERSION)
            out.writeShort(schedule.subjects.size)
            schedule.subjects.forEach {
                out.writeUTF(it.name)
                out.writeInt(it.backgroundColor)
                out.writeInt(it.foregroundColor)
            }
            out.writeShort(schedule.classrooms.size)
            schedule.classrooms.forEach(out::writeUTF)
            out.writeShort(schedule.sessions.size)
            schedule.sessions.forEach {
                out.writeByte(it.dayOfWeek)
                out.writeShort(it.startMinutes)
                out.writeShort(it.endMinutes)
                out.writeShort(it.subjectIndex)
                out.writeShort(it.classroomIndex)
            }
        }
        return PREFIX + Base45.encode(deflate(raw.toByteArray()))
    }

    /** @throws InvalidScheduleCodeException with a user-facing message */
    fun decode(text: String): SharedSchedule {
        if (!text.startsWith(PREFIX)) invalid("This QR code isn't a shared schedule")

        val bytes = try {
            inflate(Base45.decode(text.removePrefix(PREFIX)))
        } catch (_: IllegalArgumentException) {
            invalid(DAMAGED)
        } catch (_: DataFormatException) {
            invalid(DAMAGED)
        }

        val schedule = try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val version = input.readUnsignedByte()
                if (version != FORMAT_VERSION) invalid("This code was made by a newer version of the app")

                val subjects = List(readCount(input, MAX_NAMED_ITEMS)) {
                    SharedSubject(input.readUTF().trim(), input.readInt() or OPAQUE, input.readInt() or OPAQUE)
                }
                val classrooms = List(readCount(input, MAX_NAMED_ITEMS)) { input.readUTF().trim() }
                val sessions = List(readCount(input, MAX_SESSIONS)) {
                    SharedSession(
                        dayOfWeek = input.readUnsignedByte(),
                        startMinutes = input.readUnsignedShort(),
                        endMinutes = input.readUnsignedShort(),
                        subjectIndex = input.readUnsignedShort(),
                        classroomIndex = input.readUnsignedShort(),
                    )
                }
                SharedSchedule(subjects, classrooms, sessions)
            }
        } catch (_: IOException) {
            invalid(DAMAGED) // ran out of data mid-way, or a malformed name
        }

        validate(schedule)
        return schedule
    }

    private fun validate(schedule: SharedSchedule) {
        checkNames(schedule.subjects.map { it.name }, "subject")
        checkNames(schedule.classrooms, "classroom")

        schedule.sessions.forEach {
            if (it.dayOfWeek !in 0..6 ||
                it.startMinutes >= it.endMinutes || it.endMinutes > LAST_MINUTE_OF_DAY ||
                it.subjectIndex !in schedule.subjects.indices ||
                it.classroomIndex !in schedule.classrooms.indices
            ) {
                invalid(DAMAGED)
            }
        }

        // Same rule as the app: no two sessions overlap on the same day
        schedule.sessions
            .sortedWith(compareBy({ it.dayOfWeek }, { it.startMinutes }))
            .zipWithNext()
            .forEach { (a, b) ->
                if (a.dayOfWeek == b.dayOfWeek && b.startMinutes < a.endMinutes) {
                    invalid("This schedule has overlapping sessions")
                }
            }
    }

    private fun checkNames(names: List<String>, what: String) {
        names.forEach { if (it.isEmpty() || it.length > MAX_NAME_LENGTH) invalid(DAMAGED) }
        if (names.map { it.lowercase() }.toSet().size != names.size) invalid("This schedule has a duplicate $what")
    }

    private fun readCount(input: DataInputStream, max: Int): Int =
        input.readUnsignedShort().also { if (it > max) invalid("This schedule is too large to import") }

    private fun deflate(bytes: ByteArray): ByteArray {
        // nowrap = raw deflate, without the 6-byte zlib header/checksum
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    /** Stops at [MAX_DECOMPRESSED_BYTES] so a crafted code can't blow up memory. */
    private fun inflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw DataFormatException("Truncated")
                out.write(buffer, 0, n)
                if (out.size() > MAX_DECOMPRESSED_BYTES) invalid("This schedule is too large to import")
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private const val DAMAGED = "This schedule code is damaged or incomplete"
    private const val OPAQUE = 0xFF shl 24

    private fun invalid(message: String): Nothing = throw InvalidScheduleCodeException(message)
}
