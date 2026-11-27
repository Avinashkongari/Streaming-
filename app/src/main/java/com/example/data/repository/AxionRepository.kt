package com.example.data.repository

import com.example.data.config.ApiConfig
import com.example.data.model.ActivateAccessCodeRequest
import com.example.data.model.AddFavoriteRequest
import com.example.data.model.ApiErrorResponse
import com.example.data.model.ApiResult
import com.example.data.model.AuthSessionResponse
import com.example.data.model.Category
import com.example.data.model.Movie
import com.example.data.model.MovieDetailResponse
import com.example.data.model.MoviesListResponse
import com.example.data.model.ResolvedStream
import com.example.data.model.StreamDescriptorResponse
import com.example.data.model.WatchHistoryEntry
import com.example.data.model.WatchHistoryUpsertRequest
import com.example.data.remote.NetworkClientFactory
import com.example.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/**
 * Production backend repository for Axion Stream.
 *
 * Strictly executes real backend requests — NEVER simulates authentication
 * and NEVER fabricates Telegram video streams when the backend is unavailable.
 */
class AxionRepository(
    private val networkFactory: NetworkClientFactory,
    private val sessionManager: SessionManager
) {
    private val api = networkFactory.apiService
    private val errorAdapter = networkFactory.moshi.adapter(ApiErrorResponse::class.java)
    private val streamDescriptorAdapter = networkFactory.moshi.adapter(StreamDescriptorResponse::class.java)

    suspend fun restoreVerifiedSession(): ApiResult<AuthSessionResponse?> = withContext(Dispatchers.IO) {
        val token = sessionManager.loadSessionToken()
        if (token.isNullOrBlank()) {
            return@withContext ApiResult.Success(null)
        }
        if (!ApiConfig.isBackendConfigured) {
            return@withContext ApiResult.Failure(
                message = "Saved session cannot be verified because AXION_API_BASE_URL is not configured."
            )
        }
        try {
            val response = api.getSession()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.authenticated) {
                    val refreshedToken = body.sessionToken?.takeIf { it.isNotBlank() } ?: token
                    sessionManager.saveSessionToken(refreshedToken)
                    ApiResult.Success(body.copy(sessionToken = refreshedToken))
                } else {
                    sessionManager.clearSession()
                    ApiResult.Success(null)
                }
            } else if (response.code() == 401 || response.code() == 403) {
                sessionManager.clearSession()
                ApiResult.Success(null)
            } else {
                parseFailure(response)
            }
        } catch (e: IOException) {
            ApiResult.Failure("Unable to reach the Axion Stream backend to verify your session. Check your connection.")
        } catch (e: Exception) {
            ApiResult.Failure("Session verification failed: ${e.localizedMessage ?: "Unexpected error"}")
        }
    }

    suspend fun activateAccessCode(rawCode: String): ApiResult<AuthSessionResponse> = withContext(Dispatchers.IO) {
        val cleaned = rawCode.trim().uppercase()
        if (cleaned.isEmpty()) {
            return@withContext ApiResult.Failure("Please enter your access code.", errorCode = "EMPTY_CODE")
        }
        if (!ApiConfig.isBackendConfigured) {
            return@withContext ApiResult.Failure(
                message = "Streaming backend URL is not configured. Set AXION_API_BASE_URL in AI Studio Secrets before signing in, or use UI Preview to explore the interface.",
                errorCode = "BACKEND_UNCONFIGURED"
            )
        }
        try {
            val deviceId = sessionManager.getOrCreateDeviceId()
            val response = api.activateAccessCode(
                ActivateAccessCodeRequest(
                    accessCode = cleaned,
                    deviceId = deviceId
                )
            )
            if (response.isSuccessful) {
                val body = response.body()
                val token = body?.sessionToken
                if (body != null && !token.isNullOrBlank()) {
                    sessionManager.saveSessionToken(token)
                    ApiResult.Success(body)
                } else {
                    ApiResult.Failure("Backend returned an incomplete session response.")
                }
            } else {
                parseAuthFailure(response)
            }
        } catch (e: IOException) {
            ApiResult.Failure("Cannot connect to the Axion Stream backend. Verify the server is online and try again.")
        } catch (e: Exception) {
            ApiResult.Failure("Activation request failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun logout(): ApiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            if (ApiConfig.isBackendConfigured && !sessionManager.currentTokenSync().isNullOrBlank()) {
                runCatching { api.logout() }
            }
        } finally {
            sessionManager.clearSession()
        }
        ApiResult.Success(Unit)
    }

    suspend fun fetchMovies(
        categoryId: String? = null,
        searchQuery: String? = null
    ): ApiResult<MoviesListResponse> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.getMovies(
                categoryId = categoryId?.takeIf { it.isNotBlank() },
                searchQuery = searchQuery?.takeIf { it.isNotBlank() }
            )
            if (response.isSuccessful) {
                val body = response.body() ?: MoviesListResponse()
                ApiResult.Success(
                    body.copy(
                        movies = body.movies.map(::normalizeMovieUrls),
                        featured = body.featured?.let(::normalizeMovieUrls),
                        trending = body.trending.map(::normalizeMovieUrls),
                        recentlyAdded = body.recentlyAdded.map(::normalizeMovieUrls)
                    )
                )
            } else {
                parseFailure(response)
            }
        }
    }

    suspend fun fetchMovieDetails(movieId: String): ApiResult<MovieDetailResponse> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.getMovieDetails(movieId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body.copy(movie = normalizeMovieUrls(body.movie)))
                } else {
                    ApiResult.Failure("Movie details could not be loaded.")
                }
            } else {
                parseFailure(response)
            }
        }
    }

    suspend fun fetchCategories(): ApiResult<List<Category>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.getCategories()
            if (response.isSuccessful) {
                ApiResult.Success(response.body()?.categories.orEmpty().sortedBy { it.sortOrder })
            } else {
                parseFailure(response)
            }
        }
    }

    suspend fun fetchFavorites(): ApiResult<Set<String>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.getFavorites()
            if (response.isSuccessful) {
                val body = response.body()
                val ids = buildSet {
                    body?.favorites?.forEach { add(it.movieId) }
                    body?.movies?.forEach { add(it.id) }
                }
                ApiResult.Success(ids)
            } else {
                parseFailure(response)
            }
        }
    }

    suspend fun addFavorite(movieId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.addFavorite(AddFavoriteRequest(movieId = movieId))
            if (response.isSuccessful) ApiResult.Success(Unit) else parseFailure(response)
        }
    }

    suspend fun removeFavorite(movieId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.removeFavorite(movieId)
            if (response.isSuccessful) ApiResult.Success(Unit) else parseFailure(response)
        }
    }

    suspend fun syncWatchHistory(movieId: String, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        sessionManager.saveWatchPosition(movieId, positionMs, durationMs)
        if (ApiConfig.isBackendConfigured && !sessionManager.currentTokenSync().isNullOrBlank()) {
            runCatching {
                api.upsertWatchHistory(
                    WatchHistoryUpsertRequest(
                        movieId = movieId,
                        positionMs = positionMs,
                        durationMs = durationMs
                    )
                )
            }
        }
    }

    suspend fun loadWatchHistory(): Map<String, WatchHistoryEntry> = withContext(Dispatchers.IO) {
        val local = sessionManager.loadAllWatchHistory().toMutableMap()
        if (ApiConfig.isBackendConfigured && !sessionManager.currentTokenSync().isNullOrBlank()) {
            runCatching {
                val response = api.getWatchHistory()
                if (response.isSuccessful) {
                    response.body().orEmpty().forEach { remoteEntry ->
                        val existing = local[remoteEntry.movieId]
                        if (existing == null || remoteEntry.positionMs > existing.positionMs) {
                            local[remoteEntry.movieId] = remoteEntry
                        }
                    }
                }
            }
        }
        local
    }

    /**
     * Resolves an authorized progressive stream endpoint via GET /api/movies/:id/stream.
     *
     * Supports two common secure backend implementations:
     * 1) JSON descriptor returning `{ stream_url, mime_type, supports_range, auth_header_required }`
     * 2) Direct HTTP 206 Partial Content / 200 video stream directly at `/api/movies/:id/stream`
     *
     * NEVER fakes playback if the backend is offline or rejects authorization.
     */
    suspend fun resolveStream(movie: Movie): ResolvedStream = withContext(Dispatchers.IO) {
        if (movie.isSamplePreview) {
            return@withContext ResolvedStream.PreviewModeBlocked
        }
        if (!ApiConfig.isBackendConfigured) {
            return@withContext ResolvedStream.Unavailable(
                "Backend streaming URL is not configured. Set AXION_API_BASE_URL in Secrets."
            )
        }
        if (sessionManager.currentTokenSync().isNullOrBlank()) {
            return@withContext ResolvedStream.Unavailable(
                "Your session has expired. Please sign in again with your access code."
            )
        }

        val directEndpoint = ApiConfig.resolveBackendUrl("/api/movies/${movie.id}/stream")
            ?: return@withContext ResolvedStream.Unavailable(
                "Invalid HTTPS stream endpoint configuration."
            )

        try {
            val response = api.resolveMovieStream(movieId = movie.id)
            val contentType = response.headers()["Content-Type"].orEmpty().lowercase()
            val acceptRanges = response.headers()["Accept-Ranges"].orEmpty().lowercase()

            when {
                response.code() == 401 -> {
                    sessionManager.clearSession()
                    ResolvedStream.Unavailable("Session unauthorized or expired. Please sign in again.")
                }
                response.code() == 403 -> {
                    ResolvedStream.Unavailable("Access denied for this title. Your access code may be disabled or expired.")
                }
                response.code() == 404 -> {
                    ResolvedStream.Unavailable("This video stream was not found on the authorized Telegram media backend.")
                }
                response.isSuccessful && contentType.contains("application/json") -> {
                    val rawJson = response.body()?.string().orEmpty()
                    val descriptor = runCatching { streamDescriptorAdapter.fromJson(rawJson) }.getOrNull()
                    val resolvedUrl = descriptor?.streamUrl?.let { ApiConfig.resolveBackendUrl(it) }
                    if (resolvedUrl != null) {
                        ResolvedStream.Ready(
                            streamUrl = resolvedUrl,
                            mimeType = descriptor.mimeType ?: "video/mp4",
                            requiresAuthHeader = descriptor.authHeaderRequired,
                            supportsRangeRequests = descriptor.supportsRange
                        )
                    } else {
                        ResolvedStream.Unavailable(
                            "Backend returned an invalid or non-HTTPS stream descriptor."
                        )
                    }
                }
                response.code() == 206 || (response.isSuccessful && contentType.startsWith("video/")) -> {
                    response.body()?.close()
                    ResolvedStream.Ready(
                        streamUrl = directEndpoint,
                        mimeType = contentType.substringBefore(';').ifBlank { "video/mp4" },
                        requiresAuthHeader = true,
                        supportsRangeRequests = response.code() == 206 || acceptRanges.contains("bytes")
                    )
                }
                else -> {
                    val failure = parseFailure(response)
                    ResolvedStream.Unavailable(failure.message)
                }
            }
        } catch (e: IOException) {
            ResolvedStream.Unavailable(
                "Streaming backend is unreachable. Verify your network connection and backend server status."
            )
        } catch (e: Exception) {
            ResolvedStream.Unavailable(
                "Unable to initialize authorized stream: ${e.localizedMessage ?: "Unexpected error"}"
            )
        }
    }

    private fun normalizeMovieUrls(movie: Movie): Movie {
        return movie.copy(
            thumbnailUrl = movie.thumbnailUrl?.let { ApiConfig.resolveBackendUrl(it) },
            posterUrl = movie.posterUrl?.let { ApiConfig.resolveBackendUrl(it) },
            backdropUrl = movie.backdropUrl?.let { ApiConfig.resolveBackendUrl(it) }
        )
    }

    private suspend fun <T> safeApiCall(block: suspend () -> ApiResult<T>): ApiResult<T> {
        if (!ApiConfig.isBackendConfigured) {
            return ApiResult.Failure("Backend API URL is not configured.")
        }
        return try {
            block()
        } catch (e: IOException) {
            ApiResult.Failure("Network connection error. Unable to reach Axion Stream backend.")
        } catch (e: Exception) {
            ApiResult.Failure(e.localizedMessage ?: "Unexpected network error.")
        }
    }

    private fun parseAuthFailure(response: Response<*>): ApiResult.Failure {
        val parsed = parseErrorBody(response)
        val code = parsed?.code?.uppercase()
        val message = when {
            code == "INVALID_CODE" || response.code() == 404 ->
                "Invalid access code. Double-check your invitation code and try again."
            code == "CODE_ALREADY_CLAIMED" || response.code() == 409 ->
                "This access code has already been claimed by another user. Each code belongs to a single account."
            code == "CODE_DISABLED" ->
                "This access code has been disabled by the administrator."
            code == "CODE_EXPIRED" ->
                "This access code has expired and can no longer be activated."
            response.code() == 403 ->
                parsed?.message ?: "Access code is disabled, expired, or unauthorized."
            else ->
                parsed?.message ?: parsed?.error ?: "Activation failed (HTTP ${response.code()})."
        }
        return ApiResult.Failure(
            message = message,
            errorCode = code,
            isUnauthorized = response.code() == 401
        )
    }

    private fun parseFailure(response: Response<*>): ApiResult.Failure {
        val parsed = parseErrorBody(response)
        val unauthorized = response.code() == 401
        val fallback = when (response.code()) {
            401 -> "Your session expired or was revoked. Please sign in again."
            403 -> "Access denied for this resource."
            404 -> "The requested item was not found."
            503 -> "Streaming service is temporarily unavailable. Please retry."
            else -> "Request failed (HTTP ${response.code()})."
        }
        return ApiResult.Failure(
            message = parsed?.message ?: parsed?.error ?: fallback,
            errorCode = parsed?.code,
            isUnauthorized = unauthorized
        )
    }

    private fun parseErrorBody(response: Response<*>): ApiErrorResponse? {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull() ?: return null
        return runCatching { errorAdapter.fromJson(raw) }.getOrNull()
    }
}
