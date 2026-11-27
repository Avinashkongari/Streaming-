package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.data.model.ApiResult
import com.example.data.model.AppSessionMode
import com.example.data.model.Category
import com.example.data.model.Movie
import com.example.data.model.ResolvedStream
import com.example.data.model.WatchHistoryEntry
import com.example.data.remote.NetworkClientFactory
import com.example.data.repository.AxionRepository
import com.example.data.sample.SampleCatalogueData
import com.example.data.session.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class LoginUiState(
    val accessCodeInput: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

data class CatalogueUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val movies: List<Movie> = emptyList(),
    val featuredMovie: Movie? = null,
    val trendingMovies: List<Movie> = emptyList(),
    val recentlyAddedMovies: List<Movie> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val favoriteIds: Set<String> = emptySet(),
    val watchHistory: Map<String, WatchHistoryEntry> = emptyMap()
) {
    val favoriteMovies: List<Movie>
        get() = movies.filter { it.id in favoriteIds }

    val continueWatchingMovies: List<Movie>
        get() = movies.filter { movie ->
            val entry = watchHistory[movie.id]
            entry != null && entry.positionMs > 5_000L && entry.progressFraction < 0.96f
        }.sortedByDescending { watchHistory[it.id]?.positionMs ?: 0L }
}

data class SearchUiState(
    val query: String = "",
    val selectedCategoryId: String? = null,
    val isSearching: Boolean = false,
    val results: List<Movie> = emptyList(),
    val errorMessage: String? = null
)

data class MovieDetailUiState(
    val isLoading: Boolean = false,
    val movie: Movie? = null,
    val errorMessage: String? = null
)

data class StreamLoadUiState(
    val isResolving: Boolean = false,
    val resolvedStream: ResolvedStream? = null
)

class AxionAppViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application.applicationContext)
    private val networkFactory = NetworkClientFactory(application.applicationContext, sessionManager)
    private val repository = AxionRepository(networkFactory, sessionManager)

    val okHttpClient: OkHttpClient
        get() = networkFactory.okHttpClient

    val imageLoader: ImageLoader
        get() = networkFactory.imageLoader

    private val _sessionMode = MutableStateFlow<AppSessionMode>(AppSessionMode.Checking)
    val sessionMode: StateFlow<AppSessionMode> = _sessionMode.asStateFlow()

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _catalogueState = MutableStateFlow(CatalogueUiState())
    val catalogueState: StateFlow<CatalogueUiState> = _catalogueState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _detailState = MutableStateFlow(MovieDetailUiState())
    val detailState: StateFlow<MovieDetailUiState> = _detailState.asStateFlow()

    private val _streamState = MutableStateFlow(StreamLoadUiState())
    val streamState: StateFlow<StreamLoadUiState> = _streamState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var searchJob: Job? = null

    init {
        verifyExistingSession()
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    private fun verifyExistingSession() {
        viewModelScope.launch {
            _sessionMode.value = AppSessionMode.Checking
            when (val result = repository.restoreVerifiedSession()) {
                is ApiResult.Success -> {
                    val session = result.data
                    if (session != null && !session.sessionToken.isNullOrBlank()) {
                        _sessionMode.value = AppSessionMode.Authenticated(
                            sessionToken = session.sessionToken,
                            user = session.user,
                            accessCode = session.accessCode
                        )
                        loadProductionCatalogue()
                    } else {
                        _sessionMode.value = AppSessionMode.Unauthenticated
                    }
                }
                is ApiResult.Failure -> {
                    _loginState.update { it.copy(errorMessage = result.message) }
                    _sessionMode.value = AppSessionMode.Unauthenticated
                }
            }
        }
    }

    fun updateAccessCodeInput(value: String) {
        val sanitized = value.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(32)
        _loginState.update { it.copy(accessCodeInput = sanitized, errorMessage = null) }
    }

    fun submitAccessCode() {
        val code = _loginState.value.accessCodeInput.trim()
        if (code.isEmpty()) {
            _loginState.update { it.copy(errorMessage = "Please enter your access code.") }
            return
        }
        viewModelScope.launch {
            _loginState.update { it.copy(isSubmitting = true, errorMessage = null) }
            when (val result = repository.activateAccessCode(code)) {
                is ApiResult.Success -> {
                    val data = result.data
                    val token = data.sessionToken.orEmpty()
                    _loginState.value = LoginUiState()
                    _sessionMode.value = AppSessionMode.Authenticated(
                        sessionToken = token,
                        user = data.user,
                        accessCode = data.accessCode
                    )
                    loadProductionCatalogue()
                }
                is ApiResult.Failure -> {
                    _loginState.update {
                        it.copy(isSubmitting = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    /**
     * Enters strictly isolated UI Preview Mode using offline sample catalogue artwork.
     * Does NOT authenticate or simulate backend/Telegram streaming.
     */
    fun enterUiPreviewMode() {
        _loginState.update { it.copy(errorMessage = null, isSubmitting = false) }
        _sessionMode.value = AppSessionMode.UiPreview
        val sampleMovies = SampleCatalogueData.movies
        val sampleCategories = SampleCatalogueData.categories
        _catalogueState.value = CatalogueUiState(
            isLoading = false,
            errorMessage = null,
            movies = sampleMovies,
            featuredMovie = sampleMovies.firstOrNull { it.isFeatured } ?: sampleMovies.firstOrNull(),
            trendingMovies = sampleMovies.filter { it.isTrending },
            recentlyAddedMovies = sampleMovies.filter { it.isRecentlyAdded },
            categories = sampleCategories,
            selectedCategoryId = null,
            favoriteIds = setOf("sample-chronos-protocol", "sample-solaris-eclipse"),
            watchHistory = mapOf(
                "sample-chronos-protocol" to WatchHistoryEntry(
                    movieId = "sample-chronos-protocol",
                    positionMs = 2_240_000L,
                    durationMs = 7_380_000L
                )
            )
        )
        _searchState.value = SearchUiState(results = sampleMovies)
    }

    fun logout() {
        viewModelScope.launch {
            if (_sessionMode.value is AppSessionMode.Authenticated) {
                repository.logout()
            }
            _sessionMode.value = AppSessionMode.Unauthenticated
            _catalogueState.value = CatalogueUiState()
            _searchState.value = SearchUiState()
            _detailState.value = MovieDetailUiState()
            _streamState.value = StreamLoadUiState()
            _loginState.value = LoginUiState()
        }
    }

    fun refreshCatalogue() {
        when (_sessionMode.value) {
            is AppSessionMode.Authenticated -> loadProductionCatalogue()
            is AppSessionMode.UiPreview -> enterUiPreviewMode()
            else -> Unit
        }
    }

    private fun loadProductionCatalogue() {
        viewModelScope.launch {
            _catalogueState.update { it.copy(isLoading = true, errorMessage = null) }

            val moviesDeferred = async { repository.fetchMovies() }
            val categoriesDeferred = async { repository.fetchCategories() }
            val favoritesDeferred = async { repository.fetchFavorites() }
            val historyDeferred = async { repository.loadWatchHistory() }

            val moviesResult = moviesDeferred.await()
            val categoriesResult = categoriesDeferred.await()
            val favoritesResult = favoritesDeferred.await()
            val historyMap = historyDeferred.await()

            if (moviesResult is ApiResult.Failure && moviesResult.isUnauthorized) {
                handleUnauthorizedSession(moviesResult.message)
                return@launch
            }

            when (moviesResult) {
                is ApiResult.Success -> {
                    val payload = moviesResult.data
                    val allMovies = payload.movies
                    val featured = payload.featured
                        ?: allMovies.firstOrNull { it.isFeatured }
                        ?: allMovies.firstOrNull()
                    val trending = payload.trending.ifEmpty { allMovies.filter { it.isTrending } }
                    val recent = payload.recentlyAdded.ifEmpty { allMovies.filter { it.isRecentlyAdded } }
                    val categories = (categoriesResult as? ApiResult.Success)?.data.orEmpty()
                    val favorites = (favoritesResult as? ApiResult.Success)?.data.orEmpty()

                    _catalogueState.value = CatalogueUiState(
                        isLoading = false,
                        errorMessage = null,
                        movies = allMovies,
                        featuredMovie = featured,
                        trendingMovies = trending,
                        recentlyAddedMovies = recent,
                        categories = categories,
                        selectedCategoryId = _catalogueState.value.selectedCategoryId,
                        favoriteIds = favorites,
                        watchHistory = historyMap
                    )
                    _searchState.update { current ->
                        if (current.query.isBlank() && current.selectedCategoryId == null) {
                            current.copy(results = allMovies, errorMessage = null)
                        } else {
                            current
                        }
                    }
                }
                is ApiResult.Failure -> {
                    _catalogueState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = moviesResult.message
                        )
                    }
                }
            }
        }
    }

    fun selectHomeCategory(categoryId: String?) {
        _catalogueState.update { current ->
            val next = if (current.selectedCategoryId == categoryId) null else categoryId
            current.copy(selectedCategoryId = next)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchState.update { it.copy(query = query, errorMessage = null) }
        executeSearch()
    }

    fun selectSearchCategory(categoryId: String?) {
        _searchState.update { current ->
            val next = if (current.selectedCategoryId == categoryId) null else categoryId
            current.copy(selectedCategoryId = next, errorMessage = null)
        }
        executeSearch()
    }

    private fun executeSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val currentQuery = _searchState.value.query.trim()
            val categoryId = _searchState.value.selectedCategoryId

            if (_sessionMode.value is AppSessionMode.UiPreview) {
                val filtered = filterLocally(SampleCatalogueData.movies, currentQuery, categoryId)
                _searchState.update {
                    it.copy(isSearching = false, results = filtered, errorMessage = null)
                }
                return@launch
            }

            if (currentQuery.isEmpty() && categoryId == null) {
                _searchState.update {
                    it.copy(
                        isSearching = false,
                        results = _catalogueState.value.movies,
                        errorMessage = null
                    )
                }
                return@launch
            }

            _searchState.update { it.copy(isSearching = true, errorMessage = null) }
            delay(280L)

            when (val result = repository.fetchMovies(categoryId = categoryId, searchQuery = currentQuery)) {
                is ApiResult.Success -> {
                    _searchState.update {
                        it.copy(
                            isSearching = false,
                            results = result.data.movies,
                            errorMessage = null
                        )
                    }
                }
                is ApiResult.Failure -> {
                    if (result.isUnauthorized) {
                        handleUnauthorizedSession(result.message)
                    } else {
                        // Fallback to local filter on already-fetched catalogue if network hiccups
                        val fallback = filterLocally(_catalogueState.value.movies, currentQuery, categoryId)
                        _searchState.update {
                            it.copy(
                                isSearching = false,
                                results = fallback,
                                errorMessage = if (fallback.isEmpty()) result.message else null
                            )
                        }
                    }
                }
            }
        }
    }

    private fun filterLocally(source: List<Movie>, query: String, categoryId: String?): List<Movie> {
        val catName = _catalogueState.value.categories.firstOrNull { it.id == categoryId }?.name
        return source.filter { movie ->
            val matchesCategory = categoryId == null ||
                movie.categoryId == categoryId ||
                (catName != null && movie.categories.any { it.equals(catName, ignoreCase = true) })
            val matchesQuery = query.isEmpty() ||
                movie.title.contains(query, ignoreCase = true) ||
                movie.description.orEmpty().contains(query, ignoreCase = true) ||
                movie.categories.any { it.contains(query, ignoreCase = true) }
            matchesCategory && matchesQuery
        }
    }

    fun toggleFavorite(movie: Movie) {
        val currentlyFavorite = movie.id in _catalogueState.value.favoriteIds

        if (_sessionMode.value is AppSessionMode.UiPreview) {
            _catalogueState.update { state ->
                val updated = if (currentlyFavorite) state.favoriteIds - movie.id else state.favoriteIds + movie.id
                state.copy(favoriteIds = updated)
            }
            _snackbarMessage.value = if (currentlyFavorite) {
                "Removed \"${movie.title}\" from Preview List"
            } else {
                "Added \"${movie.title}\" to Preview List"
            }
            return
        }

        // Optimistic update, reverted on backend error
        _catalogueState.update { state ->
            val updated = if (currentlyFavorite) state.favoriteIds - movie.id else state.favoriteIds + movie.id
            state.copy(favoriteIds = updated)
        }

        viewModelScope.launch {
            val result = if (currentlyFavorite) {
                repository.removeFavorite(movie.id)
            } else {
                repository.addFavorite(movie.id)
            }
            when (result) {
                is ApiResult.Success -> {
                    _snackbarMessage.value = if (currentlyFavorite) {
                        "Removed from My List"
                    } else {
                        "Saved to My List"
                    }
                }
                is ApiResult.Failure -> {
                    if (result.isUnauthorized) {
                        handleUnauthorizedSession(result.message)
                    } else {
                        // Revert optimistic change
                        _catalogueState.update { state ->
                            val reverted = if (currentlyFavorite) state.favoriteIds + movie.id else state.favoriteIds - movie.id
                            state.copy(favoriteIds = reverted)
                        }
                        _snackbarMessage.value = result.message
                    }
                }
            }
        }
    }

    fun loadMovieDetail(movieId: String) {
        val cached = _catalogueState.value.movies.firstOrNull { it.id == movieId }
        if (_sessionMode.value is AppSessionMode.UiPreview) {
            val sample = SampleCatalogueData.movies.firstOrNull { it.id == movieId } ?: cached
            _detailState.value = MovieDetailUiState(
                isLoading = false,
                movie = sample,
                errorMessage = if (sample == null) "Movie not found." else null
            )
            return
        }

        _detailState.value = MovieDetailUiState(isLoading = cached == null, movie = cached)
        viewModelScope.launch {
            when (val result = repository.fetchMovieDetails(movieId)) {
                is ApiResult.Success -> {
                    val detail = result.data
                    _detailState.value = MovieDetailUiState(isLoading = false, movie = detail.movie)
                    _catalogueState.update { state ->
                        val favs = if (detail.isFavorite) {
                            state.favoriteIds + detail.movie.id
                        } else {
                            state.favoriteIds - detail.movie.id
                        }
                        val history = if (detail.watchHistory != null) {
                            state.watchHistory + (detail.movie.id to detail.watchHistory)
                        } else {
                            state.watchHistory
                        }
                        state.copy(favoriteIds = favs, watchHistory = history)
                    }
                }
                is ApiResult.Failure -> {
                    if (result.isUnauthorized) {
                        handleUnauthorizedSession(result.message)
                    } else {
                        _detailState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = if (it.movie == null) result.message else null
                            )
                        }
                    }
                }
            }
        }
    }

    fun resolveMovieStream(movieId: String) {
        val movie = _detailState.value.movie?.takeIf { it.id == movieId }
            ?: _catalogueState.value.movies.firstOrNull { it.id == movieId }

        if (movie == null) {
            _streamState.value = StreamLoadUiState(
                isResolving = false,
                resolvedStream = ResolvedStream.Unavailable("Unable to locate movie metadata for streaming.")
            )
            return
        }

        if (_sessionMode.value is AppSessionMode.UiPreview || movie.isSamplePreview) {
            _streamState.value = StreamLoadUiState(
                isResolving = false,
                resolvedStream = ResolvedStream.PreviewModeBlocked
            )
            return
        }

        viewModelScope.launch {
            _streamState.value = StreamLoadUiState(isResolving = true, resolvedStream = null)
            val resolved = repository.resolveStream(movie)
            _streamState.value = StreamLoadUiState(isResolving = false, resolvedStream = resolved)
        }
    }

    fun savePlaybackProgress(movieId: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L || positionMs < 0L) return
        val entry = WatchHistoryEntry(
            movieId = movieId,
            positionMs = positionMs,
            durationMs = durationMs
        )
        _catalogueState.update { state ->
            state.copy(watchHistory = state.watchHistory + (movieId to entry))
        }
        if (_sessionMode.value is AppSessionMode.Authenticated) {
            viewModelScope.launch {
                repository.syncWatchHistory(movieId, positionMs, durationMs)
            }
        }
    }

    fun getMovieById(movieId: String): Movie? {
        return _detailState.value.movie?.takeIf { it.id == movieId }
            ?: _catalogueState.value.movies.firstOrNull { it.id == movieId }
            ?: SampleCatalogueData.movies.firstOrNull { it.id == movieId }
    }

    private fun handleUnauthorizedSession(message: String) {
        viewModelScope.launch {
            repository.logout()
            _sessionMode.value = AppSessionMode.Unauthenticated
            _loginState.update { it.copy(errorMessage = message, isSubmitting = false) }
        }
    }
}
