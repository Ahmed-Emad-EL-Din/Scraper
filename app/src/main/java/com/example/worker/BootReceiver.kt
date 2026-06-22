package com.example.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    private val tag = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(tag, "Boot completed broadcast received. Re-scheduling trackers...")
            val appContext = context.applicationContext
            
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(appContext)
                val activeRules = db.trackingRuleDao().getAllRulesDirect().filter { !it.isPaused }
                if (activeRules.isNotEmpty()) {
                    Log.d(tag, "Found ${activeRules.size} active non-paused rules. Re-enqueuing WorkManager task.")
                    try {
                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()

                        val workRequest = PeriodicWorkRequestBuilder<WebTrackerWorker>(15, TimeUnit.MINUTES)
                            .setConstraints(constraints)
                            .build()

                        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                            "WebTrackerWorker",
                            ExistingPeriodicWorkPolicy.UPDATE,
                            workRequest
                        )
                        Log.d(tag, "Periodic WorkManager check scheduled on boot successfully.")
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to schedule on boot", e)
                    }
                } else {
                    Log.d(tag, "No active custom non-paused rules. Skipping scheduler bootstrap.")
                }
            }
        }
    }
}
