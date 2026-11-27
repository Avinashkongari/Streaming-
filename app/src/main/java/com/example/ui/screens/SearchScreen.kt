package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.example.R
import com.example.data.model.Category
import com.example.data.model.Movie
import com.example.data.model.WatchHistoryEntry
import com.example.ui.components.CategoryChipRow
import com.example.ui.components.EmptyStateView
import com.example.ui.components.MoviePosterCard
import com.example.ui.theme.AxionCrimson
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceCarbon
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.SearchUiState

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    categories: List<Category>,
    watchHistory: Map<String, WatchHistoryEntry>,
    imageLoader: ImageLoader,
    onQueryChange: (String) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onMovieClick: (Movie) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_query_input"),
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_placeholder),
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.testTag("search_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.search_clear_cd),
                                tint = TextSecondary
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AxionCrimson,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = SurfaceCarbon,
                    unfocusedContainerColor = SurfaceCarbon,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AxionCrimson
                )
            )
        }

        if (categories.isNotEmpty()) {
            CategoryChipRow(
                categories = categories,
                selectedCategoryId = uiState.selectedCategoryId,
                onSelectCategory = onSelectCategory
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isSearching -> {
                    CircularProgressIndicator(
                        color = AxionCrimson,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                            .testTag("search_loading_indicator")
                    )
                }
                uiState.results.isEmpty() -> {
                    val emptyBody = if (uiState.query.isNotBlank()) {
                        stringResource(R.string.search_empty_body, uiState.query)
                    } else {
                        stringResource(R.string.search_prompt_body)
                    }
                    EmptyStateView(
                        icon = Icons.Filled.SearchOff,
                        title = stringResource(R.string.search_empty_title),
                        body = emptyBody,
                        actionLabel = if (uiState.query.isNotBlank() || uiState.selectedCategoryId != null) {
                            "Clear Filters"
                        } else null,
                        onAction = {
                            onQueryChange("")
                            onSelectCategory(null)
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 148.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("search_results_grid"),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(uiState.results, key = { "search_${it.id}" }) { movie ->
                            MoviePosterCard(
                                movie = movie,
                                imageLoader = imageLoader,
                                watchHistory = watchHistory[movie.id],
                                showSubtitle = true,
                                cardWidth = 170.dp,
                                onClick = { onMovieClick(movie) }
                            )
                        }
                    }
                }
            }
        }
    }
}
