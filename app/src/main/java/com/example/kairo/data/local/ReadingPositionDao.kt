package com.example.kairo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingPositionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(entity: ReadingPositionEntity)

    @Query("SELECT * FROM reading_positions WHERE bookId = :bookId LIMIT 1")
    suspend fun getPosition(bookId: String): ReadingPositionEntity?

    @Query("SELECT * FROM reading_positions")
    fun getPositions(): Flow<List<ReadingPositionEntity>>

    @Query("DELETE FROM reading_positions WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
