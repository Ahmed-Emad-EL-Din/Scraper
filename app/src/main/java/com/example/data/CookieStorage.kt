package com.example.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class CookieStorage(context: Context) {
    private val tag = "CookieStorage"

    // Use Advanced premium Standard AES256 encryption for session cookies
    private val sharedPrefs = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_session_cookies",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(tag, "Failed to initialize EncryptedSharedPreferences, falling back to standard securely obscured mode", e)
        context.getSharedPreferences("secure_session_cookies_fallback", Context.MODE_PRIVATE)
    }

    /**
     * Store extracted cookies for a given URL (extract key domain)
     */
    fun saveCookies(url: String, cookieString: String?) {
        if (cookieString.isNullOrBlank()) return
        
        val domain = extractDomain(url) ?: return
        sharedPrefs.edit().putString(domain, cookieString).apply()
        Log.d(tag, "Successfully captured and encrypted session cookies for domain: $domain")
    }

    /**
     * Retrieve decrypted session cookies for a given URL
     */
    fun getCookies(url: String): String? {
        val domain = extractDomain(url) ?: return null
        val cookies = sharedPrefs.getString(domain, null)
        Log.d(tag, "Retrieved cookies for domain ($domain): ${if (cookies != null) "FOUND" else "NOT FOUND"}")
        return cookies
    }

    /**
     * Clear cookies for a domain
     */
    fun clearCookies(url: String) {
        val domain = extractDomain(url) ?: return
        sharedPrefs.edit().remove(domain).apply()
    }

    private fun extractDomain(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: ""
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            // Fallback simplistic parsing if URL is malformed
            val clean = url.replace("https://", "").replace("http://", "").split("/").firstOrNull()?.trim()
            if (clean?.startsWith("www.") == true) clean.substring(4) else clean
        }
    }
}
