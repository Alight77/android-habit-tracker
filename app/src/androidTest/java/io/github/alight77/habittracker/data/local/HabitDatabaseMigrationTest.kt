package io.github.alight77.habittracker.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class HabitDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HabitDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3_convertsLegacyLocalMidnightMillisToEpochDay() {
        val databaseName = "habit-migration-conversion-test"
        val expectedDate = LocalDate.of(2026, 9, 19)
        val legacyMillis = expectedDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        helper.createDatabase(databaseName, 2).apply {
            insertHabit()
            execSQL("INSERT INTO records (id, habitId, date, isDone) VALUES (1, 1, $legacyMillis, 1)")
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            HabitDatabaseMigrations.MIGRATION_2_3
        ).apply {
            query("SELECT date, isDone FROM records WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(expectedDate.toEpochDay(), cursor.getLong(0))
                assertEquals(1, cursor.getInt(1))
            }
            close()
        }
    }

    @Test
    fun migrate2To3_mergesSameHabitDayKeepingDoneState() {
        val databaseName = "habit-migration-merge-test"
        val expectedDate = LocalDate.of(2026, 9, 19)
        val startOfDayMillis = expectedDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val middayMillis = expectedDate
            .atTime(LocalTime.NOON)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        helper.createDatabase(databaseName, 2).apply {
            insertHabit()
            execSQL("INSERT INTO records (id, habitId, date, isDone) VALUES (1, 1, $startOfDayMillis, 0)")
            execSQL("INSERT INTO records (id, habitId, date, isDone) VALUES (2, 1, $middayMillis, 1)")
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            HabitDatabaseMigrations.MIGRATION_2_3
        ).apply {
            query("SELECT date, isDone FROM records WHERE habitId = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(expectedDate.toEpochDay(), cursor.getLong(0))
                assertEquals(1, cursor.getInt(1))
                assertFalse(cursor.moveToNext())
            }
            close()
        }
    }

    @Test
    fun migrate3To4_removesOrphanRecords() {
        val databaseName = "habit-migration-orphan-record-test"

        helper.createDatabase(databaseName, 3).apply {
            insertHabit()
            execSQL("INSERT INTO records (id, habitId, date, isDone) VALUES (1, 1, 20000, 1)")
            execSQL("INSERT INTO records (id, habitId, date, isDone) VALUES (2, 99, 20001, 1)")
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            HabitDatabaseMigrations.MIGRATION_3_4
        ).apply {
            query("SELECT habitId FROM records ORDER BY id").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertFalse(cursor.moveToNext())
            }
            close()
        }
    }

    @Test
    fun migrate3To4_cascadesRecordsWhenHabitDeleted() {
        val databaseName = "habit-migration-cascade-test"

        helper.createDatabase(databaseName, 3).apply {
            insertHabit()
            execSQL("INSERT INTO records (id, habitId, date, isDone) VALUES (1, 1, 20000, 1)")
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            HabitDatabaseMigrations.MIGRATION_3_4
        ).close()

        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java,
            databaseName
        ).build()
        try {
            runBlocking {
                database.habitDao().deleteHabit(
                    HabitEntity(
                        id = 1,
                        name = "Read",
                        description = "",
                        targetPerWeek = 7,
                        createdAt = 0
                    )
                )
                assertTrue(database.recordDao().getAllRecords().first().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertHabit() {
        execSQL(
            "INSERT INTO habits (id, name, description, targetPerWeek, createdAt) VALUES (1, 'Read', '', 7, 0)"
        )
    }
}
