package com.example.worker

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.os.Build
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
            sendActivityNotification("Web Monitor Enabled", "Website background check tracking has started (Interval: $intervalMinutes mins).")
        } else {
            WorkManager.getInstance(applicationContext).cancelUniqueWork("WebTrackerWorker")
            Log.d(tag, "WebTrackerWorker cancelled via Tile toggled OFF")
            sendActivityNotification("Web Monitor Disabled", "Website background check tracking stopped.")
        }

        updateTileState()
    }

    private fun sendActivityNotification(title: String, message: String) {
        val channelId = "webview_tracker_updates"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(9999, notification)
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
