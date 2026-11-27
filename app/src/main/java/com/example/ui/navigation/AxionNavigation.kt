package com.example.ui.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.model.AppSessionMode
import com.example.ui.components.AxionWordmark
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MovieDetailScreen
import com.example.ui.screens.MyListScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.AxionCrimson
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianDeep
import com.example.ui.theme.SurfaceCarbon
import com.example.ui.theme.SurfaceSlate
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.AxionAppViewModel

object AxionRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val MY_LIST = "my_list"
    const val PROFILE = "profile"
    const val MOVIE_DETAIL = "movie_detail/{movieId}"
    const val PLAYER = "player/{movieId}/{restart}"

    fun movieDetail(movieId: String): String = "movie_detail/${Uri.encode(movieId)}"
    fun player(movieId: String, restart: Boolean = false): String =
        "player/${Uri.encode(movieId)}/$restart"
}

private data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val mainTabs = listOf(
    NavTabItem(
        route = AxionRoutes.HOME,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_tab_home"
    ),
    NavTabItem(
        route = AxionRoutes.SEARCH,
        labelRes = R.string.nav_search,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        testTag = "nav_tab_search"
    ),
    NavTabItem(
        route = AxionRoutes.MY_LIST,
        labelRes = R.string.nav_my_list,
        selectedIcon = Icons.Filled.Bookmark,
        unselectedIcon = Icons.Outlined.BookmarkBorder,
        testTag = "nav_tab_mylist"
    ),
    NavTabItem(
        route = AxionRoutes.PROFILE,
        labelRes = R.string.nav_profile,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
        testTag = "nav_tab_profile"
    )
)

@Composable
fun AxionStreamRoot(
    viewModel: AxionAppViewModel = viewModel()
) {
    val sessionMode by viewModel.sessionMode.collectAsStateWithLifecycle()
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val catalogueState by viewModel.catalogueState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    when (sessionMode) {
        AppSessionMode.Checking -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AxionWordmark()
                    Spacer(modifier = Modifier.height(22.dp))
                    CircularProgressIndicator(
                        color = AxionCrimson,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        AppSessionMode.Unauthenticated -> {
            LoginScreen(
                uiState = loginState,
                onAccessCodeChange = viewModel::updateAccessCodeInput,
                onSubmit = viewModel::submitAccessCode,
                onEnterPreviewMode = viewModel::enterUiPreviewMode
            )
        }

        is AppSessionMode.Authenticated,
        AppSessionMode.UiPreview -> {
            val isPreviewMode = sessionMode is AppSessionMode.UiPreview
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val currentRoute = currentDestination?.route
            val showPrimaryNav = currentRoute in setOf(
                AxionRoutes.HOME,
                AxionRoutes.SEARCH,
                AxionRoutes.MY_LIST,
                AxionRoutes.PROFILE
            )

            val snackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(snackbarMessage) {
                val msg = snackbarMessage
                if (!msg.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(msg)
                    viewModel.consumeSnackbar()
                }
            }

            fun navigateToTab(route: String) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack)
            ) {
                val useNavigationRail = maxWidth >= 600.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavigationRail && showPrimaryNav) {
                        NavigationRail(
                            containerColor = ObsidianDeep,
                            contentColor = TextPrimary,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(modifier = Modifier.height(24.dp))
                            mainTabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(tab.labelRes),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = TextPrimary,
                                        selectedTextColor = TextPrimary,
                                        indicatorColor = AxionCrimson.copy(alpha = 0.25f),
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f),
                        containerColor = ObsidianBlack,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = {
                            SnackbarHost(hostState = snackbarHostState) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = SurfaceSlate,
                                    contentColor = TextPrimary
                                )
                            }
                        },
                        bottomBar = {
                            if (!useNavigationRail && showPrimaryNav) {
                                NavigationBar(
                                    containerColor = ObsidianDeep,
                                    tonalElevation = 0.dp,
                                    windowInsets = WindowInsets.navigationBars
                                ) {
                                    mainTabs.forEach { tab ->
                                        val selected =
                                            currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateToTab(tab.route) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = stringResource(tab.labelRes)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = stringResource(tab.labelRes),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = TextPrimary,
                                                selectedTextColor = TextPrimary,
                                                indicatorColor = AxionCrimson.copy(alpha = 0.26f),
                                                unselectedIconColor = TextMuted,
                                                unselectedTextColor = TextMuted
                                            ),
                                            modifier = Modifier.testTag(tab.testTag)
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = AxionRoutes.HOME,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            composable(AxionRoutes.HOME) {
                                HomeScreen(
                                    uiState = catalogueState,
                                    isPreviewMode = isPreviewMode,
                                    imageLoader = viewModel.imageLoader,
                                    onMovieClick = { movie ->
                                        navController.navigate(AxionRoutes.movieDetail(movie.id))
                                    },
                                    onPlayMovie = { movie ->
                                        navController.navigate(AxionRoutes.player(movie.id, restart = false))
                                    },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onSelectCategory = viewModel::selectHomeCategory,
                                    onSearchClick = { navigateToTab(AxionRoutes.SEARCH) },
                                    onRefresh = viewModel::refreshCatalogue,
                                    onExitPreview = viewModel::logout
                                )
                            }

                            composable(AxionRoutes.SEARCH) {
                                SearchScreen(
                                    uiState = searchState,
                                    categories = catalogueState.categories,
                                    watchHistory = catalogueState.watchHistory,
                                    imageLoader = viewModel.imageLoader,
                                    onQueryChange = viewModel::updateSearchQuery,
                                    onSelectCategory = viewModel::selectSearchCategory,
                                    onMovieClick = { movie ->
                                        navController.navigate(AxionRoutes.movieDetail(movie.id))
                                    }
                                )
                            }

                            composable(AxionRoutes.MY_LIST) {
                                MyListScreen(
                                    favoriteMovies = catalogueState.favoriteMovies,
                                    isPreviewMode = isPreviewMode,
                                    imageLoader = viewModel.imageLoader,
                                    onMovieClick = { movie ->
                                        navController.navigate(AxionRoutes.movieDetail(movie.id))
                                    },
                                    onPlayMovie = { movie ->
                                        navController.navigate(AxionRoutes.player(movie.id, restart = false))
                                    },
                                    onRemoveFavorite = viewModel::toggleFavorite,
                                    onExploreClick = { navigateToTab(AxionRoutes.HOME) }
                                )
                            }

                            composable(AxionRoutes.PROFILE) {
                                ProfileScreen(
                                    sessionMode = sessionMode,
                                    favoritesCount = catalogueState.favoriteIds.size,
                                    watchedCount = catalogueState.continueWatchingMovies.size,
                                    onLogout = viewModel::logout
                                )
                            }

                            composable(
                                route = AxionRoutes.MOVIE_DETAIL,
                                arguments = listOf(navArgument("movieId") { type = NavType.StringType })
                            ) { entry ->
                                val movieId = entry.arguments?.getString("movieId").orEmpty()
                                LaunchedEffect(movieId) {
                                    viewModel.loadMovieDetail(movieId)
                                }
                                MovieDetailScreen(
                                    uiState = detailState,
                                    allMovies = catalogueState.movies,
                                    isFavorite = movieId in catalogueState.favoriteIds,
                                    watchHistory = catalogueState.watchHistory[movieId],
                                    imageLoader = viewModel.imageLoader,
                                    onBack = { navController.popBackStack() },
                                    onPlayNow = { movie, restart ->
                                        navController.navigate(AxionRoutes.player(movie.id, restart = restart))
                                    },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onSelectRelatedMovie = { rel ->
                                        navController.navigate(AxionRoutes.movieDetail(rel.id))
                                    },
                                    onRetry = { viewModel.loadMovieDetail(movieId) }
                                )
                            }

                            composable(
                                route = AxionRoutes.PLAYER,
                                arguments = listOf(
                                    navArgument("movieId") { type = NavType.StringType },
                                    navArgument("restart") { type = NavType.BoolType }
                                )
                            ) { entry ->
                                val movieId = entry.arguments?.getString("movieId").orEmpty()
                                val restart = entry.arguments?.getBoolean("restart") ?: false
                                val movie = remember(movieId, catalogueState.movies, detailState.movie) {
                                    viewModel.getMovieById(movieId)
                                }
                                val initialPos = remember(movieId, restart) {
                                    if (restart) 0L else catalogueState.watchHistory[movieId]?.positionMs ?: 0L
                                }

                                LaunchedEffect(movieId) {
                                    viewModel.resolveMovieStream(movieId)
                                }

                                PlayerScreen(
                                    movie = movie,
                                    streamState = streamState,
                                    initialResumePositionMs = initialPos,
                                    okHttpClient = viewModel.okHttpClient,
                                    onRetryResolve = { viewModel.resolveMovieStream(movieId) },
                                    onSaveProgress = { pos, dur ->
                                        viewModel.savePlaybackProgress(movieId, pos, dur)
                                    },
                                    onExitPreviewToLogin = viewModel::logout,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
