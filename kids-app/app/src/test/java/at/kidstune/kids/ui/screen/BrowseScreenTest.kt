package at.kidstune.kids.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.ui.screens.BrowseScreen
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.BrowseState
import org.junit.Assert.assertEquals
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
class BrowseScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val musicState = BrowseState(
        category = BrowseCategory.MUSIC,
        tiles    = MockContentProvider.mockMusicTiles,
        pages    = MockContentProvider.mockMusicTiles.chunked(4)
    )

    private val favoritesState = BrowseState(
        category = BrowseCategory.FAVORITES,
        tiles    = emptyList(),
        pages    = emptyList()
    )

    @Test
    fun `should show first four tiles on initial page for music`() {
        composeTestRule.setContent {
            KidstuneTheme { BrowseScreen(state = musicState) }
        }

        val firstPage = MockContentProvider.mockMusicTiles.take(4)
        firstPage.forEach { tile ->
            composeTestRule.onNodeWithContentDescription(tile.title).assertIsDisplayed()
        }
    }

    @Test
    fun `page indicator shows seite 1 von 2 for music with six tiles`() {
        composeTestRule.setContent {
            KidstuneTheme { BrowseScreen(state = musicState) }
        }

        // 6 music tiles → 2 pages
        assertEquals(2, musicState.totalPages)
        composeTestRule
            .onNodeWithContentDescription("Seite 1 von 2")
            .assertIsDisplayed()
    }

    @Test
    fun `swiping left advances to page two and dots update`() {
        composeTestRule.setContent {
            KidstuneTheme { BrowseScreen(state = musicState) }
        }

        composeTestRule.onNodeWithContentDescription("Seite 1 von 2").assertIsDisplayed()

        composeTestRule.onNodeWithTag("browse_pager").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Seite 2 von 2").assertIsDisplayed()
    }

    @Test
    fun `swiping right from page two returns to page one`() {
        composeTestRule.setContent {
            KidstuneTheme { BrowseScreen(state = musicState) }
        }

        // Navigate to page 2
        composeTestRule.onNodeWithTag("browse_pager").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Seite 2 von 2").assertIsDisplayed()

        // Swipe back to page 1
        composeTestRule.onNodeWithTag("browse_pager").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Seite 1 von 2").assertIsDisplayed()
    }

    @Test
    fun `tapping a tile invokes navigate to now playing`() {
        var nowPlayingOpened = false
        composeTestRule.setContent {
            KidstuneTheme {
                BrowseScreen(
                    state                  = musicState,
                    onNavigateToNowPlaying = { nowPlayingOpened = true }
                )
            }
        }

        val firstTile = MockContentProvider.mockMusicTiles.first()
        composeTestRule.onNodeWithContentDescription(firstTile.title).performClick()

        assertTrue("NowPlaying should open after tapping a tile", nowPlayingOpened)
    }

    @Test
    fun `favorites category shows empty state message`() {
        composeTestRule.setContent {
            KidstuneTheme { BrowseScreen(state = favoritesState) }
        }

        composeTestRule.onNodeWithText("Noch keine Lieblingssongs").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tippe auf das Herz bei einem Song!").assertIsDisplayed()
    }

    @Test
    fun `back button is present`() {
        composeTestRule.setContent {
            KidstuneTheme { BrowseScreen(state = musicState) }
        }

        composeTestRule.onNodeWithContentDescription("Zurück").assertIsDisplayed()
    }

    @Test
    fun `back button invokes navigate up`() {
        var navigatedUp = false
        composeTestRule.setContent {
            KidstuneTheme {
                BrowseScreen(
                    state        = musicState,
                    onNavigateUp = { navigatedUp = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Zurück").performClick()

        assertTrue("Back button should invoke navigateUp", navigatedUp)
    }
}
