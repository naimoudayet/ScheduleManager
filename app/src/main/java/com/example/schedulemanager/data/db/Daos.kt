package com.example.schedulemanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

private const val SESSIONS_FOR_DAY = """
    SELECT s.*, c.name AS classroom_name, sub.name AS subject_name,
           sub.background_color, sub.foreground_color
    FROM sessions s
    JOIN classrooms c ON c.id = s.classroom_id
    JOIN subjects sub ON sub.id = s.subject_id
    WHERE s.day_of_week = :day
    ORDER BY s.start_minutes
"""

@Dao
interface SessionDao {

    @Query(SESSIONS_FOR_DAY)
    fun observeDay(day: Int): Flow<List<SessionWithNames>>

    @Query(SESSIONS_FOR_DAY)
    suspend fun getDay(day: Int): List<SessionWithNames>

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

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
        GROUP BY c.id
        ORDER BY c.name COLLATE NOCASE
        """
    )
    fun observeAll(): Flow<List<NamedWithCount>>

    /** Case-insensitive lookup (the column uses NOCASE). */
    @Query("SELECT * FROM classrooms WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ClassroomEntity?

    @Query("SELECT COUNT(*) FROM sessions WHERE classroom_id = :id")
    suspend fun countSessions(id: Long): Int

    @Insert
    suspend fun insert(classroom: ClassroomEntity): Long

    @Update
    suspend fun update(classroom: ClassroomEntity)

    @Query("DELETE FROM classrooms WHERE id = :id")
    suspend fun delete(id: Long)

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
        GROUP BY sub.id
        ORDER BY sub.name COLLATE NOCASE
        """
    )
    fun observeAll(): Flow<List<NamedWithCount>>

    /** Case-insensitive lookup (the column uses NOCASE). */
    @Query("SELECT * FROM subjects WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): SubjectEntity?

    @Query("SELECT COUNT(*) FROM sessions WHERE subject_id = :id")
    suspend fun countSessions(id: Long): Int

    @Insert
    suspend fun insert(subject: SubjectEntity): Long

    @Update
    suspend fun update(subject: SubjectEntity)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM subjects")
    suspend fun deleteAll()
}
