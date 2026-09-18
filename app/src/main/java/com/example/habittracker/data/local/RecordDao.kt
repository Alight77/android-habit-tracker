package com.example.habittracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    @Insert
    suspend fun insertRecord(record: RecordEntity)

    @Delete
    suspend fun deleteRecord(record: RecordEntity)

    @Update
    suspend fun updateRecord(record: RecordEntity)

    @Transaction
    suspend fun setRecordChecked(habitId: Int, date: Long, targetChecked: Boolean) {
        val record = getRecordByDate(habitId, date)

        if (record == null) {
            insertRecord(
                RecordEntity(
                    habitId = habitId,
                    date = date,
                    isDone = targetChecked
                )
            )
        } else {
            updateRecord(record.copy(isDone = targetChecked))
        }
    }

    @Query(
        """
        SELECT * FROM records
        WHERE habitId = :habitId AND date = :date
    """
    )
    suspend fun getRecordByDate(habitId: Int, date: Long): RecordEntity?

    @Query(
        """
        SELECT * FROM records
        WHERE habitId = :habitId
    """
    )
    fun getRecordsForHabitId(habitId: Int): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<RecordEntity>>

    @Query(
        """
        SELECT * FROM records
        WHERE habitId = :habitId AND date = :date
    """
    )
    fun observeRecordByDate(habitId: Int, date: Long): Flow<RecordEntity?>

    @Query(
        """
        SELECT * FROM records
        WHERE habitId = :habitId AND isDone = 1
        ORDER BY date DESC
    """
    )
    fun getDoneRecord(habitId: Int): Flow<List<RecordEntity>>
}