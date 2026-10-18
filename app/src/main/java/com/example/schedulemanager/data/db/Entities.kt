package com.example.schedulemanager.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One person's schedule ("Me", or a child in parent mode). Classrooms, subjects and sessions all
 * belong to a profile, so each child's school data stays separate.
 */
@Entity(
    tableName = "profiles",
    indices = [Index(value = ["name"], unique = true)],
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** ARGB avatar color */
    val color: Int,
    /** The parent's own schedule (notifications don't prefix it with a name) */
    @ColumnInfo(name = "is_me") val isMe: Boolean,
    /** Session reminders for this person, in parent mode */
    val reminders: Boolean,
    /** Last change to this person's schedule, epoch millis */
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "classrooms",
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profile_id"])],
    // Unique per person; NOCASE so "Room A" and "room a" count as the same name
    indices = [Index(value = ["profile_id", "name"], unique = true)],
)
data class ClassroomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
)

@Entity(
    tableName = "subjects",
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profile_id"])],
    indices = [Index(value = ["profile_id", "name"], unique = true)],
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** ARGB colors used by this subject's sessions; defaults are SessionPalette.Blue */
    @ColumnInfo(name = "background_color", defaultValue = DEFAULT_BACKGROUND) val backgroundColor: Int,
    @ColumnInfo(name = "foreground_color", defaultValue = DEFAULT_FOREGROUND) val foregroundColor: Int,
)

// SessionPalette.Blue as signed ARGB ints (0xFFDCE8FF / 0xFF1B3A75)
const val DEFAULT_BACKGROUND = "-2299649"
const val DEFAULT_FOREGROUND = "-14992779"

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profile_id"]),
        // A classroom or subject that is still used by sessions cannot be deleted
        ForeignKey(
            entity = ClassroomEntity::class,
            parentColumns = ["id"],
            childColumns = ["classroom_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subject_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["profile_id", "day_of_week", "start_minutes"]),
        Index(value = ["classroom_id"]),
        Index(value = ["subject_id"]),
    ],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "start_minutes") val startMinutes: Int,
    @ColumnInfo(name = "end_minutes") val endMinutes: Int,
    @ColumnInfo(name = "subject_id") val subjectId: Long,
    @ColumnInfo(name = "classroom_id") val classroomId: Long,
)

/** A session row joined with its classroom name and its subject's name and colors. */
data class SessionWithNames(
    @Embedded val session: SessionEntity,
    @ColumnInfo(name = "classroom_name") val classroomName: String,
    @ColumnInfo(name = "subject_name") val subjectName: String,
    @ColumnInfo(name = "background_color") val backgroundColor: Int,
    @ColumnInfo(name = "foreground_color") val foregroundColor: Int,
)

/** A classroom or subject row with the number of sessions using it. Colors are null for classrooms. */
data class NamedWithCount(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
    @ColumnInfo(name = "background_color") val backgroundColor: Int?,
    @ColumnInfo(name = "foreground_color") val foregroundColor: Int?,
)

/** A profile with how many sessions it has. */
data class ProfileWithCount(
    @Embedded val profile: ProfileEntity,
    @ColumnInfo(name = "session_count") val sessionCount: Int,
)
