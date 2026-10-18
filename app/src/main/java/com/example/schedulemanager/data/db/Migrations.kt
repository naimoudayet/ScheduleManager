package com.example.schedulemanager.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: subjects move from a text column on `sessions` into their own `subjects` table.
 * Every distinct subject name becomes a subject row, and each session is linked to it,
 * so no existing session is lost.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `subjects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL COLLATE NOCASE)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subjects_name` ON `subjects` (`name`)")

        // OR IGNORE: names differing only in case ("math" / "Math") collapse into one subject
        db.execSQL("INSERT OR IGNORE INTO subjects (name) SELECT DISTINCT TRIM(subject) FROM sessions")

        // SQLite can't add a foreign key to an existing table, so rebuild `sessions`
        db.execSQL(
            "CREATE TABLE `sessions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`day_of_week` INTEGER NOT NULL, `start_minutes` INTEGER NOT NULL, `end_minutes` INTEGER NOT NULL, " +
                "`subject_id` INTEGER NOT NULL, `classroom_id` INTEGER NOT NULL, " +
                "`background_color` INTEGER NOT NULL, `foreground_color` INTEGER NOT NULL, " +
                "FOREIGN KEY(`classroom_id`) REFERENCES `classrooms`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`subject_id`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        db.execSQL(
            """
            INSERT INTO sessions_new (id, day_of_week, start_minutes, end_minutes, subject_id, classroom_id,
                                      background_color, foreground_color)
            SELECT s.id, s.day_of_week, s.start_minutes, s.end_minutes,
                   (SELECT sub.id FROM subjects sub WHERE sub.name = TRIM(s.subject)),
                   s.classroom_id, s.background_color, s.foreground_color
            FROM sessions s
            """
        )
        db.execSQL("DROP TABLE sessions")
        db.execSQL("ALTER TABLE sessions_new RENAME TO sessions")

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sessions_day_of_week_start_minutes` " +
                "ON `sessions` (`day_of_week`, `start_minutes`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_classroom_id` ON `sessions` (`classroom_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_subject_id` ON `sessions` (`subject_id`)")
    }
}

/**
 * v2 -> v3: subjects get their own colors. Each existing subject takes the colors of its
 * first session, so nothing changes visually; subjects without sessions get the default blue.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `subjects` ADD COLUMN `background_color` INTEGER NOT NULL DEFAULT $DEFAULT_BACKGROUND"
        )
        db.execSQL(
            "ALTER TABLE `subjects` ADD COLUMN `foreground_color` INTEGER NOT NULL DEFAULT $DEFAULT_FOREGROUND"
        )
        db.execSQL(
            """
            UPDATE subjects SET
                background_color = (SELECT s.background_color FROM sessions s
                                    WHERE s.subject_id = subjects.id ORDER BY s.id LIMIT 1),
                foreground_color = (SELECT s.foreground_color FROM sessions s
                                    WHERE s.subject_id = subjects.id ORDER BY s.id LIMIT 1)
            WHERE EXISTS (SELECT 1 FROM sessions s WHERE s.subject_id = subjects.id)
            """
        )
    }
}

/**
 * v3 -> v4: sessions no longer store colors; they always use their subject's colors.
 * Older SQLite versions (Android < 11) can't drop columns, so `sessions` is rebuilt.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `sessions_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`day_of_week` INTEGER NOT NULL, `start_minutes` INTEGER NOT NULL, `end_minutes` INTEGER NOT NULL, " +
                "`subject_id` INTEGER NOT NULL, `classroom_id` INTEGER NOT NULL, " +
                "FOREIGN KEY(`classroom_id`) REFERENCES `classrooms`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`subject_id`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        db.execSQL(
            """
            INSERT INTO sessions_new (id, day_of_week, start_minutes, end_minutes, subject_id, classroom_id)
            SELECT id, day_of_week, start_minutes, end_minutes, subject_id, classroom_id FROM sessions
            """
        )
        db.execSQL("DROP TABLE sessions")
        db.execSQL("ALTER TABLE sessions_new RENAME TO sessions")

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sessions_day_of_week_start_minutes` " +
                "ON `sessions` (`day_of_week`, `start_minutes`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_classroom_id` ON `sessions` (`classroom_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_subject_id` ON `sessions` (`subject_id`)")
    }
}


/** Avatar color of the first profile ("Me"), as a signed ARGB int (0xFF6B7280). */
const val DEFAULT_PROFILE_COLOR = -9735552

/**
 * v4 -> v5: profiles (parent mode). Everything that exists becomes profile 1, "Me", and classrooms,
 * subjects and sessions are rebuilt with a profile_id. Names become unique per profile.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL COLLATE NOCASE, `color` INTEGER NOT NULL, `is_me` INTEGER NOT NULL, " +
                "`reminders` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_profiles_name` ON `profiles` (`name`)")
        db.execSQL(
            "INSERT INTO profiles (id, name, color, is_me, reminders, updated_at) VALUES (1, 'Me', ?, 1, 1, ?)",
            arrayOf<Any>(DEFAULT_PROFILE_COLOR, System.currentTimeMillis()),
        )

        // Park the sessions in a plain table and drop the old one first, so no table ever points at
        // a dropped classroom or subject table (this works whether or not foreign keys are enforced)
        db.execSQL(
            "CREATE TABLE sessions_backup AS " +
                "SELECT id, day_of_week, start_minutes, end_minutes, subject_id, classroom_id FROM sessions"
        )
        db.execSQL("DROP TABLE sessions")

        db.execSQL(
            "CREATE TABLE `classrooms_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profile_id` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL COLLATE NOCASE, " +
                "FOREIGN KEY(`profile_id`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        db.execSQL("INSERT INTO classrooms_new (id, profile_id, name) SELECT id, 1, name FROM classrooms")
        db.execSQL("DROP TABLE classrooms")
        db.execSQL("ALTER TABLE classrooms_new RENAME TO classrooms")

        db.execSQL(
            "CREATE TABLE `subjects_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profile_id` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL COLLATE NOCASE, " +
                "`background_color` INTEGER NOT NULL DEFAULT $DEFAULT_BACKGROUND, " +
                "`foreground_color` INTEGER NOT NULL DEFAULT $DEFAULT_FOREGROUND, " +
                "FOREIGN KEY(`profile_id`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        db.execSQL(
            "INSERT INTO subjects_new (id, profile_id, name, background_color, foreground_color) " +
                "SELECT id, 1, name, background_color, foreground_color FROM subjects"
        )
        db.execSQL("DROP TABLE subjects")
        db.execSQL("ALTER TABLE subjects_new RENAME TO subjects")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profile_id` INTEGER NOT NULL, " +
                "`day_of_week` INTEGER NOT NULL, `start_minutes` INTEGER NOT NULL, `end_minutes` INTEGER NOT NULL, " +
                "`subject_id` INTEGER NOT NULL, `classroom_id` INTEGER NOT NULL, " +
                "FOREIGN KEY(`profile_id`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                "FOREIGN KEY(`classroom_id`) REFERENCES `classrooms`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                "FOREIGN KEY(`subject_id`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        db.execSQL(
            "INSERT INTO sessions (id, profile_id, day_of_week, start_minutes, end_minutes, subject_id, classroom_id) " +
                "SELECT id, 1, day_of_week, start_minutes, end_minutes, subject_id, classroom_id FROM sessions_backup"
        )
        db.execSQL("DROP TABLE sessions_backup")

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_classrooms_profile_id_name` ON `classrooms` (`profile_id`, `name`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_subjects_profile_id_name` ON `subjects` (`profile_id`, `name`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sessions_profile_id_day_of_week_start_minutes` " +
                "ON `sessions` (`profile_id`, `day_of_week`, `start_minutes`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_classroom_id` ON `sessions` (`classroom_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_subject_id` ON `sessions` (`subject_id`)")
    }
}
