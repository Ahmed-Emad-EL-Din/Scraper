package com.example.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SettingsStorage(context: Context) {
    private val tag = "SettingsStorage"

    private val sharedPrefs = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_settings",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(tag, "Failed to initialize EncryptedSharedPreferences, falling back to standard private preferences", e)
        context.getSharedPreferences("secure_settings_fallback", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_TRACKER_INTERVAL_MINUTES = "tracker_interval_minutes"
        private const val KEY_TRACKER_ENABLED = "tracker_enabled"
        private const val KEY_USER_AGENT = "user_agent"
    }

    fun saveGeminiApiKey(apiKey: String) {
        sharedPrefs.edit().putString(KEY_GEMINI_API_KEY, apiKey).apply()
    }

    fun getGeminiApiKey(): String {
        return sharedPrefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    }

    fun saveUserAgent(userAgent: String) {
        sharedPrefs.edit().putString(KEY_USER_AGENT, userAgent).apply()
    }

    fun getUserAgent(): String {
        return sharedPrefs.getString(KEY_USER_AGENT, "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36") ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    fun saveAiModel(model: String) {
        sharedPrefs.edit().putString(KEY_AI_MODEL, model).apply()
    }

    fun getAiModel(): String {
        return sharedPrefs.getString(KEY_AI_MODEL, "gemini-1.5-flash") ?: "gemini-1.5-flash"
    }

    fun saveTrackerIntervalMinutes(minutes: Int) {
        sharedPrefs.edit().putInt(KEY_TRACKER_INTERVAL_MINUTES, minutes).apply()
    }

    fun getTrackerIntervalMinutes(): Int {
        return sharedPrefs.getInt(KEY_TRACKER_INTERVAL_MINUTES, 15)
    }

    fun saveTrackerEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_TRACKER_ENABLED, enabled).apply()
    }

    fun isTrackerEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_TRACKER_ENABLED, true)
    }
}
