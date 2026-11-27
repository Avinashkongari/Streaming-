package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.example.R
import com.example.data.model.Movie
import com.example.data.model.WatchHistoryEntry
import com.example.ui.components.ErrorStateView
import com.example.ui.components.MovieArtwork
import com.example.ui.components.MoviePosterCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.formatPlaybackTime
import com.example.ui.theme.AxionCrimson
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceCarbon
import com.example.ui.theme.SurfaceSlate
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MovieDetailUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetailScreen(
    uiState: MovieDetailUiState,
    allMovies: List<Movie>,
    isFavorite: Boolean,
    watchHistory: WatchHistoryEntry?,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onPlayNow: (Movie, Boolean) -> Unit, // (movie, restartFromBeginning)
    onToggleFavorite: (Movie) -> Unit,
    onSelectRelatedMovie: (Movie) -> Unit,
    onRetry: () -> Unit
) {
    val movie = uiState.movie
    val relatedMovies = remember(movie, allMovies) {
        if (movie == null) emptyList()
        else allMovies.filter { it.id != movie.id }.take(6)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        when {
            uiState.isLoading && movie == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AxionCrimson,
                        modifier = Modifier.testTag("details_loading_indicator")
                    )
                }
            }
            uiState.errorMessage != null && movie == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ErrorStateView(message = uiState.errorMessage, onRetry = onRetry)
                }
            }
            movie != null -> {
                val hasResume = watchHistory != null && watchHistory.positionMs > 5_000L

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .testTag("movie_details_content"),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Large Movie Poster Hero
                    item(key = "poster_hero") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(460.dp)
                        ) {
                            MovieArtwork(
                                movie = movie,
                                imageLoader = imageLoader,
                                modifier = Modifier.fillMaxSize()
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(ObsidianBlack.copy(alpha = 0.8f), Color.Transparent)
                                        )
                                    )
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to Color.Transparent,
                                                0.55f to ObsidianBlack.copy(alpha = 0.82f),
                                                1.0f to ObsidianBlack
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    // Title, Metadata & Actions
                    item(key = "metadata_and_actions") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .widthIn(max = 720.dp)
                        ) {
                            Text(
                                text = movie.title.uppercase(),
                                style = MaterialTheme.typography.displayMedium,
                                color = TextPrimary,
                                modifier = Modifier.testTag("detail_movie_title")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                movie.releaseYear?.let { year ->
                                    MetadataBadge(text = year.toString())
                                }
                                MetadataBadge(text = movie.formattedDuration)
                                MetadataBadge(text = movie.qualityLabel, highlight = true)
                                MetadataBadge(text = movie.maturityRating)
                                movie.formattedFileSizeMb?.let { sizeMb ->
                                    MetadataBadge(text = sizeMb)
                                }
                            }

                            if (movie.categories.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = movie.categories.joinToString("  •  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ChampagneGold
                                )
                            }

                            // Resume Progress Bar
                            if (hasResume && watchHistory != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Resume available",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = "${formatPlaybackTime(watchHistory.positionMs)} / ${formatPlaybackTime(watchHistory.durationMs)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextPrimary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { watchHistory.progressFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(CircleShape),
                                        color = AxionCrimson,
                                        trackColor = SurfaceSlate
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Primary Play Now / Resume CTA
                            Button(
                                onClick = { onPlayNow(movie, false) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("detail_play_now_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AxionCrimson,
                                    contentColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (hasResume && watchHistory != null) {
                                        stringResource(
                                            R.string.details_resume_from,
                                            formatPlaybackTime(watchHistory.positionMs)
                                        )
                                    } else {
                                        stringResource(R.string.details_play_now)
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Secondary Action Row: My List + Play from Beginning (if resumed)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onToggleFavorite(movie) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("detail_favorite_button"),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = SurfaceCarbon,
                                        contentColor = TextPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Filled.Check else Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(
                                            if (isFavorite) R.string.home_my_list_added else R.string.home_my_list_add
                                        ),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                if (hasResume) {
                                    OutlinedButton(
                                        onClick = { onPlayNow(movie, true) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("detail_restart_button"),
                                        border = BorderStroke(1.dp, BorderSubtle),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = SurfaceCarbon,
                                            contentColor = TextPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Replay,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Restart",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }

                            // Synopsis
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = stringResource(R.string.details_synopsis),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = movie.description?.takeIf { it.isNotBlank() }
                                    ?: "No description provided for this private release.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )

                            // Streaming Security & Privacy Notice
                            Spacer(modifier = Modifier.height(22.dp))
                            Surface(
                                color = SurfaceCarbon,
                                border = BorderStroke(1.dp, BorderSubtle),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Security,
                                        contentDescription = null,
                                        tint = EmeraldActive,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = stringResource(R.string.details_stream_info_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = stringResource(R.string.details_stream_info_body),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Related Titles Carousel
                    if (relatedMovies.isNotEmpty()) {
                        item(key = "related_titles") {
                            Spacer(modifier = Modifier.height(28.dp))
                            SectionHeader(title = "More in Your Library")
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(relatedMovies, key = { "rel_${it.id}" }) { rel ->
                                    MoviePosterCard(
                                        movie = rel,
                                        imageLoader = imageLoader,
                                        onClick = { onSelectRelatedMovie(rel) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Back Button
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
            color = SurfaceCarbon.copy(alpha = 0.78f),
            shape = CircleShape,
            border = BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.6f))
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("detail_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.details_back_cd),
                    tint = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun MetadataBadge(
    text: String,
    highlight: Boolean = false
) {
    Surface(
        color = if (highlight) AxionCrimson.copy(alpha = 0.2f) else SurfaceSlate,
        border = BorderStroke(
            width = 1.dp,
            color = if (highlight) AxionCrimson.copy(alpha = 0.6f) else BorderSubtle
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) TextPrimary else TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
