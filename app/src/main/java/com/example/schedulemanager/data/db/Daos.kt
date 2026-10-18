package com.example.schedulemanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val SESSION_COLUMNS = """
    SELECT s.*, c.name AS classroom_name, sub.name AS subject_name,
           sub.background_color, sub.foreground_color
    FROM sessions s
    JOIN classrooms c ON c.id = s.classroom_id
    JOIN subjects sub ON sub.id = s.subject_id
"""

@Dao
interface SessionDao {

    @Query("$SESSION_COLUMNS WHERE s.profile_id = :profileId AND s.day_of_week = :day ORDER BY s.start_minutes")
    suspend fun getDay(profileId: Long, day: Int): List<SessionWithNames>

    @Query("$SESSION_COLUMNS WHERE s.profile_id = :profileId ORDER BY s.day_of_week, s.start_minutes")
    fun observeProfile(profileId: Long): Flow<List<SessionWithNames>>

    @Query("$SESSION_COLUMNS WHERE s.profile_id = :profileId ORDER BY s.day_of_week, s.start_minutes")
    suspend fun getProfile(profileId: Long): List<SessionWithNames>

    /** Every person's sessions (the "Right now" strip and reminders). */
    @Query("$SESSION_COLUMNS ORDER BY s.day_of_week, s.start_minutes")
    fun observeEveryone(): Flow<List<SessionWithNames>>

    @Query("$SESSION_COLUMNS ORDER BY s.day_of_week, s.start_minutes")
    suspend fun getEveryone(): List<SessionWithNames>

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sessions WHERE profile_id = :profileId")
    suspend fun deleteProfile(profileId: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}

@Dao
interface ClassroomDao {

    @Query(
        """
        SELECT c.id, c.name, COUNT(s.id) AS session_count,
               NULL AS background_color, NULL AS foreground_color
        FROM classrooms c LEFT JOIN sessions s ON s.classroom_id = c.id
        WHERE c.profile_id = :profileId
        GROUP BY c.id
        ORDER BY c.name COLLATE NOCASE
        """
    )
    fun observeAll(profileId: Long): Flow<List<NamedWithCount>>

    /** Case-insensitive lookup (the column uses NOCASE). */
    @Query("SELECT * FROM classrooms WHERE profile_id = :profileId AND name = :name LIMIT 1")
    suspend fun findByName(profileId: Long, name: String): ClassroomEntity?

    @Query("SELECT * FROM classrooms WHERE profile_id = :profileId ORDER BY name COLLATE NOCASE")
    suspend fun getAll(profileId: Long): List<ClassroomEntity>

    @Query("SELECT COUNT(*) FROM sessions WHERE classroom_id = :id")
    suspend fun countSessions(id: Long): Int

    @Insert
    suspend fun insert(classroom: ClassroomEntity): Long

    @Update
    suspend fun update(classroom: ClassroomEntity)

    @Query("DELETE FROM classrooms WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM classrooms WHERE profile_id = :profileId")
    suspend fun deleteProfile(profileId: Long)

    @Query("DELETE FROM classrooms")
    suspend fun deleteAll()
}

@Dao
interface SubjectDao {

    @Query(
        """
        SELECT sub.id, sub.name, COUNT(s.id) AS session_count,
               sub.background_color, sub.foreground_color
        FROM subjects sub LEFT JOIN sessions s ON s.subject_id = sub.id
        WHERE sub.profile_id = :profileId
        GROUP BY sub.id
        ORDER BY sub.name COLLATE NOCASE
        """
    )
    fun observeAll(profileId: Long): Flow<List<NamedWithCount>>

    /** Case-insensitive lookup (the column uses NOCASE). */
    @Query("SELECT * FROM subjects WHERE profile_id = :profileId AND name = :name LIMIT 1")
    suspend fun findByName(profileId: Long, name: String): SubjectEntity?

    @Query("SELECT * FROM subjects WHERE profile_id = :profileId ORDER BY name COLLATE NOCASE")
    suspend fun getAll(profileId: Long): List<SubjectEntity>

    @Query("SELECT COUNT(*) FROM sessions WHERE subject_id = :id")
    suspend fun countSessions(id: Long): Int

    @Insert
    suspend fun insert(subject: SubjectEntity): Long

    @Update
    suspend fun update(subject: SubjectEntity)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM subjects WHERE profile_id = :profileId")
    suspend fun deleteProfile(profileId: Long)

    @Query("DELETE FROM subjects")
    suspend fun deleteAll()
}

@Dao
interface ProfileDao {

    @Query(
        """
        SELECT p.*, (SELECT COUNT(*) FROM sessions s WHERE s.profile_id = p.id) AS session_count
        FROM profiles p
        ORDER BY p.is_me DESC, p.id
        """
    )
    fun observeAll(): Flow<List<ProfileWithCount>>

    @Query("SELECT * FROM profiles ORDER BY is_me DESC, id")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    /** Case-insensitive lookup (the column uses NOCASE). */
    @Query("SELECT * FROM profiles WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("UPDATE profiles SET updated_at = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}
