package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingHistoryDao {
    @Query("SELECT * FROM tracking_histories WHERE ruleId = :ruleId ORDER BY timestamp DESC")
    fun getHistoryByRule(ruleId: Int): Flow<List<TrackingHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TrackingHistory): Long

    @Query("DELETE FROM tracking_histories WHERE ruleId = :ruleId")
    suspend fun deleteHistoryByRule(ruleId: Int)
}
