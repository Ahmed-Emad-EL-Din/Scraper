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
