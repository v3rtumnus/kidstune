package at.kidstune.kids.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.ui.components.MiniPlayerBar
import at.kidstune.kids.ui.screens.AlbumGridScreen
import at.kidstune.kids.ui.screens.BrowseScreen
import at.kidstune.kids.ui.screens.DiscoverScreen
import at.kidstune.kids.ui.screens.HomeScreen
import at.kidstune.kids.ui.screens.NowPlayingScreen
import at.kidstune.kids.ui.screens.PairingScreen
import at.kidstune.kids.ui.screens.PlaylistTrackListScreen
import at.kidstune.kids.ui.screens.ProfileSelectionScreen
import at.kidstune.kids.ui.screens.TrackListScreen
import at.kidstune.kids.ui.viewmodel.ShellIntent
import at.kidstune.kids.ui.viewmodel.ShellViewModel
import kotlinx.serialization.Serializable

// ── Route definitions (type-safe Compose Navigation) ──────────────────────

@Serializable
object PairingRoute

@Serializable
object ProfileSelectionRoute

@Serializable
object HomeRoute

/** [category] is a [BrowseCategory] name, e.g. "MUSIC", "AUDIOBOOK", "FAVORITES". */
@Serializable
data class BrowseRoute(val category: String)

/** Shows the 2×2 album grid for the given content entry (ARTIST scope). */
@Serializable
data class AlbumGridRoute(val contentEntryId: String)

/** Shows the ordered track list for a single MUSIC album or playlist. */
@Serializable
data class TrackListRoute(val albumId: String)

/** Shows all tracks in a playlist in flat playlist order. */
@Serializable
data class PlaylistTrackListRoute(val contentEntryId: String)

@Serializable
object NowPlayingRoute

@Serializable
object DiscoverRoute

// ── NavHost ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun KidstuneNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Any = HomeRoute,
    shellViewModel: ShellViewModel = hiltViewModel()
) {
    val shellState by shellViewModel.state.collectAsState()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDest = currentEntry?.destination

    // Hide the mini-player on setup screens and on the full-screen NowPlaying screen.
    val showMiniPlayer = currentDest?.let { dest ->
        dest.route != PairingRoute::class.qualifiedName &&
        dest.route != ProfileSelectionRoute::class.qualifiedName &&
        dest.route != NowPlayingRoute::class.qualifiedName
    } ?: false

    Column(modifier = modifier.fillMaxSize()) {
        // SharedTransitionLayout enables shared-element transitions between destinations.
        // LocalSharedTransitionScope is provided to the whole tree so deep composables
        // (e.g. NowPlayingScreen) can attach sharedBounds modifiers without threading
        // SharedTransitionScope through every function signature.
        SharedTransitionLayout(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController    = navController,
                    startDestination = startDestination,
                    modifier         = Modifier.fillMaxSize()
                ) {
                composable<PairingRoute> {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        PairingScreen(
                            onPairingSuccess = {
                                navController.navigate(ProfileSelectionRoute) {
                                    popUpTo<PairingRoute> { inclusive = true }
                                }
                            }
                        )
                    }
                }

                composable<ProfileSelectionRoute> {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        ProfileSelectionScreen(
                            onProfileBound = {
                                navController.navigate(HomeRoute) {
                                    popUpTo<ProfileSelectionRoute> { inclusive = true }
                                }
                            }
                        )
                    }
                }

                composable<HomeRoute> {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        HomeScreen(
                            onNavigateToBrowse   = { category ->
                                navController.navigate(BrowseRoute(category.name))
                            },
                            onNavigateToDiscover = { navController.navigate(DiscoverRoute) }
                        )
                    }
                }

                composable<BrowseRoute> { backStackEntry ->
                    val route    = backStackEntry.toRoute<BrowseRoute>()
                    val category = BrowseCategory.fromString(route.category)
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        BrowseScreen(
                            category               = category,
                            onNavigateUp           = { navController.navigateUp() },
                            onNavigateToAlbumGrid  = { contentEntryId ->
                                navController.navigate(AlbumGridRoute(contentEntryId))
                            },
                            onNavigateToTrackList  = { albumId ->
                                navController.navigate(TrackListRoute(albumId))
                            },
                            onNavigateToPlaylistTrackList = { contentEntryId ->
                                navController.navigate(PlaylistTrackListRoute(contentEntryId))
                            },
                            onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) }
                        )
                    }
                }

                composable<AlbumGridRoute> {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        AlbumGridScreen(
                            onNavigateUp          = { navController.navigateUp() },
                            onNavigateToTrackList = { albumId ->
                                navController.navigate(TrackListRoute(albumId))
                            }
                        )
                    }
                }

                composable<TrackListRoute> {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        TrackListScreen(
                            onNavigateUp           = { navController.navigateUp() },
                            onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) }
                        )
                    }
                }

                composable<PlaylistTrackListRoute> {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        PlaylistTrackListScreen(
                            onNavigateUp           = { navController.navigateUp() },
                            onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) }
                        )
                    }
                }

                composable<NowPlayingRoute>(
                    // NowPlaying slides up from bottom and fades in; exits the same way.
                    enterTransition = { slideInVertically(initialOffsetY = { it / 3 }) + fadeIn() },
                    exitTransition  = { slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut() },
                    popEnterTransition = { slideInVertically(initialOffsetY = { it / 3 }) + fadeIn() },
                    popExitTransition  = { slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut() }
                ) {
                    // NowPlayingRoute provides AnimatedVisibilityScope so NowPlayingScreen
                    // can participate in the shared-element transition from HomeRoute.
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        NowPlayingScreen(
                            onNavigateUp = { navController.navigateUp() }
                        )
                    }
                }

                composable<DiscoverRoute>(
                    enterTransition = { scaleIn(initialScale = 0.95f) + fadeIn() },
                    exitTransition  = { scaleOut(targetScale = 0.95f) + fadeOut() },
                    popEnterTransition = { scaleIn(initialScale = 0.95f) + fadeIn() },
                    popExitTransition  = { scaleOut(targetScale = 0.95f) + fadeOut() }
                ) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        DiscoverScreen(
                            onNavigateUp = { navController.navigateUp() }
                        )
                    }
                }
                }
            }
        }  // SharedTransitionLayout

        if (showMiniPlayer) {
            MiniPlayerBar(
                title       = shellState.nowPlayingTitle,
                artistName  = shellState.nowPlayingArtist,
                imageUrl    = shellState.nowPlayingImageUrl,
                isPlaying   = shellState.isPlaying,
                onPlayPause = { shellViewModel.onIntent(ShellIntent.TogglePlayPause) },
                onExpand    = { navController.navigate(NowPlayingRoute) }
            )
        }
    }  // Column
}
