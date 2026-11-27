package com.example.data.model

import androidx.annotation.DrawableRes
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ---------------------------------------------------------------------------
// Core Database Data Models (Users, Access Codes, Movies, Categories, Favorites, Watch History)
// ---------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class UserAccount(
    val id: String,
    @Json(name = "display_name") val displayName: String? = null,
    val status: String = "active", // active, suspended, revoked
    @Json(name = "device_bound") val deviceBound: Boolean = true,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "last_seen_at") val lastSeenAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AccessCodeStatus(
    val id: String? = null,
    @Json(name = "masked_code") val maskedCode: String,
    val status: String = "active", // active, claimed, disabled, expired
    @Json(name = "claimed_at") val claimedAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "single_user_locked") val singleUserLocked: Boolean = true
)

@JsonClass(generateAdapter = true)
data class Category(
    val id: String,
    val name: String,
    val slug: String = "",
    @Json(name = "sort_order") val sortOrder: Int = 0
)

@JsonClass(generateAdapter = true)
data class Movie(
    val id: String,
    val title: String,
    val description: String? = null,
    /**
     * Automatically retrieved Telegram video thumbnail served through the authorized backend
     * (never a raw Telegram bot API URL containing a token).
     */
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "backdrop_url") val backdropUrl: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "category_name") val categoryName: String? = null,
    val categories: List<String> = emptyList(),
    @Json(name = "release_year") val releaseYear: Int? = null,
    @Json(name = "duration_seconds") val durationSeconds: Long = 0L,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long? = null,
    @Json(name = "quality_label") val qualityLabel: String = "HD",
    @Json(name = "maturity_rating") val maturityRating: String = "TV-MA",
    @Json(name = "is_featured") val isFeatured: Boolean = false,
    @Json(name = "is_trending") val isTrending: Boolean = false,
    @Json(name = "is_recently_added") val isRecentlyAdded: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "has_telegram_thumbnail") val hasTelegramThumbnail: Boolean = true,
    /**
     * Strictly non-null ONLY for offline UI Preview catalogue items.
     * Production backend items always keep this null and load remote thumbnails via Coil.
     */
    @Transient @DrawableRes val samplePosterRes: Int? = null,
    @Transient val isSamplePreview: Boolean = false
) {
    val bestRemoteThumbnail: String?
        get() = thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: posterUrl?.takeIf { it.isNotBlank() }
            ?: backdropUrl?.takeIf { it.isNotBlank() }

    val formattedDuration: String
        get() {
            if (durationSeconds <= 0L) return "Feature"
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }

    val formattedFileSizeMb: String?
        get() {
            val bytes = fileSizeBytes ?: return null
            if (bytes <= 0L) return null
            val mb = bytes / (1024.0 * 1024.0)
            return "${mb.toInt()} MB"
        }
}

@JsonClass(generateAdapter = true)
data class FavoriteItem(
    val id: String,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "movie_id") val movieId: String,
    @Json(name = "created_at") val createdAt: String? = null,
    val movie: Movie? = null
)

@JsonClass(generateAdapter = true)
data class WatchHistoryEntry(
    val id: String? = null,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "movie_id") val movieId: String,
    @Json(name = "position_ms") val positionMs: Long = 0L,
    @Json(name = "duration_ms") val durationMs: Long = 0L,
    @Json(name = "updated_at") val updatedAt: String? = null
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

// ---------------------------------------------------------------------------
// Backend API Request / Response Payloads
// ---------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class ActivateAccessCodeRequest(
    @Json(name = "access_code") val accessCode: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "platform") val platform: String = "android"
)

@JsonClass(generateAdapter = true)
data class AuthSessionResponse(
    val authenticated: Boolean = true,
    @Json(name = "session_token") val sessionToken: String? = null,
    val user: UserAccount,
    @Json(name = "access_code") val accessCode: AccessCodeStatus
)

@JsonClass(generateAdapter = true)
data class MoviesListResponse(
    val movies: List<Movie> = emptyList(),
    val featured: Movie? = null,
    val trending: List<Movie> = emptyList(),
    @Json(name = "recently_added") val recentlyAdded: List<Movie> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MovieDetailResponse(
    val movie: Movie,
    @Json(name = "is_favorite") val isFavorite: Boolean = false,
    @Json(name = "watch_history") val watchHistory: WatchHistoryEntry? = null
)

@JsonClass(generateAdapter = true)
data class CategoriesResponse(
    val categories: List<Category> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FavoritesResponse(
    val favorites: List<FavoriteItem> = emptyList(),
    val movies: List<Movie> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AddFavoriteRequest(
    @Json(name = "movie_id") val movieId: String
)

@JsonClass(generateAdapter = true)
data class WatchHistoryUpsertRequest(
    @Json(name = "movie_id") val movieId: String,
    @Json(name = "position_ms") val positionMs: Long,
    @Json(name = "duration_ms") val durationMs: Long
)

@JsonClass(generateAdapter = true)
data class StreamDescriptorResponse(
    @Json(name = "stream_url") val streamUrl: String? = null,
    @Json(name = "mime_type") val mimeType: String? = "video/mp4",
    @Json(name = "supports_range") val supportsRange: Boolean = true,
    @Json(name = "content_length") val contentLength: Long? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "auth_header_required") val authHeaderRequired: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ApiErrorResponse(
    val code: String? = null,
    val error: String? = null,
    val message: String? = null
)

// ---------------------------------------------------------------------------
// Application Mode & UI State Sealed Types
// ---------------------------------------------------------------------------

sealed interface AppSessionMode {
    data object Checking : AppSessionMode
    data object Unauthenticated : AppSessionMode
    data class Authenticated(
        val sessionToken: String,
        val user: UserAccount,
        val accessCode: AccessCodeStatus
    ) : AppSessionMode
    data object UiPreview : AppSessionMode
}

sealed interface ResolvedStream {
    data class Ready(
        val streamUrl: String,
        val mimeType: String,
        val requiresAuthHeader: Boolean,
        val supportsRangeRequests: Boolean
    ) : ResolvedStream

    data object PreviewModeBlocked : ResolvedStream

    data class Unavailable(val reason: String) : ResolvedStream
}

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(
        val message: String,
        val errorCode: String? = null,
        val isUnauthorized: Boolean = false
    ) : ApiResult<Nothing>()
}
