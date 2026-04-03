package at.kidstune.kids.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import at.kidstune.kids.domain.model.ContentType
import at.kidstune.kids.ui.screens.BrowseScreen
import at.kidstune.kids.ui.screens.DiscoverScreen
import at.kidstune.kids.ui.screens.HomeScreen
import at.kidstune.kids.ui.screens.NowPlayingScreen
import at.kidstune.kids.ui.screens.SetupScreen
import kotlinx.serialization.Serializable

// ── Route definitions (type-safe Compose Navigation) ──────────────────────

@Serializable
object SetupRoute

@Serializable
object HomeRoute

@Serializable
data class BrowseRoute(val category: String) // category = ContentType name

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
        composable<SetupRoute> {
            SetupScreen(
                onSetupComplete = { navController.navigate(HomeRoute) {
                    popUpTo<SetupRoute> { inclusive = true }
                }}
            )
        }

        composable<HomeRoute> {
            HomeScreen(
                onNavigateToBrowse    = { type -> navController.navigate(BrowseRoute(type.name)) },
                onNavigateToNowPlaying = { navController.navigate(NowPlayingRoute) },
                onNavigateToDiscover  = { navController.navigate(DiscoverRoute) }
            )
        }

        composable<BrowseRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BrowseRoute>()
            val contentType = ContentType.entries.firstOrNull { it.name == route.category }
                ?: ContentType.MUSIC
            BrowseScreen(
                contentType            = contentType,
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
