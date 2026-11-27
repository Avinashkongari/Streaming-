package com.example.data.remote

import android.content.Context
import coil.ImageLoader
import com.example.BuildConfig
import com.example.data.config.ApiConfig
import com.example.data.session.SessionManager
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds a single shared OkHttpClient (recommended by Media3 and Coil) with:
 * - Automatic Bearer session injection ONLY for the configured HTTPS backend host
 * - Redacted HTTP logging (never leaks Authorization or Cookie headers)
 * - Clean timeouts suited for progressive Range streaming up to ~200 MB
 */
class NetworkClientFactory(
    context: Context,
    private val sessionManager: SessionManager
) {
    val moshi: Moshi = Moshi.Builder().build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = sessionManager.currentTokenSync()
        val expectedHost = ApiConfig.backendHost

        val shouldAttachAuth = !token.isNullOrBlank() &&
            original.url.isHttps &&
            !expectedHost.isNullOrBlank() &&
            original.url.host.equals(expectedHost, ignoreCase = true)

        val request = if (shouldAttachAuth && original.header("Authorization") == null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("X-Axion-Client", "android")
                .build()
        } else {
            original.newBuilder()
                .header("X-Axion-Client", "android")
                .build()
        }
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("apikey")
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val apiService: AxionApiService = Retrofit.Builder()
        .baseUrl(ApiConfig.normalizedApiBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(AxionApiService::class.java)

    val supabaseService: SupabaseMetadataService? = if (ApiConfig.isSupabaseConfigured) {
        Retrofit.Builder()
            .baseUrl(ApiConfig.supabaseUrl.trimEnd('/') + "/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseMetadataService::class.java)
    } else {
        null
    }

    /**
     * Authenticated Coil ImageLoader so backend-proxied Telegram thumbnails
     * receive the Bearer session token automatically.
     */
    val imageLoader: ImageLoader = ImageLoader.Builder(context.applicationContext)
        .okHttpClient(okHttpClient)
        .crossfade(true)
        .build()
}
