package at.kidstune.kids.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.ui.screens.AlbumGridScreen
import at.kidstune.kids.ui.screens.BrowseScreen
import at.kidstune.kids.ui.screens.DiscoverScreen
import at.kidstune.kids.ui.screens.HomeScreen
import at.kidstune.kids.ui.screens.NowPlayingScreen
import at.kidstune.kids.ui.screens.ProfileSelectionScreen
import at.kidstune.kids.ui.screens.TrackListScreen
import kotlinx.serialization.Serializable

// ── Route definitions (type-safe Compose Navigation) ──────────────────────

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

/** Shows the ordered track list for a single album. */
@Serializable
data class TrackListRoute(val albumId: String)

@Serializable
object NowPlayingRoute

@Serializable
object DiscoverRoute

// ── NavHost ───────────────────────────────────────────────────────────────

@Composable
fun KidstuneNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Any = HomeRoute
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier
    ) {
        composable<ProfileSelectionRoute> {
            ProfileSelectionScreen(
                onProfileBound = {
                    navController.navigate(HomeRoute) {
                        popUpTo<ProfileSelectionRoute> { inclusive = true }
                    }
                }
            )
        }

        composable<HomeRoute> {
            HomeScreen(
                onNavigateToBrowse     = { category ->
                    navController.navigate(BrowseRoute(category.name))
                },
                onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) },
                onNavigateToDiscover   = { navController.navigate(DiscoverRoute) }
            )
        }

        composable<BrowseRoute> { backStackEntry ->
            val route    = backStackEntry.toRoute<BrowseRoute>()
            val category = BrowseCategory.fromString(route.category)
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

        composable<AlbumGridRoute> {
            AlbumGridScreen(
                onNavigateUp         = { navController.navigateUp() },
                onNavigateToTrackList = { albumId ->
                    navController.navigate(TrackListRoute(albumId))
                }
            )
        }

        composable<TrackListRoute> {
            TrackListScreen(
                onNavigateUp           = { navController.navigateUp() },
                onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) }
            )
        }

        composable<NowPlayingRoute> {
            NowPlayingScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }

        composable<DiscoverRoute> {
            DiscoverScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
    }
}
