package io.github.alight77.habittracker.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alight77.habittracker.data.repository.HabitRepository
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
        ).addMigrations(HabitDatabaseMigrations.MIGRATION_4_5).build()
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

    @Test
    fun migrate4To5_preservesHabitsAndRecordsAndAddsGeneralKey() {
        val databaseName = "habit-migration-icon-preservation-test"

        helper.createDatabase(databaseName, 4).apply {
            execSQL("INSERT INTO habits (id, name, description, targetPerWeek, createdAt) VALUES (7, 'Read', 'books', 3, 100)")
            execSQL("INSERT INTO habits (id, name, description, targetPerWeek, createdAt) VALUES (11, 'Run', '', 5, 200)")
            execSQL("INSERT INTO records (id, habitId, date, isDone) VALUES (21, 7, 20000, 1)")
            execSQL("INSERT INTO records (id, habitId, date, isDone) VALUES (22, 7, 20001, 0)")
            execSQL("INSERT INTO records (id, habitId, date, isDone) VALUES (23, 11, 20002, 1)")
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName, 5, true, HabitDatabaseMigrations.MIGRATION_4_5
        ).apply {
            query("SELECT id, name, description, targetPerWeek, createdAt, iconKey FROM habits ORDER BY id").use { cursor ->
                assertEquals(2, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(7, cursor.getInt(0))
                assertEquals("Read", cursor.getString(1))
                assertEquals("books", cursor.getString(2))
                assertEquals(3, cursor.getInt(3))
                assertEquals(100L, cursor.getLong(4))
                assertEquals("general", cursor.getString(5))
                assertTrue(cursor.moveToNext())
                assertEquals(11, cursor.getInt(0))
                assertEquals("Run", cursor.getString(1))
                assertEquals(5, cursor.getInt(3))
                assertEquals(200L, cursor.getLong(4))
                assertEquals("general", cursor.getString(5))
                assertFalse(cursor.moveToNext())
            }
            query("SELECT id, habitId, date, isDone FROM records ORDER BY id").use { cursor ->
                assertEquals(3, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(listOf(21L, 7L, 20000L, 1L), (0..3).map(cursor::getLong))
                assertTrue(cursor.moveToNext())
                assertEquals(listOf(22L, 7L, 20001L, 0L), (0..3).map(cursor::getLong))
                assertTrue(cursor.moveToNext())
                assertEquals(listOf(23L, 11L, 20002L, 1L), (0..3).map(cursor::getLong))
                assertFalse(cursor.moveToNext())
            }
            query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
            close()
        }
    }

    @Test
    fun migratedDatabase_readsWritesRawKeysAndKeepsRecordCascade() {
        val databaseName = "habit-migration-icon-write-test"
        helper.createDatabase(databaseName, 4).apply {
            execSQL("INSERT INTO habits (id, name, description, targetPerWeek, createdAt) VALUES (7, 'Read', '', 3, 100)")
            execSQL("INSERT INTO records (id, habitId, date, isDone) VALUES (21, 7, 20000, 1)")
            close()
        }
        helper.runMigrationsAndValidate(
            databaseName, 5, true, HabitDatabaseMigrations.MIGRATION_4_5
        ).close()

        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HabitDatabase::class.java,
            databaseName
        ).addMigrations(HabitDatabaseMigrations.MIGRATION_4_5).build()
        try {
            runBlocking {
                val repository = HabitRepository(database.habitDao(), database.recordDao())
                val oldHabit = requireNotNull(repository.getHabitById(7))
                assertEquals("general", oldHabit.iconKey)

                repository.updateHabit(oldHabit.copy(name = "Morning read", iconKey = "reading"))
                assertEquals("reading", repository.getHabitById(7)?.iconKey)
                // 未来版本 Key 必须原样读写；展示回退不在持久化层发生。
                repository.updateHabit(requireNotNull(repository.getHabitById(7)).copy(iconKey = "cycling"))
                assertEquals("cycling", repository.getHabitById(7)?.iconKey)

                repository.addHabit(HabitEntity(name = "Drink", description = "", targetPerWeek = 7, createdAt = 300, iconKey = "water"))
                val newHabit = database.habitDao().getAllHabits().first().first { it.name == "Drink" }
                assertEquals("water", repository.getHabitById(newHabit.id)?.iconKey)
                repository.setTodayRecordChecked(newHabit.id, 20003, true)
                assertEquals(true, database.recordDao().getRecordByDate(newHabit.id, 20003)?.isDone)

                assertEquals(1, repository.deleteHabitById(7))
                assertEquals(null, database.recordDao().getRecordByDate(7, 20000))
                assertEquals(true, database.recordDao().getRecordByDate(newHabit.id, 20003)?.isDone)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate3To5_runsExistingRecordMigrationThenIconMigration() {
        val databaseName = "habit-migration-3-to-5-test"
        helper.createDatabase(databaseName, 3).apply {
            insertHabit()
            execSQL("INSERT INTO records (id, habitId, date, isDone) VALUES (9, 1, 20000, 1)")
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName, 5, true,
            HabitDatabaseMigrations.MIGRATION_3_4,
            HabitDatabaseMigrations.MIGRATION_4_5
        ).apply {
            query("SELECT iconKey FROM habits WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("general", cursor.getString(0))
            }
            query("SELECT id, habitId, date, isDone FROM records").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(listOf(9L, 1L, 20000L, 1L), (0..3).map(cursor::getLong))
            }
            close()
        }
    }

    @Test
    fun migrate2To5_runsTheCompleteUpgradeChain() {
        val databaseName = "habit-migration-2-to-5-test"
        val expectedDate = LocalDate.of(2026, 9, 19)
        val legacyMillis = expectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        helper.createDatabase(databaseName, 2).apply {
            insertHabit()
            execSQL("INSERT INTO records (id, habitId, date, isDone) VALUES (9, 1, $legacyMillis, 1)")
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName, 5, true,
            HabitDatabaseMigrations.MIGRATION_2_3,
            HabitDatabaseMigrations.MIGRATION_3_4,
            HabitDatabaseMigrations.MIGRATION_4_5
        ).apply {
            query("SELECT targetPerWeek, iconKey FROM habits WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(7, cursor.getInt(0))
                assertEquals("general", cursor.getString(1))
            }
            query("SELECT id, habitId, date, isDone FROM records").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(9L, cursor.getLong(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(expectedDate.toEpochDay(), cursor.getLong(2))
                assertEquals(1, cursor.getInt(3))
            }
            close()
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertHabit() {
        execSQL(
            "INSERT INTO habits (id, name, description, targetPerWeek, createdAt) VALUES (1, 'Read', '', 7, 0)"
        )
    }
}
