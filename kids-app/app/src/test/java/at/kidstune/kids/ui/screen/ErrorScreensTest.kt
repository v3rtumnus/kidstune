package at.kidstune.kids.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import at.kidstune.kids.playback.SpotifyConnectionError
import at.kidstune.kids.ui.screens.HomeScreen
import at.kidstune.kids.ui.screens.SpotifyNotInstalledScreen
import at.kidstune.kids.ui.screens.SpotifyNotLoggedInScreen
import at.kidstune.kids.ui.screens.SpotifyPremiumExpiredScreen
import at.kidstune.kids.ui.screens.StorageFullScreen
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.HomeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ErrorScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── SpotifyNotInstalledScreen ─────────────────────────────────────────────

    @Test
    fun `SpotifyNotInstalledScreen renders correct text`() {
        composeTestRule.setContent {
            KidstuneTheme { SpotifyNotInstalledScreen() }
        }
        composeTestRule.onNodeWithText("Spotify fehlt!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gib das Handy bitte Mama oder Papa.").assertIsDisplayed()
    }

    @Test
    fun `SpotifyNotInstalledScreen has accessibility tag`() {
        composeTestRule.setContent {
            KidstuneTheme { SpotifyNotInstalledScreen() }
        }
        composeTestRule.onNodeWithContentDescription("Spotify nicht installiert").assertIsDisplayed()
    }

    // ── SpotifyNotLoggedInScreen ──────────────────────────────────────────────

    @Test
    fun `SpotifyNotLoggedInScreen renders correct text`() {
        composeTestRule.setContent {
            KidstuneTheme { SpotifyNotLoggedInScreen() }
        }
        composeTestRule.onNodeWithText("Spotify ist nicht angemeldet.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bitte Mama oder Papa fragen.").assertIsDisplayed()
    }

    @Test
    fun `SpotifyNotLoggedInScreen has accessibility tag`() {
        composeTestRule.setContent {
            KidstuneTheme { SpotifyNotLoggedInScreen() }
        }
        composeTestRule.onNodeWithContentDescription("Spotify nicht angemeldet").assertIsDisplayed()
    }

    // ── SpotifyPremiumExpiredScreen ───────────────────────────────────────────

    @Test
    fun `SpotifyPremiumExpiredScreen renders correct text`() {
        composeTestRule.setContent {
            KidstuneTheme { SpotifyPremiumExpiredScreen() }
        }
        composeTestRule.onNodeWithText("Musik geht gerade nicht.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bitte Mama oder Papa fragen.").assertIsDisplayed()
    }

    @Test
    fun `SpotifyPremiumExpiredScreen has accessibility tag`() {
        composeTestRule.setContent {
            KidstuneTheme { SpotifyPremiumExpiredScreen() }
        }
        composeTestRule.onNodeWithContentDescription("Spotify Premium abgelaufen").assertIsDisplayed()
    }

    // ── StorageFullScreen ─────────────────────────────────────────────────────

    @Test
    fun `StorageFullScreen renders correct text`() {
        composeTestRule.setContent {
            KidstuneTheme { StorageFullScreen() }
        }
        composeTestRule.onNodeWithText("Nicht genug Platz!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bitte Mama oder Papa fragen.").assertIsDisplayed()
    }

    @Test
    fun `StorageFullScreen has accessibility tag`() {
        composeTestRule.setContent {
            KidstuneTheme { StorageFullScreen() }
        }
        composeTestRule.onNodeWithContentDescription("Speicher voll").assertIsDisplayed()
    }

    // ── HomeScreen integration – Spotify error routing ────────────────────────

    @Test
    fun `HomeScreen shows SpotifyNotInstalled screen when NOT_INSTALLED error`() {
        composeTestRule.setContent {
            KidstuneTheme {
                HomeScreen(state = HomeState(spotifyError = SpotifyConnectionError.NOT_INSTALLED))
            }
        }
        composeTestRule.onNodeWithContentDescription("Spotify nicht installiert").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen shows SpotifyNotLoggedIn screen when NOT_LOGGED_IN error`() {
        composeTestRule.setContent {
            KidstuneTheme {
                HomeScreen(state = HomeState(spotifyError = SpotifyConnectionError.NOT_LOGGED_IN))
            }
        }
        composeTestRule.onNodeWithContentDescription("Spotify nicht angemeldet").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen shows SpotifyPremiumExpired screen when PREMIUM_REQUIRED error`() {
        composeTestRule.setContent {
            KidstuneTheme {
                HomeScreen(state = HomeState(spotifyError = SpotifyConnectionError.PREMIUM_REQUIRED))
            }
        }
        composeTestRule.onNodeWithContentDescription("Spotify Premium abgelaufen").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen shows StorageFull screen when storageFull is true`() {
        composeTestRule.setContent {
            KidstuneTheme {
                HomeScreen(state = HomeState(storageFull = true))
            }
        }
        composeTestRule.onNodeWithContentDescription("Speicher voll").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen hides category buttons when Spotify error is present`() {
        composeTestRule.setContent {
            KidstuneTheme {
                HomeScreen(state = HomeState(
                    spotifyError        = SpotifyConnectionError.NOT_INSTALLED,
                    cachedContentCount  = 10   // content exists but Spotify error takes priority
                ))
            }
        }
        val musikNodes = composeTestRule.onAllNodesWithContentDescription("Musik").fetchSemanticsNodes()
        assertTrue("Category buttons should not appear during Spotify error", musikNodes.isEmpty())
    }

    @Test
    fun `HomeScreen hides category buttons when storageFull is true`() {
        composeTestRule.setContent {
            KidstuneTheme {
                HomeScreen(state = HomeState(storageFull = true, cachedContentCount = 10))
            }
        }
        val musikNodes = composeTestRule.onAllNodesWithContentDescription("Musik").fetchSemanticsNodes()
        assertTrue("Category buttons should not appear during storage-full error", musikNodes.isEmpty())
    }

    @Test
    fun `HomeScreen error priority – Spotify error takes precedence over storage full`() {
        composeTestRule.setContent {
            KidstuneTheme {
                HomeScreen(state = HomeState(
                    spotifyError = SpotifyConnectionError.NOT_INSTALLED,
                    storageFull  = true
                ))
            }
        }
        // Spotify error has higher priority, so we should see the Spotify screen
        composeTestRule.onNodeWithContentDescription("Spotify nicht installiert").assertIsDisplayed()
        val storageNodes = composeTestRule.onAllNodesWithContentDescription("Speicher voll").fetchSemanticsNodes()
        assertTrue("Storage-full screen must be hidden behind Spotify error", storageNodes.isEmpty())
    }

    @Test
    fun `HomeScreen shows normal content when no errors and content present`() {
        composeTestRule.setContent {
            KidstuneTheme {
                HomeScreen(state = HomeState(cachedContentCount = 5))
            }
        }
        composeTestRule.onNodeWithContentDescription("Musik").assertIsDisplayed()
    }
}
