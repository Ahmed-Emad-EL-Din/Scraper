package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.AppDatabase
import com.example.data.TrackingRule
import com.example.data.TrackingRuleRepository
import com.example.worker.WebTrackerWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "TrackerViewModel"
    private val repository: TrackingRuleRepository

    // Modern StateFlow collection matching the Room Database integration guidelines
    val activeRules: StateFlow<List<TrackingRule>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TrackingRuleRepository(database.trackingRuleDao(), database.trackingHistoryDao())
        activeRules = repository.allRules
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        scheduleBackgroundChecks(application)
    }

    private fun scheduleBackgroundChecks(application: Application) {
        try {
            Log.d(tag, "Scheduling background periodic scans with WorkManager...")
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Run every 15 minutes - the background minimum allowed by WorkManager
            val workRequest = PeriodicWorkRequestBuilder<WebTrackerWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(application).enqueueUniquePeriodicWork(
                "WebTrackerWorker",
                ExistingPeriodicWorkPolicy.KEEP, // Retains schedule if already enqueued
                workRequest
            )
            Log.d(tag, "Successfully secured periodic WorkManager registration")
        } catch (e: Exception) {
            Log.e(tag, "Failed to register background checks with WorkManager", e)
        }
    }

    /**
     * Add tracking rule to the database (coroutine scope handled)
     */
    fun addRule(
        url: String,
        cssSelector: String?,
        isPremium: Boolean,
        aiPrompt: String? = null,
        checkIntervalMinutes: Int = 15,
        isTrackWholePage: Boolean = false,
        isTrackList: Boolean = false,
        aiCondition: String? = null
    ) {
        viewModelScope.launch {
            repository.insertRule(
                TrackingRule(
                    url = url,
                    cssSelector = cssSelector,
                    lastKnownText = "Waiting for check",
                    isPremiumRule = isPremium,
                    aiPrompt = aiPrompt,
                    checkIntervalMinutes = checkIntervalMinutes,
                    isTrackWholePage = isTrackWholePage,
                    isTrackList = isTrackList,
                    aiCondition = aiCondition
                )
            )
            // Re-schedule just in case WorkManager was paused due to a prior Session Expired event.
            scheduleBackgroundChecks(getApplication())
        }
    }

    /**
     * Delete tracking rule by its database primary ID
     */
    fun deleteRule(ruleId: Int) {
        viewModelScope.launch {
            repository.deleteRuleById(ruleId)
        }
    }

    /**
     * Toggle pause condition on a rule
     */
    fun togglePauseRule(rule: TrackingRule) {
        viewModelScope.launch {
            repository.updateRule(rule.copy(isPaused = !rule.isPaused))
        }
    }

    /**
     * Fetch flow of historical change records for a rule
     */
    fun getHistoryForRule(ruleId: Int) = repository.getHistoryByRule(ruleId)

    // Factory pattern to simplify construction in compose views
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrackerViewModel::class.java)) {
                return TrackerViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
