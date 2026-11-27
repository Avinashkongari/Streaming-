package com.example.data.config

import com.example.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Runtime configuration sourced strictly from Secrets / BuildConfig (.env / .env.example).
 *
 * SECURITY:
 * - Only public API base URLs and RLS-protected Supabase anon config exist on the client.
 * - Telegram bot tokens, API IDs, API hashes, session strings, and private channel IDs
 *   MUST NEVER exist in this Android client application.
 */
object ApiConfig {

    private const val PLACEHOLDER_API_URL = "https://api.axion-stream.example.com"
    private const val PLACEHOLDER_SUPABASE_URL = "https://your-project.supabase.co"
    private const val PLACEHOLDER_SUPABASE_KEY = "YOUR_SUPABASE_ANON_KEY"

    val rawApiBaseUrl: String
        get() = BuildConfig.AXION_API_BASE_URL.trim()

    val normalizedApiBaseUrl: String
        get() {
            val trimmed = rawApiBaseUrl.trimEnd('/')
            return if (trimmed.isEmpty()) "$PLACEHOLDER_API_URL/" else "$trimmed/"
        }

    /**
     * Returns true only when a valid HTTPS non-placeholder backend URL has been configured.
     */
    val isBackendConfigured: Boolean
        get() {
            val candidate = rawApiBaseUrl.trimEnd('/')
            if (candidate.isEmpty() || candidate.equals(PLACEHOLDER_API_URL, ignoreCase = true)) {
                return false
            }
            val parsed = candidate.toHttpUrlOrNull() ?: return false
            return parsed.isHttps
        }

    val backendHost: String?
        get() = rawApiBaseUrl.toHttpUrlOrNull()?.host

    val supabaseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim()

    val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isSupabaseConfigured: Boolean
        get() {
            val url = supabaseUrl.trimEnd('/')
            if (url.isEmpty() || url.equals(PLACEHOLDER_SUPABASE_URL, ignoreCase = true)) {
                return false
            }
            if (supabaseAnonKey.isEmpty() || supabaseAnonKey == PLACEHOLDER_SUPABASE_KEY) {
                return false
            }
            val parsed = url.toHttpUrlOrNull() ?: return false
            return parsed.isHttps
        }

    /**
     * Validates that a stream or thumbnail URL uses HTTPS before passing it to Media3 or Coil.
     */
    fun isSafeMediaUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.isHttps
    }

    /**
     * Resolves a relative backend path (e.g. "/api/movies/123/stream") against the configured API base URL.
     */
    fun resolveBackendUrl(pathOrUrl: String): String? {
        if (pathOrUrl.startsWith("https://", ignoreCase = true)) {
            return if (isSafeMediaUrl(pathOrUrl)) pathOrUrl else null
        }
        val base = rawApiBaseUrl.trimEnd('/')
        if (base.isEmpty()) return null
        val normalizedPath = if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"
        val combined = "$base$normalizedPath"
        return if (isSafeMediaUrl(combined)) combined else null
    }
}
