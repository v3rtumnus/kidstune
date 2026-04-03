package at.kidstune.kids.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.domain.model.mockProfiles
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.BrowseState
import at.kidstune.kids.ui.viewmodel.HomeState
import at.kidstune.kids.ui.viewmodel.NowPlayingState
import at.kidstune.kids.ui.viewmodel.ProfileSelectionState

/**
 * Screenshot tests for all full-screen composables.
 * Run:      ./gradlew updateDebugScreenshotTest   → write reference PNGs
 * Validate: ./gradlew validateDebugScreenshotTest → fail if visuals changed
 */

// ── ProfileSelectionScreen ────────────────────────────────────────────────

@Preview(name = "Screen_ProfileSelection", showSystemUi = true)
@Composable
fun ProfileSelectionScreenshot() {
    KidstuneTheme {
        ProfileSelectionScreen(
            state = ProfileSelectionState(profiles = mockProfiles)
        )
    }
}

@Preview(name = "Screen_ProfileSelection_Confirm", showSystemUi = true)
@Composable
fun ProfileSelectionConfirmScreenshot() {
    KidstuneTheme {
        ProfileSelectionScreen(
            state = ProfileSelectionState(
                profiles       = mockProfiles,
                pendingProfile = mockProfiles.first()
            )
        )
    }
}

// ── HomeScreen ────────────────────────────────────────────────────────────

@Preview(name = "Screen_Home", showSystemUi = true)
@Composable
fun HomeScreenshot() {
    KidstuneTheme {
        HomeScreen(state = HomeState())
    }
}

// ── BrowseScreen – Musik ──────────────────────────────────────────────────

@Preview(name = "Screen_Browse_Music", showSystemUi = true)
@Composable
fun BrowseMusicScreenshot() {
    KidstuneTheme {
        BrowseScreen(
            state = BrowseState(
                category = BrowseCategory.MUSIC,
                tiles    = MockContentProvider.mockMusicTiles,
                pages    = MockContentProvider.mockMusicTiles.chunked(4)
            )
        )
    }
}

// ── BrowseScreen – Hörbücher ──────────────────────────────────────────────

@Preview(name = "Screen_Browse_Audiobooks", showSystemUi = true)
@Composable
fun BrowseAudiobooksScreenshot() {
    KidstuneTheme {
        BrowseScreen(
            state = BrowseState(
                category = BrowseCategory.AUDIOBOOK,
                tiles    = MockContentProvider.mockAudiobookTiles,
                pages    = MockContentProvider.mockAudiobookTiles.chunked(4)
            )
        )
    }
}

// ── BrowseScreen – Favoriten (empty state) ────────────────────────────────

@Preview(name = "Screen_Browse_Favorites_Empty", showSystemUi = true)
@Composable
fun FavoritesEmptyScreenshot() {
    KidstuneTheme {
        BrowseScreen(
            state = BrowseState(
                category = BrowseCategory.FAVORITES,
                tiles    = emptyList(),
                pages    = emptyList()
            )
        )
    }
}

// ── NowPlayingScreen ──────────────────────────────────────────────────────

@Preview(name = "Screen_NowPlaying", showSystemUi = true)
@Composable
fun NowPlayingScreenshot() {
    KidstuneTheme {
        NowPlayingScreen(state = NowPlayingState(isPlaying = true, isFavorite = false))
    }
}

@Preview(name = "Screen_NowPlaying_Favorited", showSystemUi = true)
@Composable
fun NowPlayingFavoritedScreenshot() {
    KidstuneTheme {
        NowPlayingScreen(state = NowPlayingState(isPlaying = true, isFavorite = true))
    }
}
