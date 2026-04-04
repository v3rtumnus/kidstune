package at.kidstune.kids.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.domain.model.mockProfiles
import at.kidstune.kids.playback.NowPlayingState
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.BrowseState
import at.kidstune.kids.ui.viewmodel.ChapterListState
import at.kidstune.kids.ui.viewmodel.HomeState
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
    val entries = MockContentProvider.contentEntries.filter { it.contentType.name == "MUSIC" }
    KidstuneTheme {
        BrowseScreen(
            state = BrowseState(
                category = BrowseCategory.MUSIC,
                entries  = entries,
                pages    = entries.chunked(4)
            )
        )
    }
}

// ── BrowseScreen – Hörbücher ──────────────────────────────────────────────

@Preview(name = "Screen_Browse_Audiobooks", showSystemUi = true)
@Composable
fun BrowseAudiobooksScreenshot() {
    val entries = MockContentProvider.contentEntries.filter { it.contentType.name == "AUDIOBOOK" }
    KidstuneTheme {
        BrowseScreen(
            state = BrowseState(
                category = BrowseCategory.AUDIOBOOK,
                entries  = entries,
                pages    = entries.chunked(4)
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
                category       = BrowseCategory.FAVORITES,
                favorites      = emptyList(),
                favoritesPages = emptyList()
            )
        )
    }
}

// ── NowPlayingScreen ──────────────────────────────────────────────────────

@Preview(name = "Screen_NowPlaying", showSystemUi = true)
@Composable
fun NowPlayingScreenshot() {
    KidstuneTheme {
        NowPlayingScreen(
            state = NowPlayingState(
                title      = "Bibi & Tina – Folge 1",
                artistName = "Bibi & Tina",
                isPlaying  = true,
                isFavorite = false,
                durationMs = 225_000L,
                positionMs = 83_000L
            )
        )
    }
}

@Preview(name = "Screen_NowPlaying_Favorited", showSystemUi = true)
@Composable
fun NowPlayingFavoritedScreenshot() {
    KidstuneTheme {
        NowPlayingScreen(
            state = NowPlayingState(
                title      = "Bibi & Tina – Folge 1",
                artistName = "Bibi & Tina",
                isPlaying  = true,
                isFavorite = true,
                durationMs = 225_000L,
                positionMs = 83_000L
            )
        )
    }
}

// ── ChapterListScreen ─────────────────────────────────────────────────────

@Preview(name = "Screen_ChapterList", showSystemUi = true)
@Composable
fun ChapterListScreenshot() {
    val tkkg = MockContentProvider.albums.first { it.id == "album-tkkg-200" }
    val chapters = MockContentProvider.tracks.filter { it.albumId == "album-tkkg-200" }
    KidstuneTheme {
        ChapterListScreen(
            state = ChapterListState(
                album            = tkkg,
                chapters         = chapters,
                resumeTrackUri   = "spotify:track:tkkg200t2",
                resumePositionMs = 600_000L
            )
        )
    }
}
