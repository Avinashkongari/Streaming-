package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.Movie
import com.example.data.model.ResolvedStream
import com.example.ui.components.formatPlaybackTime
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AxionCrimson
import com.example.ui.theme.AxionCrimsonBright
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceCarbon
import com.example.ui.theme.SurfaceSlate
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.StreamLoadUiState
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/**
 * Premium Jetpack Media3 ExoPlayer video screen.
 *
 * STREAMING SAFEGUARDS:
 * - Uses OkHttpDataSource with Authorization Bearer token + native HTTP Range Requests (206 Partial Content).
 * - Uses a bounded memory-only DefaultLoadControl (no SimpleCache disk storage, no full downloads, no Gallery writes).
 * - Never fakes playback if backend streaming is unavailable or when viewing UI Preview sample items.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    movie: Movie?,
    streamState: StreamLoadUiState,
    initialResumePositionMs: Long,
    okHttpClient: OkHttpClient,
    onRetryResolve: () -> Unit,
    onSaveProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onExitPreviewToLogin: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var isFullscreenLandscape by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteractionTick by remember { mutableIntStateOf(0) }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(initialResumePositionMs.coerceAtLeast(0L)) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderSeekPositionMs by remember { mutableLongStateOf(0L) }

    var volume by remember { mutableFloatStateOf(1f) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    // Apply orientation & immersive system bar visibility
    DisposableEffect(activity, isFullscreenLandscape) {
        if (activity != null) {
            activity.requestedOrientation = if (isFullscreenLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isFullscreenLandscape) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }

    // Always restore portrait/unspecified orientation & system bars when leaving PlayerScreen
    DisposableEffect(activity) {
        onDispose {
            if (activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        if (isFullscreenLandscape) {
            isFullscreenLandscape = false
        } else {
            onBack()
        }
    }

    val resolvedReady = streamState.resolvedStream as? ResolvedStream.Ready

    // Configure ExoPlayer ONLY when a real authorized HTTPS stream is ready
    val exoPlayer = remember(resolvedReady?.streamUrl) {
        if (resolvedReady == null) {
            null
        } else {
            val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
                .setUserAgent("AxionStreamAndroid/1.0")

            // Strictly temporary RAM buffering (15s min, 30s max) — never downloads complete file to disk
            val memoryOnlyLoadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000,
                    30_000,
                    2_500,
                    5_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory)
                )
                .setLoadControl(memoryOnlyLoadControl)
                .build()
                .apply {
                    val mediaItem = MediaItem.Builder()
                        .setUri(resolvedReady.streamUrl)
                        .setMimeType(resolvedReady.mimeType)
                        .build()
                    setMediaItem(mediaItem)
                    if (initialResumePositionMs > 5_000L) {
                        seekTo(initialResumePositionMs)
                    }
                    playWhenReady = true
                    prepare()
                }
        }
    }

    // Listen to ExoPlayer events & release completely on exit (discards temporary RAM buffer)
    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        playbackError = null
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    } else if (playbackState == Player.STATE_ENDED) {
                        controlsVisible = true
                        onSaveProgress(exoPlayer.duration.coerceAtLeast(0L), exoPlayer.duration.coerceAtLeast(0L))
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    isBuffering = false
                    isPlaying = false
                    controlsVisible = true
                    playbackError = "Playback interrupted (${error.errorCodeName}). The backend stream may be unavailable or your session may have expired."
                }
            }
            exoPlayer.addListener(listener)

            onDispose {
                val finalPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val finalDur = exoPlayer.duration.coerceAtLeast(0L)
                if (finalDur > 0L && finalPos > 0L) {
                    onSaveProgress(finalPos, finalDur)
                }
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }
    }

    // Poll playback position & persist resume timestamp every 5 seconds
    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect
        var tick = 0
        while (true) {
            if (!isSeeking) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                val d = exoPlayer.duration
                if (d > 0L) durationMs = d
            }
            tick++
            if (tick % 10 == 0 && durationMs > 0L && currentPositionMs > 0L) {
                onSaveProgress(currentPositionMs, durationMs)
            }
            delay(500L)
        }
    }

    // Auto-hide controls after 3.5 seconds while actively playing
    LaunchedEffect(controlsVisible, isPlaying, controlsInteractionTick) {
        if (controlsVisible && isPlaying && playbackError == null) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
                controlsInteractionTick++
            }
            .testTag("video_player_root")
    ) {
        when {
            streamState.isResolving -> {
                PlayerLoadingOverlay(
                    title = movie?.title.orEmpty(),
                    subtitle = "Authorizing private stream session…",
                    onBack = onBack
                )
            }

            streamState.resolvedStream is ResolvedStream.PreviewModeBlocked -> {
                PlayerPreviewModeBlockedOverlay(
                    movieTitle = movie?.title.orEmpty(),
                    onExitPreviewToLogin = onExitPreviewToLogin,
                    onBack = onBack
                )
            }

            streamState.resolvedStream is ResolvedStream.Unavailable -> {
                PlayerErrorOverlay(
                    title = stringResource(R.string.player_error_title),
                    message = streamState.resolvedStream.reason,
                    onRetry = onRetryResolve,
                    onBack = onBack
                )
            }

            exoPlayer != null -> {
                // ExoPlayer Video Surface
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            keepScreenOn = true
                        }
                    },
                    update = { playerView ->
                        if (playerView.player != exoPlayer) {
                            playerView.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Buffering Indicator
                if (isBuffering && playbackError == null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag("player_buffering_indicator"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = AxionCrimson,
                            strokeWidth = 3.5.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.player_buffering),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }

                // Runtime Playback Error Overlay
                val errorText = playbackError
                if (errorText != null) {
                    PlayerErrorOverlay(
                        title = stringResource(R.string.player_error_title),
                        message = errorText,
                        onRetry = {
                            playbackError = null
                            isBuffering = true
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                        },
                        onBack = onBack
                    )
                }

                // Netflix-style Controls Overlay
                AnimatedVisibility(
                    visible = controlsVisible && playbackError == null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.78f),
                                        0.3f to Color.Black.copy(alpha = 0.25f),
                                        0.7f to Color.Black.copy(alpha = 0.25f),
                                        1.0f to Color.Black.copy(alpha = 0.88f)
                                    )
                                )
                            )
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Top Bar: Back + Title + Temp Buffer Badge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isFullscreenLandscape) {
                                            isFullscreenLandscape = false
                                        } else {
                                            onBack()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("player_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.details_back_cd),
                                        tint = TextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = movie?.title.orEmpty(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                color = SurfaceCarbon.copy(alpha = 0.8f),
                                border = BorderStroke(1.dp, BorderSubtle),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.player_temp_buffer_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Center Transport Controls: Rewind 10s, Play/Pause, Forward 10s
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    controlsInteractionTick++
                                    val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    currentPositionMs = target
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("player_rewind_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Replay10,
                                    contentDescription = stringResource(R.string.player_rewind_cd),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            Surface(
                                color = AxionCrimson,
                                shape = CircleShape,
                                modifier = Modifier.size(68.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        controlsInteractionTick++
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                        } else {
                                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                                exoPlayer.seekTo(0L)
                                            }
                                            exoPlayer.play()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("player_play_pause_button")
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = stringResource(
                                            if (isPlaying) R.string.player_pause_cd else R.string.player_play_cd
                                        ),
                                        tint = TextPrimary,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    controlsInteractionTick++
                                    val maxDur = exoPlayer.duration.coerceAtLeast(0L)
                                    val target = (exoPlayer.currentPosition + 10_000L)
                                        .let { if (maxDur > 0L) it.coerceAtMost(maxDur) else it }
                                    exoPlayer.seekTo(target)
                                    currentPositionMs = target
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("player_forward_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forward10,
                                    contentDescription = stringResource(R.string.player_forward_cd),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        // Bottom Controls: Seek Bar, Timestamps, Volume, Fullscreen Landscape
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                        ) {
                            val displayedPos = if (isSeeking) sliderSeekPositionMs else currentPositionMs
                            val safeDuration = durationMs.coerceAtLeast(1L)

                            Slider(
                                value = displayedPos.coerceIn(0L, safeDuration).toFloat(),
                                onValueChange = { newVal ->
                                    isSeeking = true
                                    controlsInteractionTick++
                                    sliderSeekPositionMs = newVal.toLong()
                                },
                                onValueChangeFinished = {
                                    exoPlayer.seekTo(sliderSeekPositionMs)
                                    currentPositionMs = sliderSeekPositionMs
                                    isSeeking = false
                                    controlsInteractionTick++
                                },
                                valueRange = 0f..safeDuration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = AxionCrimsonBright,
                                    activeTrackColor = AxionCrimson,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("player_seek_slider")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Current Time / Total Duration
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = formatPlaybackTime(displayedPos),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.testTag("player_current_time")
                                    )
                                    Text(
                                        text = "/",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = formatPlaybackTime(durationMs),
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary,
                                        modifier = Modifier.testTag("player_total_duration")
                                    )
                                }

                                // Volume Controls + Fullscreen Landscape Toggle
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            controlsInteractionTick++
                                            isMuted = !isMuted
                                            exoPlayer.volume = if (isMuted) 0f else volume
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("player_mute_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted || volume == 0f) {
                                                Icons.AutoMirrored.Filled.VolumeOff
                                            } else {
                                                Icons.AutoMirrored.Filled.VolumeUp
                                            },
                                            contentDescription = stringResource(R.string.player_mute_cd),
                                            tint = TextPrimary
                                        )
                                    }

                                    Slider(
                                        value = if (isMuted) 0f else volume,
                                        onValueChange = { v ->
                                            controlsInteractionTick++
                                            volume = v
                                            isMuted = v <= 0.01f
                                            exoPlayer.volume = v
                                        },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = TextPrimary,
                                            activeTrackColor = TextPrimary,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                        ),
                                        modifier = Modifier
                                            .width(96.dp)
                                            .testTag("player_volume_slider")
                                    )

                                    IconButton(
                                        onClick = {
                                            controlsInteractionTick++
                                            isFullscreenLandscape = !isFullscreenLandscape
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("player_fullscreen_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isFullscreenLandscape) {
                                                Icons.Filled.FullscreenExit
                                            } else {
                                                Icons.Filled.Fullscreen
                                            },
                                            contentDescription = stringResource(
                                                if (isFullscreenLandscape) {
                                                    R.string.player_fullscreen_exit_cd
                                                } else {
                                                    R.string.player_fullscreen_enter_cd
                                                }
                                            ),
                                            tint = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerLoadingOverlay(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.details_back_cd),
                tint = TextPrimary
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = AxionCrimson, strokeWidth = 3.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack.copy(alpha = 0.94f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 460.dp),
            color = SurfaceCarbon,
            border = BorderStroke(1.dp, BorderSubtle),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = AxionCrimsonBright,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("player_error_back_button"),
                        border = BorderStroke(1.dp, BorderSubtle),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Back", color = TextPrimary)
                    }
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("player_retry_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AxionCrimson,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.player_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPreviewModeBlockedOverlay(
    movieTitle: String,
    onExitPreviewToLogin: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .testTag("player_preview_notice"),
            color = SurfaceCarbon,
            border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(AmberWarning.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = movieTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.player_preview_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.player_preview_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = onExitPreviewToLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("player_preview_signin_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AxionCrimson,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.profile_exit_preview), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("player_preview_back_button"),
                    border = BorderStroke(1.dp, BorderSubtle),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceSlate,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Return to Details", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
