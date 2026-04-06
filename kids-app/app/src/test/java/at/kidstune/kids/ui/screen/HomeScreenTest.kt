package at.kidstune.kids.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.ui.screens.HomeScreen
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.HomeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun launch(
        state: HomeState = HomeState(),
        onNavigateToBrowse: (BrowseCategory) -> Unit = {},
        onNavigateToNowPlaying: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            KidstuneTheme {
                HomeScreen(
                    state                  = state,
                    onNavigateToBrowse     = onNavigateToBrowse,
                    onNavigateToNowPlaying = onNavigateToNowPlaying
                )
            }
        }
    }

    @Test
    fun `should display music category button`() {
        launch()
        composeTestRule.onNodeWithContentDescription("Musik").assertIsDisplayed()
    }

    @Test
    fun `should display audiobooks category button`() {
        launch()
        composeTestRule.onNodeWithContentDescription("Hörbücher").assertIsDisplayed()
    }

    @Test
    fun `should display favorites category button`() {
        launch()
        composeTestRule.onNodeWithContentDescription("Lieblingssongs").assertIsDisplayed()
    }

    @Test
    fun `should display profile avatar badge with bound profile name`() {
        launch(state = HomeState(boundProfileName = "Luna", boundProfileEmoji = "🐻"))
        composeTestRule.onNodeWithText("Luna").assertIsDisplayed()
    }

    @Test
    fun `should display mini player bar when track is playing`() {
        launch(state = HomeState(nowPlayingTitle = "Bibi & Tina – Folge 1"))
        composeTestRule
            .onNodeWithContentDescription("Jetzt läuft: Bibi & Tina – Folge 1")
            .assertIsDisplayed()
    }

    @Test
    fun `tapping music button invokes navigate to music browse`() {
        var navigatedTo: BrowseCategory? = null
        launch(onNavigateToBrowse = { navigatedTo = it })

        composeTestRule.onNodeWithContentDescription("Musik").performClick()

        assertEquals(BrowseCategory.MUSIC, navigatedTo)
    }

    @Test
    fun `tapping audiobooks button invokes navigate to audiobooks browse`() {
        var navigatedTo: BrowseCategory? = null
        launch(onNavigateToBrowse = { navigatedTo = it })

        composeTestRule.onNodeWithContentDescription("Hörbücher").performClick()

        assertEquals(BrowseCategory.AUDIOBOOK, navigatedTo)
    }

    @Test
    fun `tapping favorites button invokes navigate to favorites browse`() {
        var navigatedTo: BrowseCategory? = null
        launch(onNavigateToBrowse = { navigatedTo = it })

        composeTestRule.onNodeWithContentDescription("Lieblingssongs").performClick()

        assertEquals(BrowseCategory.FAVORITES, navigatedTo)
    }

    @Test
    fun `tapping mini player bar invokes navigate to now playing`() {
        var nowPlayingOpened = false
        launch(
            state                  = HomeState(nowPlayingTitle = "Bibi & Tina – Folge 1"),
            onNavigateToNowPlaying = { nowPlayingOpened = true }
        )

        composeTestRule
            .onNodeWithContentDescription("Jetzt läuft: Bibi & Tina – Folge 1")
            .performClick()

        assertNotNull(nowPlayingOpened)
        assertEquals(true, nowPlayingOpened)
    }

    // ── Offline indicator ─────────────────────────────────────────────────────

    @Test
    fun `offline icon is shown when isOffline is true`() {
        launch(state = HomeState(isOffline = true))
        composeTestRule.onNodeWithContentDescription("Offline").assertIsDisplayed()
    }

    @Test
    fun `offline icon is not shown when isOffline is false`() {
        launch(state = HomeState(isOffline = false))
        val nodes = composeTestRule.onAllNodesWithContentDescription("Offline").fetchSemanticsNodes()
        assertTrue("Offline icon should be absent", nodes.isEmpty())
    }

    // ── Stale content indicator ───────────────────────────────────────────────

    @Test
    fun `stale dot is shown when isStaleContent is true`() {
        launch(state = HomeState(isStaleContent = true))
        composeTestRule.onNodeWithContentDescription("Inhalte veraltet").assertIsDisplayed()
    }

    @Test
    fun `stale dot is not shown when isStaleContent is false`() {
        launch(state = HomeState(isStaleContent = false))
        val nodes = composeTestRule.onAllNodesWithContentDescription("Inhalte veraltet").fetchSemanticsNodes()
        assertTrue("Stale dot should be absent", nodes.isEmpty())
    }

    @Test
    fun `stale dot and offline icon can appear simultaneously`() {
        // Edge case: device was online with stale content, then lost connectivity.
        // isStaleContent is only true when online per ViewModel logic, but HomeScreen
        // renders both independently based on state fields.
        launch(state = HomeState(isOffline = true, isStaleContent = true))
        composeTestRule.onNodeWithContentDescription("Offline").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Inhalte veraltet").assertIsDisplayed()
    }

    // ── No-cache screen ───────────────────────────────────────────────────────

    @Test
    fun `no-cache screen is shown when cachedContentCount is zero`() {
        launch(state = HomeState(cachedContentCount = 0))
        composeTestRule.onNodeWithContentDescription("Kein Cache verfügbar").assertIsDisplayed()
    }

    @Test
    fun `category buttons are not shown when cachedContentCount is zero`() {
        launch(state = HomeState(cachedContentCount = 0))
        val nodes = composeTestRule.onAllNodesWithContentDescription("Musik").fetchSemanticsNodes()
        assertTrue("Category buttons should be absent in no-cache state", nodes.isEmpty())
    }

    @Test
    fun `category buttons are shown when cachedContentCount is greater than zero`() {
        launch(state = HomeState(cachedContentCount = 5))
        composeTestRule.onNodeWithContentDescription("Musik").assertIsDisplayed()
    }

    @Test
    fun `category buttons are shown when cachedContentCount is null (loading)`() {
        launch(state = HomeState(cachedContentCount = null))
        composeTestRule.onNodeWithContentDescription("Musik").assertIsDisplayed()
    }

    @Test
    fun `no-cache screen is shown offline with zero cached content`() {
        launch(state = HomeState(isOffline = true, cachedContentCount = 0))
        composeTestRule.onNodeWithContentDescription("Kein Cache verfügbar").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Offline").assertIsDisplayed()
    }
}
