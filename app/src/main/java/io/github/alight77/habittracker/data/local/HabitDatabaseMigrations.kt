package io.github.alight77.habittracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object HabitDatabaseMigrations {
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS records_migrated")
            db.execSQL(
                "CREATE TABLE records_migrated (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, habitId INTEGER NOT NULL, date INTEGER NOT NULL, isDone INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO records_migrated (id, habitId, date, isDone) " +
                    "SELECT MIN(id), habitId, " +
                    "CAST(julianday(date(date / 1000, 'unixepoch', 'localtime')) - julianday('1970-01-01') AS INTEGER), " +
                    "MAX(isDone) FROM records " +
                    "GROUP BY habitId, date(date / 1000, 'unixepoch', 'localtime')"
            )
            db.execSQL("DROP TABLE records")
            db.execSQL("ALTER TABLE records_migrated RENAME TO records")
            db.execSQL(
                "CREATE UNIQUE INDEX index_records_habitId_date ON records (habitId, date)"
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS records_migrated")
            db.execSQL(
                "CREATE TABLE records_migrated (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "habitId INTEGER NOT NULL, " +
                    "date INTEGER NOT NULL, " +
                    "isDone INTEGER NOT NULL, " +
                    "FOREIGN KEY(habitId) REFERENCES habits(id) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE" +
                    ")"
            )
            db.execSQL(
                "INSERT INTO records_migrated (id, habitId, date, isDone) " +
                    "SELECT records.id, records.habitId, records.date, records.isDone " +
                    "FROM records INNER JOIN habits ON records.habitId = habits.id"
            )
            db.execSQL("DROP TABLE records")
            db.execSQL("ALTER TABLE records_migrated RENAME TO records")
            db.execSQL(
                "CREATE UNIQUE INDEX index_records_habitId_date ON records (habitId, date)"
            )
        }
    }
}
