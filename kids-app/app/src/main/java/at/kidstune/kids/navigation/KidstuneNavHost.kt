package at.kidstune.kids.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.ui.screens.AlbumGridScreen
import at.kidstune.kids.ui.screens.BrowseScreen
import at.kidstune.kids.ui.screens.ChapterListScreen
import at.kidstune.kids.ui.screens.DiscoverScreen
import at.kidstune.kids.ui.screens.HomeScreen
import at.kidstune.kids.ui.screens.NowPlayingScreen
import at.kidstune.kids.ui.screens.PairingScreen
import at.kidstune.kids.ui.screens.ProfileSelectionScreen
import at.kidstune.kids.ui.screens.TrackListScreen
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

/**
 * Shows the chapter list for a single AUDIOBOOK album.
 * Each chapter is tappable and resumes from the saved position if applicable.
 */
@Serializable
data class ChapterListRoute(val albumId: String)

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
    startDestination: Any = HomeRoute
) {
    // SharedTransitionLayout enables shared-element transitions between destinations.
    // LocalSharedTransitionScope is provided to the whole tree so deep composables
    // (e.g. MiniPlayerBar, NowPlayingScreen) can attach sharedBounds modifiers
    // without threading SharedTransitionScope through every function signature.
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController    = navController,
                startDestination = startDestination,
                modifier         = modifier
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
                    // HomeRoute provides AnimatedVisibilityScope so MiniPlayerBar can
                    // participate in the shared-element transition to NowPlayingRoute.
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        HomeScreen(
                            onNavigateToBrowse     = { category ->
                                navController.navigate(BrowseRoute(category.name))
                            },
                            onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) },
                            onNavigateToDiscover   = { navController.navigate(DiscoverRoute) }
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
                            onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) }
                        )
                    }
                }

                composable<AlbumGridRoute> {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        AlbumGridScreen(
                            onNavigateUp            = { navController.navigateUp() },
                            onNavigateToChapterList = { albumId ->
                                navController.navigate(ChapterListRoute(albumId))
                            },
                            onNavigateToNowPlaying  = { navController.navigate(NowPlayingRoute) }
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

                composable<ChapterListRoute> {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        ChapterListScreen(
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
    }
}
