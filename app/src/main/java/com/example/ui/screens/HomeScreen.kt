package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.example.R
import com.example.data.model.Movie
import com.example.ui.components.AxionWordmark
import com.example.ui.components.CategoryChipRow
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.FeaturedHeroBanner
import com.example.ui.components.MoviePosterCard
import com.example.ui.components.PreviewModeStrip
import com.example.ui.components.SectionHeader
import com.example.ui.components.ShimmerCataloguePlaceholder
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceCarbon
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.CatalogueUiState

@Composable
fun HomeScreen(
    uiState: CatalogueUiState,
    isPreviewMode: Boolean,
    imageLoader: ImageLoader,
    onMovieClick: (Movie) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    onToggleFavorite: (Movie) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    onExitPreview: () -> Unit
) {
    val selectedCategory = remember(uiState.categories, uiState.selectedCategoryId) {
        uiState.categories.firstOrNull { it.id == uiState.selectedCategoryId }
    }

    val filteredMovies = remember(uiState.movies, selectedCategory) {
        if (selectedCategory == null) {
            uiState.movies
        } else {
            uiState.movies.filter { movie ->
                movie.categoryId == selectedCategory.id ||
                    movie.categories.any { it.equals(selectedCategory.name, ignoreCase = true) }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        when {
            uiState.isLoading && uiState.movies.isEmpty() -> {
                ShimmerCataloguePlaceholder()
            }
            uiState.errorMessage != null && uiState.movies.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorStateView(
                        message = uiState.errorMessage,
                        onRetry = onRefresh
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_catalogue_list"),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Featured Hero Banner
                    val hero = uiState.featuredMovie
                    if (hero != null) {
                        item(key = "hero_banner") {
                            FeaturedHeroBanner(
                                movie = hero,
                                imageLoader = imageLoader,
                                isFavorite = hero.id in uiState.favoriteIds,
                                watchHistory = uiState.watchHistory[hero.id],
                                onPlayClick = { onPlayMovie(hero) },
                                onToggleFavorite = { onToggleFavorite(hero) },
                                onDetailsClick = { onMovieClick(hero) }
                            )
                        }
                    } else {
                        item(key = "top_spacer") {
                            Spacer(modifier = Modifier.statusBarsPadding().height(68.dp))
                        }
                    }

                    // Preview Mode indicator strip
                    if (isPreviewMode) {
                        item(key = "preview_strip") {
                            PreviewModeStrip(
                                onExitPreview = onExitPreview,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }

                    // Category Filter Chips
                    if (uiState.categories.isNotEmpty()) {
                        item(key = "category_chips") {
                            CategoryChipRow(
                                categories = uiState.categories,
                                selectedCategoryId = uiState.selectedCategoryId,
                                onSelectCategory = onSelectCategory
                            )
                        }
                    }

                    // If a specific category filter is active, show filtered carousel prominently
                    if (selectedCategory != null) {
                        item(key = "filtered_category_section") {
                            if (filteredMovies.isEmpty()) {
                                EmptyStateView(
                                    icon = Icons.Filled.LocalMovies,
                                    title = stringResource(R.string.home_empty_title),
                                    body = stringResource(R.string.home_empty_body),
                                    actionLabel = "Show All Titles",
                                    onAction = { onSelectCategory(null) }
                                )
                            } else {
                                MovieCarouselSection(
                                    title = selectedCategory.name,
                                    badge = "${filteredMovies.size} titles",
                                    movies = filteredMovies,
                                    imageLoader = imageLoader,
                                    watchHistory = uiState.watchHistory,
                                    onMovieClick = onMovieClick
                                )
                            }
                        }
                    } else {
                        // Continue Watching
                        val continueWatching = uiState.continueWatchingMovies
                        if (continueWatching.isNotEmpty()) {
                            item(key = "continue_watching") {
                                MovieCarouselSection(
                                    title = stringResource(R.string.home_section_continue),
                                    movies = continueWatching,
                                    imageLoader = imageLoader,
                                    watchHistory = uiState.watchHistory,
                                    onMovieClick = onMovieClick
                                )
                            }
                        }

                        // Trending Now
                        if (uiState.trendingMovies.isNotEmpty()) {
                            item(key = "trending_now") {
                                MovieCarouselSection(
                                    title = stringResource(R.string.home_section_trending),
                                    badge = "TOP PICKS",
                                    movies = uiState.trendingMovies,
                                    imageLoader = imageLoader,
                                    watchHistory = uiState.watchHistory,
                                    onMovieClick = onMovieClick
                                )
                            }
                        }

                        // Recently Added
                        if (uiState.recentlyAddedMovies.isNotEmpty()) {
                            item(key = "recently_added") {
                                MovieCarouselSection(
                                    title = stringResource(R.string.home_section_recent),
                                    badge = "NEW",
                                    movies = uiState.recentlyAddedMovies,
                                    imageLoader = imageLoader,
                                    watchHistory = uiState.watchHistory,
                                    onMovieClick = onMovieClick
                                )
                            }
                        }

                        // Per-Category Horizontal Carousels
                        uiState.categories.forEach { category ->
                            val categoryMovies = uiState.movies.filter { movie ->
                                movie.categoryId == category.id ||
                                    movie.categories.any { it.equals(category.name, ignoreCase = true) }
                            }
                            if (categoryMovies.isNotEmpty()) {
                                item(key = "cat_row_${category.id}") {
                                    MovieCarouselSection(
                                        title = category.name,
                                        movies = categoryMovies,
                                        imageLoader = imageLoader,
                                        watchHistory = uiState.watchHistory,
                                        onMovieClick = onMovieClick
                                    )
                                }
                            }
                        }

                        // Empty library state
                        if (uiState.movies.isEmpty()) {
                            item(key = "empty_catalogue") {
                                EmptyStateView(
                                    icon = Icons.Filled.LocalMovies,
                                    title = stringResource(R.string.home_empty_title),
                                    body = stringResource(R.string.home_empty_body),
                                    actionLabel = stringResource(R.string.home_retry),
                                    onAction = onRefresh
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Top Bar over Hero Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AxionWordmark(compact = true)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = SurfaceCarbon.copy(alpha = 0.72f),
                    shape = CircleShape
                ) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh library",
                            tint = TextPrimary
                        )
                    }
                }
                Surface(
                    color = SurfaceCarbon.copy(alpha = 0.72f),
                    shape = CircleShape
                ) {
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search movies",
                            tint = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieCarouselSection(
    title: String,
    movies: List<Movie>,
    imageLoader: ImageLoader,
    watchHistory: Map<String, com.example.data.model.WatchHistoryEntry>,
    onMovieClick: (Movie) -> Unit,
    badge: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = title, badge = badge)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies, key = { "${title}_${it.id}" }) { movie ->
                MoviePosterCard(
                    movie = movie,
                    imageLoader = imageLoader,
                    watchHistory = watchHistory[movie.id],
                    showSubtitle = true,
                    onClick = { onMovieClick(movie) }
                )
            }
        }
    }
}
