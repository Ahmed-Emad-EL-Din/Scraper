package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingRuleDao {
    @Query("SELECT * FROM tracking_rules ORDER BY id DESC")
    fun getAllRules(): Flow<List<TrackingRule>>

    @Query("SELECT * FROM tracking_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: Int): TrackingRule?

    @Query("SELECT * FROM tracking_rules")
    suspend fun getAllRulesDirect(): List<TrackingRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: TrackingRule): Long

    @Update
    suspend fun updateRule(rule: TrackingRule)

    @Delete
    suspend fun deleteRule(rule: TrackingRule)

    @Query("DELETE FROM tracking_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)
}
