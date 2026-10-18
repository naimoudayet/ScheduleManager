package com.example.schedulemanager.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.room.withTransaction
import com.example.schedulemanager.data.db.AppDatabase
import com.example.schedulemanager.data.db.ClassroomEntity
import com.example.schedulemanager.data.db.DEFAULT_PROFILE_COLOR
import com.example.schedulemanager.data.db.NamedWithCount
import com.example.schedulemanager.data.db.ProfileEntity
import com.example.schedulemanager.data.db.ProfileWithCount
import com.example.schedulemanager.data.db.SessionEntity
import com.example.schedulemanager.data.db.SessionWithNames
import com.example.schedulemanager.data.db.SubjectEntity
import com.example.schedulemanager.model.ClassSession
import com.example.schedulemanager.model.ItemKind
import com.example.schedulemanager.model.NamedItem
import com.example.schedulemanager.model.Profile
import com.example.schedulemanager.model.SessionDraft
import com.example.schedulemanager.model.SessionPalette
import com.example.schedulemanager.model.TileColors
import com.example.schedulemanager.model.findClash
import com.example.schedulemanager.share.SharedSchedule
import com.example.schedulemanager.share.SharedSession
import com.example.schedulemanager.share.SharedSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

sealed interface SaveSessionResult {
    data object Saved : SaveSessionResult
    data class Invalid(val message: String) : SaveSessionResult
    data class Clash(val with: ClassSession) : SaveSessionResult
}

/** Result of adding or renaming a classroom, subject or person. */
sealed interface SaveNameResult {
    data class Saved(val id: Long) : SaveNameResult
    data object Blank : SaveNameResult
    data object Duplicate : SaveNameResult
}

/** How many shared sessions were imported, and how many were skipped because they clash. */
data class ImportResult(val added: Int, val skipped: Int)

/** Result of deleting a classroom / subject. */
sealed interface DeleteItemResult {
    data object Deleted : DeleteItemResult
    data class InUse(val sessionCount: Int) : DeleteItemResult
}

/**
 * All schedule data. Sessions, subjects and classrooms belong to a person (profile); everything
 * here works on the active person unless a profile id is passed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleRepository(private val db: AppDatabase, private val settings: SettingsRepository) {

    private val sessionDao = db.sessionDao()
    private val classroomDao = db.classroomDao()
    private val subjectDao = db.subjectDao()
    private val profileDao = db.profileDao()

    // ---- People ----

    val profiles: Flow<List<Profile>> = profileDao.observeAll().map { rows -> rows.map { it.toModel() } }

    /** Whose schedule is showing: the chosen one, or the first person if none (or a removed one) is chosen. */
    val activeProfileId: Flow<Long> = combine(settings.activeProfileId, profiles) { chosen, people ->
        people.firstOrNull { it.id == chosen }?.id ?: people.firstOrNull()?.id ?: 1L
    }.distinctUntilChanged()

    private suspend fun currentProfileId(): Long = activeProfileId.first()

    suspend fun setActiveProfile(id: Long) = settings.setActiveProfileId(id)

    /** Adds a person (a child, unless [isMe]). Names are unique, ignoring case. */
    suspend fun addProfile(name: String, color: Color, isMe: Boolean = false): SaveNameResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveNameResult.Blank
        return db.withTransaction {
            if (profileDao.findByName(trimmed) != null) return@withTransaction SaveNameResult.Duplicate
            val id = profileDao.insert(
                // New children start with reminders off; the parent turns them on per child
                ProfileEntity(name = trimmed, color = color.toArgb(), isMe = isMe, reminders = isMe, updatedAt = now())
            )
            SaveNameResult.Saved(id)
        }
    }

    /** Renames and/or recolors a person. */
    suspend fun updateProfile(id: Long, name: String, color: Color, isMe: Boolean? = null): SaveNameResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveNameResult.Blank
        return db.withTransaction {
            val existing = profileDao.get(id) ?: return@withTransaction SaveNameResult.Blank
            val other = profileDao.findByName(trimmed)
            if (other != null && other.id != id) return@withTransaction SaveNameResult.Duplicate
            profileDao.update(existing.copy(name = trimmed, color = color.toArgb(), isMe = isMe ?: existing.isMe))
            SaveNameResult.Saved(id)
        }
    }

    suspend fun setProfileReminders(id: Long, on: Boolean) = db.withTransaction {
        profileDao.get(id)?.let { profileDao.update(it.copy(reminders = on)) }
    }

    /** Removes a person and their whole schedule. The last person can't be removed. */
    suspend fun deleteProfile(id: Long): Boolean = db.withTransaction {
        if (profileDao.count() <= 1) return@withTransaction false
        sessionDao.deleteProfile(id)
        subjectDao.deleteProfile(id)
        classroomDao.deleteProfile(id)
        profileDao.delete(id)
        true
    }

    // ---- Sessions ----

    /** The active person's whole week; emits again whenever it changes or the person switches. */
    fun observeWeek(): Flow<List<ClassSession>> =
        activeProfileId.flatMapLatest { id -> sessionDao.observeProfile(id) }.map { rows -> rows.map { it.toModel() } }

    /** Everyone's sessions (each tagged with its profile id). */
    fun observeEveryone(): Flow<List<ClassSession>> =
        sessionDao.observeEveryone().map { rows -> rows.map { it.toModel() } }

    suspend fun getEveryone(): List<ClassSession> = sessionDao.getEveryone().map { it.toModel() }

    /**
     * Validates and saves [draft] in the active person's schedule. The clash check and the write run
     * in one transaction, so two saves can never slip an overlapping session in between each other.
     */
    suspend fun saveSession(draft: SessionDraft): SaveSessionResult {
        if (draft.endMinutes <= draft.startMinutes) {
            return SaveSessionResult.Invalid("End time must be after start time")
        }
        val profileId = currentProfileId()
        return db.withTransaction {
            val daySessions = sessionDao.getDay(profileId, draft.dayOfWeek).map { it.toModel() }
            val clash = findClash(daySessions, draft.startMinutes, draft.endMinutes, ignoreId = draft.id)
            if (clash != null) return@withTransaction SaveSessionResult.Clash(clash)

            val entity = SessionEntity(
                id = draft.id ?: 0,
                profileId = profileId,
                dayOfWeek = draft.dayOfWeek,
                startMinutes = draft.startMinutes,
                endMinutes = draft.endMinutes,
                subjectId = draft.subjectId,
                classroomId = draft.classroomId,
            )
            if (draft.id == null) sessionDao.insert(entity) else sessionDao.update(entity)
            profileDao.touch(profileId, now())
            SaveSessionResult.Saved
        }
    }

    suspend fun deleteSession(id: Long) {
        val profileId = currentProfileId()
        db.withTransaction {
            sessionDao.delete(id)
            profileDao.touch(profileId, now())
        }
    }

    // ---- Classrooms & subjects (the active person's) ----

    fun observeItems(kind: ItemKind): Flow<List<NamedItem>> = activeProfileId.flatMapLatest { id ->
        when (kind) {
            ItemKind.CLASSROOM -> classroomDao.observeAll(id)
            ItemKind.SUBJECT -> subjectDao.observeAll(id)
        }
    }.map { list -> list.map { it.toModel() } }

    /**
     * Adds an item when [id] is null, otherwise renames it. Names are unique per person, ignoring case.
     * [colors] is used for subjects only (their sessions are shown in these colors).
     */
    suspend fun saveItem(kind: ItemKind, id: Long?, name: String, colors: TileColors? = null): SaveNameResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return SaveNameResult.Blank
        val profileId = currentProfileId()

        return db.withTransaction {
            val existingId = when (kind) {
                ItemKind.CLASSROOM -> classroomDao.findByName(profileId, trimmed)?.id
                ItemKind.SUBJECT -> subjectDao.findByName(profileId, trimmed)?.id
            }
            if (existingId != null && existingId != id) return@withTransaction SaveNameResult.Duplicate

            val savedId = when (kind) {
                ItemKind.CLASSROOM -> {
                    val entity = ClassroomEntity(id = id ?: 0, profileId = profileId, name = trimmed)
                    if (id == null) classroomDao.insert(entity) else id.also { classroomDao.update(entity) }
                }
                ItemKind.SUBJECT -> {
                    val c = colors ?: SessionPalette.all.first()
                    val entity = SubjectEntity(
                        id = id ?: 0,
                        profileId = profileId,
                        name = trimmed,
                        backgroundColor = c.background.toArgb(),
                        foregroundColor = c.foreground.toArgb(),
                    )
                    if (id == null) subjectDao.insert(entity) else id.also { subjectDao.update(entity) }
                }
            }
            profileDao.touch(profileId, now())
            SaveNameResult.Saved(savedId)
        }
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

    // ---- Sharing ----

    /** The active person's timetable (including unused subjects and classrooms) in shareable form. */
    suspend fun exportSchedule(): SharedSchedule {
        val profileId = currentProfileId()
        return db.withTransaction {
            val subjects = subjectDao.getAll(profileId)
            val classrooms = classroomDao.getAll(profileId)
            val subjectIndex = subjects.withIndex().associate { (i, s) -> s.id to i }
            val classroomIndex = classrooms.withIndex().associate { (i, c) -> c.id to i }
            SharedSchedule(
                subjects = subjects.map { SharedSubject(it.name, it.backgroundColor, it.foregroundColor) },
                classrooms = classrooms.map { it.name },
                sessions = sessionDao.getProfile(profileId).map {
                    SharedSession(
                        dayOfWeek = it.session.dayOfWeek,
                        startMinutes = it.session.startMinutes,
                        endMinutes = it.session.endMinutes,
                        subjectIndex = subjectIndex.getValue(it.session.subjectId),
                        classroomIndex = classroomIndex.getValue(it.session.classroomId),
                    )
                },
            )
        }
    }

    /** Imports into the active person's schedule. */
    suspend fun importSchedule(shared: SharedSchedule, replace: Boolean): ImportResult =
        importInto(currentProfileId(), shared, replace)

    /**
     * Imports a scanned schedule into [profileId] in one transaction.
     * [replace] = true wipes that person's timetable first. Otherwise it is merged in: subjects and
     * classrooms are matched by name (existing ones keep their colors) and sessions that would
     * clash with existing ones are skipped.
     */
    suspend fun importInto(profileId: Long, shared: SharedSchedule, replace: Boolean): ImportResult = db.withTransaction {
        if (replace) {
            sessionDao.deleteProfile(profileId)
            subjectDao.deleteProfile(profileId)
            classroomDao.deleteProfile(profileId)
        }

        val subjectIds = shared.subjects.map { s ->
            subjectDao.findByName(profileId, s.name)?.id
                ?: subjectDao.insert(
                    SubjectEntity(profileId = profileId, name = s.name, backgroundColor = s.backgroundColor, foregroundColor = s.foregroundColor)
                )
        }
        val classroomIds = shared.classrooms.map { name ->
            classroomDao.findByName(profileId, name)?.id ?: classroomDao.insert(ClassroomEntity(profileId = profileId, name = name))
        }

        var added = 0
        var skipped = 0
        shared.sessions.forEach { s ->
            val day = sessionDao.getDay(profileId, s.dayOfWeek).map { it.toModel() }
            if (findClash(day, s.startMinutes, s.endMinutes, ignoreId = null) != null) {
                skipped++
            } else {
                sessionDao.insert(
                    SessionEntity(
                        profileId = profileId,
                        dayOfWeek = s.dayOfWeek,
                        startMinutes = s.startMinutes,
                        endMinutes = s.endMinutes,
                        subjectId = subjectIds[s.subjectIndex],
                        classroomId = classroomIds[s.classroomIndex],
                    )
                )
                added++
            }
        }
        profileDao.touch(profileId, now())
        ImportResult(added, skipped)
    }

    /** Adds a child and imports their scanned schedule, all or nothing. Returns the new person's id. */
    suspend fun importAsNewProfile(shared: SharedSchedule, name: String, color: Color): SaveNameResult =
        db.withTransaction {
            val result = addProfile(name, color)
            if (result is SaveNameResult.Saved) importInto(result.id, shared, replace = false)
            result
        }

    // ---- Parent mode setup & reset ----

    /**
     * First time parent mode is turned on, with only one person so far: label the existing schedule
     * as the parent's own ([childName] null) or as a child's.
     */
    suspend fun labelFirstProfile(childName: String?, childColor: Color): SaveNameResult {
        val id = currentProfileId()
        return if (childName == null) {
            updateProfile(id, "Me", Color(DEFAULT_PROFILE_COLOR), isMe = true)
        } else {
            updateProfile(id, childName, childColor, isMe = false).also { result ->
                // A child's schedule starts without reminders for the parent
                if (result is SaveNameResult.Saved) setProfileReminders(id, false)
            }
        }
    }

    /** Deletes everyone's sessions, subjects and classrooms and starts over with just "Me". */
    suspend fun deleteEverything() {
        val meId = db.withTransaction {
            sessionDao.deleteAll()
            subjectDao.deleteAll()
            classroomDao.deleteAll()
            profileDao.deleteAll()
            profileDao.insert(ProfileEntity(name = "Me", color = DEFAULT_PROFILE_COLOR, isMe = true, reminders = true, updatedAt = now()))
        }
        settings.setActiveProfileId(meId)
        settings.setParentMode(false)
    }

    private fun now() = System.currentTimeMillis()
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

private fun ProfileWithCount.toModel() = Profile(
    id = profile.id,
    name = profile.name,
    color = Color(profile.color),
    isMe = profile.isMe,
    reminders = profile.reminders,
    updatedAt = profile.updatedAt,
    sessionCount = sessionCount,
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
    profileId = session.profileId,
)
