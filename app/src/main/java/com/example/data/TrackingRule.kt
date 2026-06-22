package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_rules")
data class TrackingRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val cssSelector: String?,
    val lastKnownText: String,
    val isPremiumRule: Boolean,
    val aiPrompt: String?,
    val checkIntervalMinutes: Int = 15,
    val lastCheckedTimeMillis: Long = 0L,
    val isTrackWholePage: Boolean = false,
    val isTrackList: Boolean = false,
    val aiCondition: String? = null,
    val previousText: String? = null,
    val isPaused: Boolean = false,
    val lastAiSummary: String? = null
)
