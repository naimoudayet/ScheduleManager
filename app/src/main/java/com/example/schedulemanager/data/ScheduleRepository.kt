package com.example.schedulemanager.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.room.withTransaction
import com.example.schedulemanager.data.db.AppDatabase
import com.example.schedulemanager.data.db.ClassroomEntity
import com.example.schedulemanager.data.db.NamedWithCount
import com.example.schedulemanager.data.db.SessionEntity
import com.example.schedulemanager.data.db.SessionWithNames
import com.example.schedulemanager.data.db.SubjectEntity
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.NamedItem
import com.example.schedulemanager.model.SessionDraft
import com.example.schedulemanager.model.SessionPalette
import com.example.schedulemanager.model.TileColors
import com.example.schedulemanager.model.findClash
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface SaveSessionResult {
    data object Saved : SaveSessionResult
    data class Invalid(val message: String) : SaveSessionResult
    data class Clash(val with: ClassSession) : SaveSessionResult
}

/** Result of adding or renaming a classroom / subject. */
sealed interface SaveNameResult {
    data class Saved(val id: Long) : SaveNameResult
    data object Blank : SaveNameResult
    data object Duplicate : SaveNameResult
}

/** Result of deleting a classroom / subject. */
sealed interface DeleteItemResult {
    data object Deleted : DeleteItemResult
    data class InUse(val sessionCount: Int) : DeleteItemResult
}

class ScheduleRepository(private val db: AppDatabase) {

    private val sessionDao = db.sessionDao()
    private val classroomDao = db.classroomDao()
    private val subjectDao = db.subjectDao()

    // ---- Sessions ----

    fun observeDay(day: Int): Flow<List<ClassSession>> =
        sessionDao.observeDay(day).map { rows -> rows.map { it.toModel() } }

    /**
     * Validates and saves [draft]. The clash check and the write run in one transaction,
     * so two saves can never slip an overlapping session in between each other.
     */
    suspend fun saveSession(draft: SessionDraft): SaveSessionResult {
        if (draft.endMinutes <= draft.startMinutes) {
            return SaveSessionResult.Invalid("End time must be after start time")
        }

        return db.withTransaction {
            val daySessions = sessionDao.getDay(draft.dayOfWeek).map { it.toModel() }
            val clash = findClash(daySessions, draft.startMinutes, draft.endMinutes, ignoreId = draft.id)
            if (clash != null) return@withTransaction SaveSessionResult.Clash(clash)

            val entity = SessionEntity(
                id = draft.id ?: 0,
                dayOfWeek = draft.dayOfWeek,
                startMinutes = draft.startMinutes,
                endMinutes = draft.endMinutes,
                subjectId = draft.subjectId,
                classroomId = draft.classroomId,
            )
            if (draft.id == null) sessionDao.insert(entity) else sessionDao.update(entity)
            SaveSessionResult.Saved
        }
    }

    suspend fun deleteSession(id: Long) = sessionDao.delete(id)

    // ---- Classrooms & subjects ----

    fun observeItems(kind: ItemKind): Flow<List<NamedItem>> {
        val rows = when (kind) {
            ItemKind.CLASSROOM -> classroomDao.observeAll()
            ItemKind.SUBJECT -> subjectDao.observeAll()
        }
        return rows.map { list -> list.map { it.toModel() } }
    }

    /**
     * Adds an item when [id] is null, otherwise renames it. Names are unique, ignoring case.
     * [colors] is used for subjects only (their sessions are shown in these colors).
     */
    suspend fun saveItem(kind: ItemKind, id: Long?, name: String, colors: TileColors? = null): SaveNameResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveNameResult.Blank

        return db.withTransaction {
            val existingId = when (kind) {
                ItemKind.CLASSROOM -> classroomDao.findByName(trimmed)?.id
                ItemKind.SUBJECT -> subjectDao.findByName(trimmed)?.id
            }
            if (existingId != null && existingId != id) return@withTransaction SaveNameResult.Duplicate

            val savedId = when (kind) {
                ItemKind.CLASSROOM -> {
                    val entity = ClassroomEntity(id = id ?: 0, name = trimmed)
                    if (id == null) classroomDao.insert(entity) else id.also { classroomDao.update(entity) }
                }
                ItemKind.SUBJECT -> saveSubject(id, trimmed, colors ?: SessionPalette.all.first())
            }
            SaveNameResult.Saved(savedId)
        }
    }

    private suspend fun saveSubject(id: Long?, name: String, colors: TileColors): Long {
        val entity = SubjectEntity(
            id = id ?: 0,
            name = name,
            backgroundColor = colors.background.toArgb(),
            foregroundColor = colors.foreground.toArgb(),
        )
        if (id == null) return subjectDao.insert(entity)
        subjectDao.update(entity)
        return id
    }

    /** Deletes every session, subject and classroom. Sessions go first because they reference the others. */
    suspend fun deleteEverything() = db.withTransaction {
        sessionDao.deleteAll()
        subjectDao.deleteAll()
        classroomDao.deleteAll()
    }

    /** Refuses to delete an item that sessions still use. */
    suspend fun deleteItem(kind: ItemKind, id: Long): DeleteItemResult = db.withTransaction {
        val count = when (kind) {
            ItemKind.CLASSROOM -> classroomDao.countSessions(id)
            ItemKind.SUBJECT -> subjectDao.countSessions(id)
        }
        if (count > 0) return@withTransaction DeleteItemResult.InUse(count)

        when (kind) {
            ItemKind.CLASSROOM -> classroomDao.delete(id)
            ItemKind.SUBJECT -> subjectDao.delete(id)
        }
        DeleteItemResult.Deleted
    }
}

private fun NamedWithCount.toModel() = NamedItem(
    id = id,
    name = name,
    sessionCount = sessionCount,
    colors = if (backgroundColor != null && foregroundColor != null) {
        TileColors(Color(backgroundColor), Color(foregroundColor))
    } else {
        null
    },
)

private fun SessionWithNames.toModel() = ClassSession(
    id = session.id,
    dayOfWeek = session.dayOfWeek,
    startMinutes = session.startMinutes,
    endMinutes = session.endMinutes,
    classroomId = session.classroomId,
    classroom = classroomName,
    subjectId = session.subjectId,
    subject = subjectName,
    backgroundColor = Color(backgroundColor),
    foregroundColor = Color(foregroundColor),
)
