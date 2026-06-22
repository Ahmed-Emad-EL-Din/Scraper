package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persisted_cookies")
data class PersistedCookie(
    @PrimaryKey val id: String, // format: "domain|name"
    val domain: String,
    val name: String,
    val value: String,
    val url: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean
)
