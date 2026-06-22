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
import androidx.work.ForegroundInfo
import android.content.pm.ServiceInfo
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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channelId = "webview_tracker_updates"
        val notificationId = 8888
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Web Monitor Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Web Monitor running reliably in the background."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Web Monitor")
            .setContentText("Web Monitor is actively checking for updates...")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(tag, "Background web scraping check started")

        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.e(tag, "Failed to start foreground service", e)
        }

        val isForced = inputData.getBoolean("is_forced", false)

        val rules = dao.getAllRulesDirect().filter { !it.isPaused }
        if (rules.isEmpty()) {
            Log.d(tag, "No active tracking rules. Stopping work.")
            return Result.success()
        }

        val currentTime = System.currentTimeMillis()
        val rulesByUrl = rules.groupBy { it.url }
        
        val settingsStorage = com.example.data.SettingsStorage(applicationContext)
        val activeUserAgent = settingsStorage.getUserAgent()

        // Setup robust client timeouts (15 seconds) to prevent infinite hangs
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .cookieJar(com.example.data.PersistentCookieJar(applicationContext))
            .build()

        for ((url, urlRules) in rulesByUrl) {
            // Check if any rule for this URL is due for update
            val dueRules = urlRules.filter { rule ->
                val elapsedMinutes = (currentTime - rule.lastCheckedTimeMillis) / (1000 * 60)
                isForced || rule.lastKnownText == "Waiting for check" || rule.lastCheckedTimeMillis == 0L || elapsedMinutes >= rule.checkIntervalMinutes
            }

            if (dueRules.isEmpty()) {
                continue
            }

            // Perform single request for this URL to avoid duplicate loads & respect battery / network
            try {
                Log.d(tag, "Running update checks for URL: $url")
                
                // Ensure Jsoup connects include timeout as well if used
                val jsoupConn = Jsoup.connect(url).timeout(15000)

                val urlHasStealthMode = dueRules.any { it.isStealthMode }
                var htmlContent = ""
                var isFallbackUsed = false
                var finalUrl = url
                var responseCode = 200

                if (urlHasStealthMode) {
                    Log.d(tag, "URL has Stealth Mode enabled. Skipping OkHttp and using Headless WebView directly for: $url")
                    try {
                        htmlContent = fetchHtmlWithHiddenWebView(applicationContext, url)
                        isFallbackUsed = true
                        finalUrl = url
                        responseCode = 200
                    } catch (e: Exception) {
                        Log.e(tag, "Headless WebView failed for Stealth Mode URL: $url", e)
                        throw e
                    }
                } else {
                    // Try OkHttp first
                    var okHttpResponse: okhttp3.Response? = null
                    try {
                        val requestBuilder = Request.Builder()
                            .url(url)
                            .header("User-Agent", activeUserAgent)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                            .header("Accept-Language", "en-US,en;q=0.9")
                            .header("Accept-Encoding", "gzip, deflate, br")
                            .header("Upgrade-Insecure-Requests", "1")
                            .header("Sec-Fetch-Dest", "document")
                            .header("Sec-Fetch-Mode", "navigate")
                            .header("Sec-Fetch-Site", "none")
                            .header("Sec-Fetch-User", "?1")

                        val response = okHttpClient.newCall(requestBuilder.build()).execute()
                        okHttpResponse = response
                        finalUrl = response.request.url.toString()
                        responseCode = response.code
                        val responseBodyText = response.body?.string() ?: ""

                        val isResponseOkHttpFailure = response.code == 409 || response.code == 403
                        val refreshLoopReason = isRefreshLoopOrRedirect(responseBodyText)

                        if (isResponseOkHttpFailure || refreshLoopReason != null) {
                            val msg = refreshLoopReason ?: "HTTP error code ${response.code}"
                            Log.d(tag, "Bypass trigger: $msg. Activating Headless WebView Fallback for URL: $url")
                            try {
                                htmlContent = fetchHtmlWithHiddenWebView(applicationContext, url)
                                isFallbackUsed = true
                                finalUrl = url
                                responseCode = 200
                            } catch (fallbackEx: Exception) {
                                Log.e(tag, "Headless WebView Fallback failed for URL: $url", fallbackEx)
                                htmlContent = responseBodyText
                            }
                        } else {
                            htmlContent = responseBodyText
                        }
                    } catch (okhttpEx: Exception) {
                        Log.e(tag, "OkHttp network call failed for URL: $url. Retrying via Headless WebView Fallback.", okhttpEx)
                        try {
                            htmlContent = fetchHtmlWithHiddenWebView(applicationContext, url)
                            isFallbackUsed = true
                            finalUrl = url
                            responseCode = 200
                        } catch (fallbackEx: Exception) {
                            Log.e(tag, "Headless WebView Fallback retry also failed for URL: $url", fallbackEx)
                            throw okhttpEx
                        }
                    } finally {
                        try {
                            okHttpResponse?.close()
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }

                val document = Jsoup.parse(htmlContent)

                for (rule in dueRules) {
                    val wasRedirectedToLogin = if (url.contains("/login", ignoreCase = true) || url.contains("/signin", ignoreCase = true)) {
                        false
                    } else {
                        val isFinalUrlLogin = finalUrl.contains("/login", ignoreCase = true) || finalUrl.contains("/signin", ignoreCase = true)
                        val hasSelector = if (!rule.isTrackWholePage && !rule.cssSelector.isNullOrBlank()) {
                            document.select(rule.cssSelector).isNotEmpty()
                        } else {
                            true
                        }
                        isFinalUrlLogin && !hasSelector
                    }

                    val rCode = if (isFallbackUsed) 200 else responseCode
                    val isSessionExpired = rCode == 401 || rCode == 419 || wasRedirectedToLogin || (
                        !rule.isTrackWholePage && !rule.cssSelector.isNullOrBlank() && 
                        document.select(rule.cssSelector).isEmpty() && 
                        (htmlContent.contains("type=\"password\"", ignoreCase = true) || htmlContent.contains("name=\"password\"", ignoreCase = true))
                    )

                    if (isSessionExpired) {
                        Log.w(tag, "Session Expired detected for rule: ${rule.id} (URL: ${rule.url})")
                        dao.updateRule(
                            rule.copy(
                                isPaused = true,
                                lastKnownText = "Session Expired (Please login again)",
                                lastCheckedTimeMillis = currentTime
                            )
                        )
                        sendNotifications(
                            id = rule.id,
                            title = "Session Expired",
                            message = "Your active login session expired for: ${cleanUrlForDisplay(rule.url)}. Please open the browser and log in again."
                        )
                        continue
                    }

                    val isSuccessfulLoad = if (isFallbackUsed) htmlContent.isNotEmpty() else (rCode in 200..299)
                    if (!isSuccessfulLoad) {
                        Log.e(tag, "Scraping failed with non-200 code: $rCode")
                        dao.updateRule(
                            rule.copy(
                                lastCheckedTimeMillis = currentTime,
                                lastKnownText = "Error: Server returned code $rCode"
                            )
                        )
                        continue
                    }

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

                    if (newText.isEmpty() && !rule.isTrackWholePage && !rule.cssSelector.isNullOrBlank()) {
                        Log.w(tag, "Rule [${rule.id}]: Scraped empty text for selector: ${rule.cssSelector}. Site may be dynamic or loading. Preserving last known text: ${rule.lastKnownText}")
                        dao.updateRule(
                            rule.copy(
                                lastCheckedTimeMillis = currentTime
                            )
                        )
                        continue
                    }

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

                    // Extract pure plain text from both newText and lastKnownText before comparison
                    val plainOldText = Jsoup.parse(rule.lastKnownText).text().trim()
                    val plainNewText = Jsoup.parse(newText).text().trim()

                    if (plainNewText != plainOldText) {
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
                            // Path A: Standard User (with Readable Old Text to New Text formatting)
                            val oldPreview = if (plainOldText.length > 80) "${plainOldText.take(77)}..." else plainOldText
                            val newPreview = if (plainNewText.length > 80) "${plainNewText.take(77)}..." else plainNewText
                            val changeMsg = "Update: Changed from $oldPreview to $newPreview"

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
                for (rule in dueRules) {
                    try {
                        dao.updateRule(
                            rule.copy(
                                lastCheckedTimeMillis = currentTime,
                                lastKnownText = "Error: ${e.localizedMessage ?: "Connection failed"}"
                            )
                        )
                    } catch (dbEx: Exception) {
                        Log.e(tag, "Failed to update db error state for rule ${rule.id}", dbEx)
                    }
                }
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

    private fun isRefreshLoopOrRedirect(html: String): String? {
        val lower = html.lowercase()
        if (lower.contains("http-equiv=\"refresh\"") || lower.contains("http-equiv='refresh'")) {
            return "Meta-Refresh tag found"
        }
        val metaRefreshPattern = Regex("<meta\\s+[^>]*http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*>", RegexOption.IGNORE_CASE)
        if (metaRefreshPattern.containsMatchIn(html)) {
            return "Regex Meta-Refresh tag found"
        }
        if (lower.contains("window.location.") || lower.contains("location.replace(") || lower.contains("location.href=")) {
            return "Javascript location redirect script found"
        }
        return null
    }

    private fun unescapeJson(jsonStr: String): String {
        if (!jsonStr.startsWith("\"") || !jsonStr.endsWith("\"")) {
            return jsonStr
        }
        val sb = java.lang.StringBuilder()
        var i = 1
        val len = jsonStr.length - 1
        while (i < len) {
            val c = jsonStr[i]
            if (c == '\\') {
                i++
                if (i < len) {
                    val next = jsonStr[i]
                    when (next) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'u' -> {
                            if (i + 4 < len) {
                                val hexVal = jsonStr.substring(i + 1, i + 5)
                                try {
                                    val codeHex = hexVal.toInt(16)
                                    sb.append(codeHex.toChar())
                                } catch (e: Exception) {
                                    sb.append("\\u").append(hexVal)
                                }
                                i += 4
                            } else {
                                sb.append("\\u")
                            }
                        }
                        else -> sb.append(next)
                    }
                }
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private suspend fun fetchHtmlWithHiddenWebView(context: Context, url: String): String = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String>()
        
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            val storedUa = SettingsStorage(context).getUserAgent()
            settings.userAgentString = storedUa
        }
        
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        
        try {
            val savedCookieString = cookieManager.getCookie(url)
            if (!savedCookieString.isNullOrBlank()) {
                cookieManager.setCookie(url, savedCookieString)
            } else {
                val helperJar = com.example.data.PersistentCookieJar(context)
                val hUrl = url.toHttpUrlOrNull()
                if (hUrl != null) {
                    val cookiesList = helperJar.loadForRequest(hUrl)
                    for (cookie in cookiesList) {
                        cookieManager.setCookie(url, cookie.toString())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to restore cookies for headless load", e)
        }
        cookieManager.flush()
        
        var settlementTimer: java.util.Timer? = null
        
        val checkPageAction = Runnable {
            webView.evaluateJavascript("document.documentElement.outerHTML") { html ->
                var cleanHtml = html ?: ""
                cleanHtml = unescapeJson(cleanHtml)
                Log.d(tag, "Headless WebView fully settled. Extracted HTML length: ${cleanHtml.length}")
                if (!deferred.isCompleted) {
                    deferred.complete(cleanHtml)
                }
            }
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                super.onPageFinished(view, finishedUrl)
                Log.d(tag, "onPageFinished triggered for: $finishedUrl")
                
                 settlementTimer?.cancel()
                settlementTimer = java.util.Timer().apply {
                    schedule(object : java.util.TimerTask() {
                        override fun run() {
                            webView.post {
                                checkPageAction.run()
                            }
                        }
                    }, 5000)
                }
            }
            
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(tag, "Headless WebView error: Code $errorCode, $description")
            }
        }
        
        Log.d(tag, "Loading page in headless WebView: $url")
        webView.loadUrl(url)
        
        val timeoutTimer = java.util.Timer().apply {
            schedule(object : java.util.TimerTask() {
                override fun run() {
                    if (!deferred.isCompleted) {
                        Log.w(tag, "Headless WebView loading timeout exceeded. Resolving with current capture.")
                        webView.post {
                            webView.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                var cleanHtml = html ?: ""
                                cleanHtml = unescapeJson(cleanHtml)
                                deferred.complete(cleanHtml)
                            }
                        }
                    }
                }
            }, 25000)
        }
        
        val htmlResult = deferred.await()
        timeoutTimer.cancel()
        settlementTimer?.cancel()
        
        webView.stopLoading()
        webView.destroy()
        
        htmlResult
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
