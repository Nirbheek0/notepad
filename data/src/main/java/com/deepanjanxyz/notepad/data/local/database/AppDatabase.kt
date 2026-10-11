package com.deepanjanxyz.notepad.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.deepanjanxyz.notepad.data.local.dao.LabelDao
import com.deepanjanxyz.notepad.data.local.dao.NoteDao
import com.deepanjanxyz.notepad.data.local.entity.LabelEntity
import com.deepanjanxyz.notepad.data.local.entity.NoteEntity

@Database(
    entities = [NoteEntity::class, LabelEntity::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun labelDao(): LabelDao

    companion object {
        private const val DATABASE_NAME = "notes.db"

        /**
         * Migrates the pre-Room database to the current Room schema.
         *
         * Room was only ever shipped at version 5, so the single historical schema
         * that can still be on disk is the old SQLiteOpenHelper `notes.db`
         * (`user_version` 1) with a `notes_table(ID, TITLE, CONTENT, DATE)` and no
         * labels table. Without this migration a user updating directly from a
         * pre-Room release would hit a missing-migration failure; the table is
         * recreated to match Room's expected schema and the existing notes are
         * copied across (the columns Room added fall back to their defaults).
         */
        private val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes_table_new` (" +
                        "`ID` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`TITLE` TEXT NOT NULL, " +
                        "`CONTENT` TEXT NOT NULL, " +
                        "`DATE` TEXT NOT NULL, " +
                        "`PINNED` INTEGER NOT NULL DEFAULT 0, " +
                        "`COLOR_INDEX` INTEGER NOT NULL DEFAULT 0, " +
                        "`TAGS` TEXT NOT NULL DEFAULT '', " +
                        "`IN_TRASH` INTEGER NOT NULL DEFAULT 0, " +
                        "`IN_ARCHIVE` INTEGER NOT NULL DEFAULT 0, " +
                        "`REMINDER_TIME` INTEGER)"
                )
                db.execSQL(
                    "INSERT INTO `notes_table_new` (`ID`, `TITLE`, `CONTENT`, `DATE`) " +
                        "SELECT `ID`, COALESCE(`TITLE`, ''), COALESCE(`CONTENT`, ''), " +
                        "COALESCE(`DATE`, '') FROM `notes_table`"
                )
                db.execSQL("DROP TABLE `notes_table`")
                db.execSQL("ALTER TABLE `notes_table_new` RENAME TO `notes_table`")

                // The labels table did not exist before Room.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `labels_table` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL)"
                )
            }
        }

        /**
         * Adds the real created/updated timestamps behind the sort orders.
         *
         * "Last modified" used to order by row id, which never changes when an
         * old note is edited, so a freshly edited note did not necessarily rise
         * to the top. Rows written before this version carry no timestamp, so
         * they are backfilled from the id: the id is monotonic with insertion
         * order, which reproduces exactly the ordering the old code produced.
         * Every later save stores a real epoch-millis value instead.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `notes_table` ADD COLUMN `CREATED_AT` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `notes_table` ADD COLUMN `UPDATED_AT` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("UPDATE `notes_table` SET `CREATED_AT` = `ID`, `UPDATED_AT` = `ID`")
            }
        }

        /**
         * Adds the real trash timestamp behind the retention window.
         *
         * Notes already sitting in the trash have no known trash time, so they
         * are stamped with the moment of the upgrade instead of their last edit.
         * That gives them a full retention window rather than deleting them on
         * the first launch after updating.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `notes_table` ADD COLUMN `TRASHED_AT` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE `notes_table` SET `TRASHED_AT` = " +
                        "CAST(strftime('%s','now') AS INTEGER) * 1000 WHERE `IN_TRASH` = 1"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    // Destructive migration is deliberately NOT enabled: silently
                    // wiping the user's notes when the schema changes is never
                    // acceptable. The legacy pre-Room schema is handled explicitly
                    // by MIGRATION_1_5, and exported schemas (see the
                    // room.schemaLocation argument) make it possible to author a
                    // further Migration for any future version bump.
                    .addMigrations(MIGRATION_1_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
