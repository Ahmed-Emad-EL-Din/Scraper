package com.example.worker

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.SettingsStorage
import java.util.concurrent.TimeUnit

class WebMonitorTileService : TileService() {
    private val tag = "WebMonitorTileService"

    override fun onStartListening() {
        super.onStartListening()
        Log.d(tag, "onStartListening")
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        Log.d(tag, "onClick")
        val settings = SettingsStorage(applicationContext)
        val currentlyEnabled = settings.isTrackerEnabled()
        val newState = !currentlyEnabled

        settings.saveTrackerEnabled(newState)

        if (newState) {
            val intervalMinutes = settings.getTrackerIntervalMinutes()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WebTrackerWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "WebTrackerWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            Log.d(tag, "WebTrackerWorker started via Tile toggled ON with interval $intervalMinutes mins")
        } else {
            WorkManager.getInstance(applicationContext).cancelUniqueWork("WebTrackerWorker")
            Log.d(tag, "WebTrackerWorker cancelled via Tile toggled OFF")
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val settings = SettingsStorage(applicationContext)
        
        val isWorkScheduled = try {
            val workInfos = WorkManager.getInstance(applicationContext)
                .getWorkInfosForUniqueWork("WebTrackerWorker")
                .get()
            workInfos.any { !it.state.isFinished }
        } catch (e: Exception) {
            false
        }

        val isActive = settings.isTrackerEnabled() && isWorkScheduled

        if (isActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Web Monitor"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val mins = settings.getTrackerIntervalMinutes()
                tile.subtitle = if (mins < 60) {
                    "${mins}m interval"
                } else {
                    "${mins / 60}h interval"
                }
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Web Monitor"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                tile.subtitle = "Inactive"
            }
        }
        tile.updateTile()
    }
}
