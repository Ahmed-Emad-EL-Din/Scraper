package com.example.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
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
import com.example.data.SettingsStorage
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

    fun scheduleBackgroundChecks(application: Application) {
        try {
            Log.d(tag, "Scheduling background periodic scans with WorkManager...")
            val settings = SettingsStorage(application)
            if (!settings.isTrackerEnabled()) {
                Log.d(tag, "Web tracker is globally disabled. Cancelling work.")
                WorkManager.getInstance(application).cancelUniqueWork("WebTrackerWorker")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val intervalMinutes = settings.getTrackerIntervalMinutes()
            Log.d(tag, "Scheduling WorkManager with current interval: $intervalMinutes minutes")

            val workRequest = PeriodicWorkRequestBuilder<WebTrackerWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(application).enqueueUniquePeriodicWork(
                "WebTrackerWorker",
                ExistingPeriodicWorkPolicy.UPDATE, // Updates the schedule with new configurations
                workRequest
            )
            Log.d(tag, "Successfully secured periodic WorkManager registration")
        } catch (e: Exception) {
            Log.e(tag, "Failed to register background checks with WorkManager", e)
        }
    }

    /**
     * Dispatch an immediate one-time track scan check for all active rules
     */
    fun runImmediateCheck(application: Application) {
        try {
            Log.d(tag, "Scheduling an immediate check via OneTimeWorkRequest...")
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workInput = androidx.work.workDataOf("is_forced" to true)
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<WebTrackerWorker>()
                .setConstraints(constraints)
                .setInputData(workInput)
                .build()

            WorkManager.getInstance(application).enqueueUniqueWork(
                "WebTrackerWorkerImmediate",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(tag, "Successfully queued immediate check request")
        } catch (e: Exception) {
            Log.e(tag, "Failed to run immediate check", e)
        }
    }

    /**
     * Post helper notification for system status updates
     */
    fun sendActivityNotification(context: Context, title: String, message: String) {
        val channelId = "webview_tracker_updates"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Web Monitor Background Activity",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when Web Monitor tasks change status."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(9999, notification)
    }

    private fun cleanUrlForDisplay(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: url
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            url
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
        aiCondition: String? = null,
        initialText: String = "Waiting for check",
        isStealthMode: Boolean = false
    ) {
        viewModelScope.launch {
            repository.insertRule(
                TrackingRule(
                    url = url,
                    cssSelector = cssSelector,
                    lastKnownText = initialText.trim().ifEmpty { "Waiting for check" },
                    isPremiumRule = isPremium,
                    aiPrompt = aiPrompt,
                    checkIntervalMinutes = checkIntervalMinutes,
                    isTrackWholePage = isTrackWholePage,
                    isTrackList = isTrackList,
                    aiCondition = aiCondition,
                    isStealthMode = isStealthMode
                )
            )
            // Re-schedule just in case WorkManager was paused due to a prior Session Expired event.
            scheduleBackgroundChecks(getApplication())

            // Immediately send dynamic notification
            sendActivityNotification(
                getApplication(),
                "Tracking Started",
                "Website monitoring successfully configured for: ${cleanUrlForDisplay(url)}"
            )

            // Force run an immediate check so the user doesn't wait 15 minutes
            runImmediateCheck(getApplication())
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
            val nextPaused = !rule.isPaused
            repository.updateRule(rule.copy(isPaused = nextPaused))

            val stateName = if (nextPaused) "Paused" else "Resumed"
            sendActivityNotification(
                getApplication(),
                "Tracking $stateName",
                "Website checks are now $stateName for ${cleanUrlForDisplay(rule.url)}"
            )

            if (!nextPaused) {
                // If checking is resumed/reactivated, trigger an immediate scan check to sync
                runImmediateCheck(getApplication())
            }
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
