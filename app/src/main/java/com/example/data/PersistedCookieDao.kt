package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PersistedCookieDao {
    @Query("SELECT * FROM persisted_cookies")
    suspend fun getAllCookies(): List<PersistedCookie>

    @Query("SELECT * FROM persisted_cookies WHERE domain = :domain")
    suspend fun getCookiesForDomain(domain: String): List<PersistedCookie>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCookie(cookie: PersistedCookie)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCookies(cookies: List<PersistedCookie>)

    @Query("DELETE FROM persisted_cookies WHERE domain = :domain AND name = :name")
    suspend fun deleteCookie(domain: String, name: String)

    @Query("DELETE FROM persisted_cookies WHERE domain = :domain")
    suspend fun deleteCookiesForDomain(domain: String)

    @Query("DELETE FROM persisted_cookies")
    suspend fun clearAllCookies()
}
