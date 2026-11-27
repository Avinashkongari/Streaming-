package com.example.data.remote

import com.example.data.model.ActivateAccessCodeRequest
import com.example.data.model.AddFavoriteRequest
import com.example.data.model.AuthSessionResponse
import com.example.data.model.CategoriesResponse
import com.example.data.model.FavoritesResponse
import com.example.data.model.MovieDetailResponse
import com.example.data.model.MoviesListResponse
import com.example.data.model.WatchHistoryEntry
import com.example.data.model.WatchHistoryUpsertRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Authorized Axion Stream User Backend API service layer.
 *
 * Strictly USER-ONLY endpoints — zero admin registration, metadata mutation,
 * or raw Telegram credentials are exposed to this application.
 */
interface AxionApiService {

    @POST("api/auth/activate")
    suspend fun activateAccessCode(
        @Body request: ActivateAccessCodeRequest
    ): Response<AuthSessionResponse>

    @GET("api/auth/session")
    suspend fun getSession(): Response<AuthSessionResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/movies")
    suspend fun getMovies(
        @Query("category_id") categoryId: String? = null,
        @Query("q") searchQuery: String? = null
    ): Response<MoviesListResponse>

    @GET("api/movies/{id}")
    suspend fun getMovieDetails(
        @Path("id") movieId: String
    ): Response<MovieDetailResponse>

    /**
     * Resolves or probes the authorized Telegram progressive streaming endpoint.
     * Supports both:
     * 1) JSON descriptor responses returning a signed HTTPS stream URL with Range support, OR
     * 2) Direct HTTP 206 Partial Content video streaming from /api/movies/:id/stream.
     */
    @GET("api/movies/{id}/stream")
    suspend fun resolveMovieStream(
        @Path("id") movieId: String,
        @Header("Range") probeRange: String = "bytes=0-1",
        @Header("Accept") accept: String = "application/json, video/*;q=0.9"
    ): Response<ResponseBody>

    @GET("api/categories")
    suspend fun getCategories(): Response<CategoriesResponse>

    @GET("api/user/favorites")
    suspend fun getFavorites(): Response<FavoritesResponse>

    @POST("api/user/favorites")
    suspend fun addFavorite(
        @Body request: AddFavoriteRequest
    ): Response<FavoritesResponse>

    @DELETE("api/user/favorites/{id}")
    suspend fun removeFavorite(
        @Path("id") movieId: String
    ): Response<Unit>

    @GET("api/user/watch-history")
    suspend fun getWatchHistory(): Response<List<WatchHistoryEntry>>

    @POST("api/user/watch-history")
    suspend fun upsertWatchHistory(
        @Body request: WatchHistoryUpsertRequest
    ): Response<WatchHistoryEntry>
}
