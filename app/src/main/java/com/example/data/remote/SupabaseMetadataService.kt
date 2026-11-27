package com.example.data.remote

import com.example.data.model.Category
import com.example.data.model.FavoriteItem
import com.example.data.model.Movie
import com.example.data.model.WatchHistoryEntry
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Optional read-only Supabase PostgREST client for inspecting RLS-protected
 * catalogue & user watch-history tables when Supabase env variables are configured.
 *
 * SECURITY:
 * - Strictly read-only on user-visible tables protected by Supabase Row Level Security (RLS).
 * - Never exposes admin tables, service role keys, or Telegram media credentials.
 */
interface SupabaseMetadataService {

    @GET("rest/v1/categories")
    suspend fun fetchCategories(
        @Header("apikey") anonKey: String,
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "id,name,slug,sort_order",
        @Query("order") order: String = "sort_order.asc"
    ): Response<List<Category>>

    @GET("rest/v1/movies")
    suspend fun fetchMovies(
        @Header("apikey") anonKey: String,
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "id,title,description,thumbnail_url,poster_url,backdrop_url,category_id,release_year,duration_seconds,file_size_bytes,quality_label,maturity_rating,is_featured,is_trending,is_recently_added,created_at",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<Movie>>

    @GET("rest/v1/favorites")
    suspend fun fetchFavorites(
        @Header("apikey") anonKey: String,
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "id,user_id,movie_id,created_at"
    ): Response<List<FavoriteItem>>

    @GET("rest/v1/watch_history")
    suspend fun fetchWatchHistory(
        @Header("apikey") anonKey: String,
        @Header("Authorization") bearerAuth: String,
        @Query("select") select: String = "id,user_id,movie_id,position_ms,duration_ms,updated_at"
    ): Response<List<WatchHistoryEntry>>
}
