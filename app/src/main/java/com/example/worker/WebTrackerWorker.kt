package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.CookieStorage
import com.example.data.TrackingRule
import com.example.data.TrackingHistory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.example.MainActivity
import com.example.data.SettingsStorage

class WebTrackerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val tag = "WebTrackerWorker"
    private val database = AppDatabase.getDatabase(appContext)
    private val dao = database.trackingRuleDao()
    private val cookieStorage = CookieStorage(appContext)

    // Moshi & Retrofit Setup for Gemini
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val geminiApiService = retrofit.create(GeminiApiService::class.java)

    override suspend fun doWork(): Result {
        Log.d(tag, "Background web scraping check started")
        val rules = dao.getAllRulesDirect().filter { !it.isPaused }
        if (rules.isEmpty()) {
            Log.d(tag, "No active tracking rules. Stopping work.")
            return Result.success()
        }

        val currentTime = System.currentTimeMillis()
        val rulesByUrl = rules.groupBy { it.url }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        for ((url, urlRules) in rulesByUrl) {
            // Check if any rule for this URL is due for update
            val dueRules = urlRules.filter { rule ->
                val elapsedMinutes = (currentTime - rule.lastCheckedTimeMillis) / (1000 * 60)
                rule.lastCheckedTimeMillis == 0L || elapsedMinutes >= rule.checkIntervalMinutes
            }

            if (dueRules.isEmpty()) {
                continue
            }

            // Perform single request for this URL to avoid duplicate loads & respect battery / network
            try {
                Log.d(tag, "Running update checks for URL: $url")
                val cookies = cookieStorage.getCookies(url)
                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                
                if (!cookies.isNullOrBlank()) {
                    requestBuilder.header("Cookie", cookies)
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()

                // Session Expiry Handling
                val finalUrl = response.request.url.toString()
                val responseBodyText = response.body?.string() ?: ""
                val isLoginPage = finalUrl.contains("/login", ignoreCase = true) ||
                        finalUrl.contains("/signin", ignoreCase = true) ||
                        responseBodyText.contains("type=\"password\"", ignoreCase = true) ||
                        responseBodyText.contains("name=\"password\"", ignoreCase = true)

                if (response.code == 401 || isLoginPage) {
                    Log.w(tag, "Session Expired detected for URL: $url (Code: ${response.code}, LoginPage: $isLoginPage)")
                    
                    // Pauses WorkManager and tells user to re-login
                    WorkManager.getInstance(applicationContext).cancelUniqueWork("WebTrackerWorker")
                    sendNotifications(
                        id = 9999,
                        title = "Session Expired",
                        message = "Your active login session expired. Please open the app and log in again."
                    )
                    return Result.success()
                }

                if (!response.isSuccessful) {
                    Log.e(tag, "Scraping failed with non-200 code: ${response.code}")
                    continue
                }

                val document = Jsoup.parse(responseBodyText)

                for (rule in dueRules) {
                    val newText = if (rule.isTrackWholePage || rule.cssSelector.isNullOrBlank()) {
                        document.text().trim()
                    } else if (rule.isTrackList) {
                        val elements = document.select(rule.cssSelector)
                        val map = mutableMapOf<String, String>()
                        elements.forEachIndexed { idx, el ->
                            val txt = el.text().trim()
                            if (txt.isNotEmpty()) {
                                map["Item ${idx + 1}"] = txt
                            }
                        }
                        try {
                            val adapter = moshi.adapter(Map::class.java)
                            adapter.toJson(map)
                        } catch (e: Exception) {
                            elements.text().trim()
                        }
                    } else {
                        document.select(rule.cssSelector).text().trim()
                    }

                    Log.d(tag, "Rule [${rule.id}] Selector: ${rule.cssSelector} -> New Text: $newText")

                    if (rule.lastKnownText == "Waiting for check") {
                        // First run initialization, don't trigger alert yet but initialize the text
                        dao.updateRule(
                            rule.copy(
                                lastKnownText = newText,
                                lastCheckedTimeMillis = currentTime
                            )
                        )
                        continue
                    }

                    if (newText != rule.lastKnownText) {
                        try {
                            val historyDao = database.trackingHistoryDao()
                            historyDao.insertHistory(
                                TrackingHistory(
                                    ruleId = rule.id,
                                    oldText = rule.lastKnownText,
                                    newText = newText
                                )
                            )
                        } catch (histEx: Exception) {
                            Log.e(tag, "Failed to save tracking history record", histEx)
                        }

                        if (!rule.isPremiumRule) {
                            // Path A: Standard User
                            // Inform the user precisely of what changed from what to what.
                            val changeMsg = findTextDifference(rule.lastKnownText, newText)

                            sendNotifications(
                                id = rule.id,
                                title = "Update on ${cleanUrlForDisplay(rule.url)}",
                                message = changeMsg
                            )

                            dao.updateRule(
                                rule.copy(
                                    previousText = rule.lastKnownText,
                                    lastKnownText = newText,
                                    lastCheckedTimeMillis = currentTime
                                )
                            )
                        } else {
                            // Path B: Premium Smart User (Gemini AI Gate)
                            Log.d(tag, "Triggering Smart Gemini evaluation gate for Premium rule: ${rule.id}")
                            val geminiPrompt = """
                                You are an automated web monitoring assistant. 
                                Old Website Text: "${rule.lastKnownText}"
                                New Website Text: "$newText"
                                User's Condition Rule: "${rule.aiPrompt ?: "summarize the change"}"

                                TASK:
                                1. Determine if the 'New Website Text' meets the 'User's Condition Rule'.
                                2. If it DOES NOT meet the rule (or is a trivial irrelevant change), reply with EXACTLY the word: "IGNORE".
                                3. If it DOES meet the rule, reply starting with "TRIGGER: " followed by a very short, human-readable summary of what changed (maximum 2 sentences).
                            """.trimIndent()

                            var aiSummary: String? = null
                            var shouldTriggerNotification = false

                            try {
                                val settingsStorage = SettingsStorage(applicationContext)
                                var apiKey = settingsStorage.getGeminiApiKey().trim()
                                if (apiKey.isEmpty()) {
                                    apiKey = BuildConfig.GEMINI_API_KEY
                                }
                                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                                    throw IllegalStateException("AI API Key is missing. Please set your Gemini API Key in Settings.")
                                }

                                val selectedModel = settingsStorage.getAiModel().trim()
                                val requestPayload = GeminiRequest(
                                    contents = listOf(
                                        GeminiRequest.Content(
                                            parts = listOf(GeminiRequest.Part(text = geminiPrompt))
                                        )
                                    )
                                )

                                val aiResponse = geminiApiService.generateContent(selectedModel, apiKey, requestPayload)
                                val responseContent = aiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim() ?: ""

                                Log.d(tag, "Gemini API decision response: $responseContent")

                                if (responseContent.startsWith("TRIGGER:", ignoreCase = true)) {
                                    shouldTriggerNotification = true
                                    aiSummary = responseContent.substring(8).trim()
                                } else if (responseContent.contains("TRIGGER:", ignoreCase = true)) {
                                    shouldTriggerNotification = true
                                    val triggerIdx = responseContent.indexOf("TRIGGER:", ignoreCase = true)
                                    aiSummary = responseContent.substring(triggerIdx + 8).trim()
                                } else {
                                    // Default output if IGNORE or anything else contains IGNORE
                                    Log.d(tag, "Gemini resolved page change as trivial IGNORE. Updating local state silently.")
                                }

                            } catch (e: Exception) {
                                Log.e(tag, "AI Api failed or timed out. Defaulting back to Path A standard notification.", e)
                                // Fallback: Default to Path A standard notification to prevent missing important updates
                                shouldTriggerNotification = true
                                aiSummary = findTextDifference(rule.lastKnownText, newText) + " (AI Engine Offline: ${e.message})"
                            }

                            // Update Database and notify user if triggered
                            dao.updateRule(
                                rule.copy(
                                    previousText = rule.lastKnownText,
                                    lastKnownText = newText,
                                    lastCheckedTimeMillis = currentTime,
                                    lastAiSummary = if (shouldTriggerNotification) aiSummary else null
                                )
                            )

                            if (shouldTriggerNotification && aiSummary != null) {
                                sendNotifications(
                                    id = rule.id,
                                    title = "Smart Tracker Triggered",
                                    message = aiSummary
                                )
                            }
                        }
                    } else {
                        // Value matches the last known text exactly. Just update last checked timestamp
                        dao.updateRule(rule.copy(lastCheckedTimeMillis = currentTime))
                    }
                }

            } catch (e: Exception) {
                Log.e(tag, "Connection/Scraping error checking URL: $url", e)
            }
        }

        return Result.success()
    }

    private fun sendNotifications(id: Int, title: String, message: String) {
        val channelId = "webview_tracker_updates"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Web Tracker Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when monitored elements change"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = android.content.Intent(applicationContext, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("rule_id", id)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            id,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        notificationManager.notify(id, builder.build())
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

    private fun findTextDifference(oldText: String, newText: String): String {
        val cleanOld = oldText.trim()
        val cleanNew = newText.trim()
        if (cleanOld == cleanNew) return "Page content unchanged."

        // If either is short, just show 'from X to Y'
        if (cleanOld.length <= 100 && cleanNew.length <= 100) {
            return "Changed from: '$cleanOld' ➡️ '$cleanNew'"
        }

        // Sentence or Line-based smart extraction if it's long
        val oldLines = cleanOld.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val newLines = cleanNew.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val addedLines = newLines.filter { !oldLines.contains(it) }
        val removedLines = oldLines.filter { !newLines.contains(it) }

        if (addedLines.isNotEmpty() && removedLines.isNotEmpty()) {
            val rem = removedLines.first().take(120)
            val add = addedLines.first().take(120)
            return "Changed: '$rem' ➡️ '$add'"
        } else if (addedLines.isNotEmpty()) {
            return "Added segment: '${addedLines.first().take(150)}'"
        } else if (removedLines.isNotEmpty()) {
            return "Removed segment: '${removedLines.first().take(150)}'"
        }

        // Falling back to character-segment boundary comparison
        var commonPrefixLength = 0
        val minLength = minOf(cleanOld.length, cleanNew.length)
        while (commonPrefixLength < minLength && cleanOld[commonPrefixLength] == cleanNew[commonPrefixLength]) {
            commonPrefixLength++
        }

        var commonSuffixLength = 0
        while (commonSuffixLength < (minLength - commonPrefixLength) &&
            cleanOld[cleanOld.length - 1 - commonSuffixLength] == cleanNew[cleanNew.length - 1 - commonSuffixLength]) {
            commonSuffixLength++
        }

        val oldDiffSegment = cleanOld.substring(commonPrefixLength, cleanOld.length - commonSuffixLength).trim()
        val newDiffSegment = cleanNew.substring(commonPrefixLength, cleanNew.length - commonSuffixLength).trim()

        return if (oldDiffSegment.isNotEmpty() && newDiffSegment.isNotEmpty()) {
            "Changed: '${oldDiffSegment.take(80)}' ➡️ '${newDiffSegment.take(80)}'"
        } else if (newDiffSegment.isNotEmpty()) {
            "Added: '${newDiffSegment.take(150)}'"
        } else if (oldDiffSegment.isNotEmpty()) {
            "Removed: '${oldDiffSegment.take(150)}'"
        } else {
            "Update detected: '${cleanNew.take(150)}'"
        }
    }
}

// Retrofit payload definitions matching strict REST formats
interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

data class GeminiRequest(
    val contents: List<Content>
) {
    data class Content(
        val parts: List<Part>
    )
    data class Part(
        val text: String
    )
}

data class GeminiResponse(
    val candidates: List<Candidate>?
) {
    data class Candidate(
        val content: Content?
    )
    data class Content(
        val parts: List<Part>?
    )
    data class Part(
        val text: String?
    )
}
