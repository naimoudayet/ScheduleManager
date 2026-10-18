package com.example.schedulemanager.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, ClassroomEntity::class, SubjectEntity::class, SessionEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun classroomDao(): ClassroomDao
    abstract fun subjectDao(): SubjectDao
    abstract fun profileDao(): ProfileDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "schedule.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(object : Callback() {
                    // A new install starts with one person: "Me"
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "INSERT INTO profiles (id, name, color, is_me, reminders, updated_at) VALUES (1, 'Me', ?, 1, 1, ?)",
                            arrayOf<Any>(DEFAULT_PROFILE_COLOR, System.currentTimeMillis()),
                        )
                    }
                })
                .build()
    }
}
