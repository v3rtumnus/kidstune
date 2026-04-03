package at.kidstune.kids.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.ui.screens.HomeScreen
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.HomeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
