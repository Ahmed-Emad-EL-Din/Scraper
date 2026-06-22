package com.example.data

import android.content.Context
import android.webkit.CookieManager
import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class PersistentCookieJar(context: Context) : CookieJar {
    private val tag = "PersistentCookieJar"
    private val database = AppDatabase.getDatabase(context.applicationContext)
    private val cookieDao = database.persistedCookieDao()
    private val webViewCookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        Log.d(tag, "saveFromResponse: Saving ${cookies.size} cookies for URL: $url")
        
        // 1. Sync to WebView's system CookieManager so the logged-in browser session remains active
        try {
            val urlString = url.toString()
            for (cookie in cookies) {
                webViewCookieManager.setCookie(urlString, cookie.toString())
            }
            webViewCookieManager.flush()
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync cookies to WebView CookieManager", e)
        }

        // 2. Persist to Room Database for offline and background worker session renewal
        val persistedList = cookies.map { cookie ->
            PersistedCookie(
                id = "${cookie.domain}|${cookie.name}",
                domain = cookie.domain,
                name = cookie.name,
                value = cookie.value,
                url = url.toString(),
                path = cookie.path,
                expiresAt = cookie.expiresAt,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly
            )
        }
        
        try {
            runBlocking {
                cookieDao.insertCookies(persistedList)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to insert cookies into Room database", e)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        Log.d(tag, "loadForRequest: Loading cookies for URL: $url")
        val cookiesMap = mutableMapOf<String, Cookie>()

        // 1. Load from Room Database for this domain with permissive matching
        try {
            val host = url.host
            val dbCookies = runBlocking {
                cookieDao.getAllCookies()
            }
            
            for (dbCookie in dbCookies) {
                try {
                    val isApplicable = domainMatches(dbCookie.domain, host)
                    
                    if (isApplicable) {
                        val cookie = Cookie.Builder()
                            .name(dbCookie.name)
                            .value(dbCookie.value)
                            .domain(host) // Force exact host
                            .path("/") // Force root path so any subpath applies
                            .expiresAt(dbCookie.expiresAt)
                            .apply {
                                if (dbCookie.secure) secure()
                            }
                            .build()
                        cookiesMap[cookie.name] = cookie
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing cookie ${dbCookie.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load cookies from Room Database", e)
        }

        // 2. Load from system CookieManager to merge/update
        try {
            val urlString = url.toString()
            val cookieManagerString = webViewCookieManager.getCookie(urlString)
            if (!cookieManagerString.isNullOrBlank()) {
                val systemCookies = parseCookieString(url, cookieManagerString)
                for (cookie in systemCookies) {
                    cookiesMap[cookie.name] = cookie
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load cookies from WebView CookieManager", e)
        }

        val finalCookies = cookiesMap.values.toList()
        Log.d(tag, "loadForRequest: Returning ${finalCookies.size} cookies for URL: $url")
        return finalCookies
    }

    private fun domainMatches(cookieDomain: String, requestHost: String): Boolean {
        val cleanCookie = cookieDomain.removePrefix(".").lowercase()
        val cleanHost = requestHost.lowercase()
        
        if (cleanCookie == cleanHost) return true
        
        // Allow subdomains, e.g. www.goldprice.org and goldprice.org
        if (cleanHost.endsWith(".$cleanCookie")) return true
        if (cleanCookie.endsWith(".$cleanHost")) return true
        
        return false
    }

    fun parseCookieString(url: HttpUrl, cookieString: String): List<Cookie> {
        val list = mutableListOf<Cookie>()
        val pairs = cookieString.split(";")
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.isEmpty()) continue
            val index = trimmed.indexOf('=')
            if (index == -1) continue
            val name = trimmed.substring(0, index).trim()
            val value = trimmed.substring(index + 1).trim()
            try {
                // Determine appropriate domain string for this cookie
                val cookieHost = url.host
                val cookie = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(cookieHost)
                    .build()
                list.add(cookie)
            } catch (e: Exception) {
                Log.e(tag, "Error parsing cookie string segment: $trimmed", e)
            }
        }
        return list
    }

    companion object {
        fun saveWebViewCookiesToDb(context: Context, url: String, cookieString: String?) {
            if (cookieString.isNullOrBlank()) return
            val httpUrl = url.toHttpUrlOrNull() ?: return
            val jar = PersistentCookieJar(context)
            val cookies = jar.parseCookieString(httpUrl, cookieString)
            jar.saveFromResponse(httpUrl, cookies)
        }
    }
}

class RealTimeWebViewCookieJar : CookieJar {
    private val webViewCookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        try {
            val urlString = url.toString()
            for (cookie in cookies) {
                webViewCookieManager.setCookie(urlString, cookie.toString())
            }
            webViewCookieManager.flush()
        } catch (e: Exception) {
            Log.e("RealTimeCookieJar", "Failed to save cookies to WebView CookieManager", e)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookiesList = mutableListOf<Cookie>()
        try {
            val urlString = url.toString()
            val cookieString = webViewCookieManager.getCookie(urlString)
            if (!cookieString.isNullOrBlank()) {
                val pairs = cookieString.split(";")
                for (pair in pairs) {
                    val trimmed = pair.trim()
                    if (trimmed.isEmpty()) continue
                    val index = trimmed.indexOf('=')
                    if (index == -1) continue
                    val name = trimmed.substring(0, index).trim()
                    val value = trimmed.substring(index + 1).trim()
                    try {
                        val cookie = Cookie.Builder()
                            .name(name)
                            .value(value)
                            .domain(url.host)
                            .build()
                        cookiesList.add(cookie)
                    } catch (e: Exception) {
                        Log.e("RealTimeCookieJar", "Failed to parse individual cookie segment: $trimmed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RealTimeCookieJar", "Failed to load cookies from real-time CookieManager", e)
        }
        return cookiesList
    }
}

class Http419Interceptor : okhttp3.Interceptor {
    companion object {
        // Thread-safe cache for CSRF tokens by domain/base URL
        val csrfTokensCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    }

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()
        val url = originalRequest.url
        val urlString = url.toString()
        val host = url.host
        val scheme = url.scheme
        val port = url.port
        
        // Generate Deep Headers
        // Origin: Base URL of the website
        val originStr = if ((port == 80 && scheme == "http") || (port == 443 && scheme == "https") || port == -1) {
            "$scheme://$host"
        } else {
            "$scheme://$host:$port"
        }
        val refererStr = urlString
        
        val updatedRequestBuilder = originalRequest.newBuilder()
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", originStr)
            .header("Referer", refererStr)
            
        // If we have a cached static or dynamic CSRF token for this host, append it!
        val cachedToken = csrfTokensCache[host]
        if (cachedToken != null) {
            updatedRequestBuilder.header("X-CSRF-TOKEN", cachedToken)
            Log.d("Interceptor419", "Appended cached X-CSRF-TOKEN: $cachedToken")
        }
            
        val request = updatedRequestBuilder.build()
        val response = chain.proceed(request)
        
        if (response.code == 419) {
            val tag = "Interceptor419"
            val cookiesHeader = request.header("Cookie") ?: "No Cookie Header"
            val webViewCookies = CookieManager.getInstance().getCookie(urlString) ?: "No WebView Cookies"
            
            // Peak/peek Response body safely
            val responseBody = response.peekBody(1024 * 1024) // 1MB max for safety
            val html = responseBody.string()
            
            Log.e(tag, """
                |--- EXTREME 419 ERROR LOG ---
                |URL: $urlString
                |Request Headers:
                |${request.headers}
                |Cookies Injected in Request Header: $cookiesHeader
                |Cookies present in CookieManager: $webViewCookies
                |Response Code: 419
                |Response Body (HTML):
                |$html
                |-----------------------------
            """.trimMargin())
        } else {
            // Passive HTML parsing to hunt for standard CSRF meta tags
            val contentType = response.body?.contentType()
            if (contentType != null && (contentType.toString().contains("text/html") || contentType.subtype.contains("html"))) {
                try {
                    val responseBody = response.peekBody(512 * 1024) // peek first 512KB for meta tags
                    val html = responseBody.string()
                    val doc = org.jsoup.Jsoup.parse(html)
                    val metaElement = doc.select("meta[name=csrf-token]").first()
                    if (metaElement != null) {
                        val token = metaElement.attr("content")
                        if (token.isNotEmpty()) {
                            csrfTokensCache[host] = token
                            Log.d("Interceptor419", "Dynamically discovered & cached CSRF token for host $host: $token")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Interceptor419", "Failed to parse HTML for CSRF token discovery", e)
                }
            }
        }
        
        return response
    }
}
