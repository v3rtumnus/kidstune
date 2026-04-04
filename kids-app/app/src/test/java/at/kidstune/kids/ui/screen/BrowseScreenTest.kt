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
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import at.kidstune.kids.ui.screens.BrowseScreen
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.BrowseIntent
import at.kidstune.kids.ui.viewmodel.BrowseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowseScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Six music entries so we get two pages (4 + 2)
    private val sixMusicEntries: List<LocalContentEntry> = (1..6).map { i ->
        LocalContentEntry(
            id          = "music-$i",
            profileId   = "p1",
            spotifyUri  = "spotify:artist:$i",
            scope       = if (i == 1) ContentScope.ARTIST else ContentScope.ALBUM,
            contentType = ContentType.MUSIC,
            title       = "Musik Eintrag $i",
            imageUrl    = null,
            artistName  = "Künstler $i",
            lastSyncedAt = Instant.parse("2025-01-01T00:00:00Z")
        )
    }

    private val musicState = BrowseState(
        category = BrowseCategory.MUSIC,
        entries  = sixMusicEntries,
        pages    = sixMusicEntries.chunked(4)
    )

    private val favoritesState = BrowseState(
        category = BrowseCategory.FAVORITES,
        entries  = emptyList(),
        pages    = emptyList()
    )

    @Test
    fun `should show first four tiles on initial page for music`() {
        composeTestRule.setContent {
            KidstuneTheme { BrowseScreen(state = musicState) }
        }

        sixMusicEntries.take(4).forEach { entry ->
            composeTestRule.onNodeWithContentDescription(entry.title).assertIsDisplayed()
        }
    }

    @Test
    fun `page indicator shows seite 1 von 2 for six entries`() {
        composeTestRule.setContent {
            KidstuneTheme { BrowseScreen(state = musicState) }
        }

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

        composeTestRule.onNodeWithTag("browse_pager").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Seite 2 von 2").assertIsDisplayed()

        composeTestRule.onNodeWithTag("browse_pager").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Seite 1 von 2").assertIsDisplayed()
    }

    @Test
    fun `tapping a tile dispatches TileTapped intent`() {
        var capturedIntent: BrowseIntent? = null
        composeTestRule.setContent {
            KidstuneTheme {
                BrowseScreen(
                    state    = musicState,
                    onIntent = { capturedIntent = it }
                )
            }
        }

        val firstEntry = sixMusicEntries.first()
        composeTestRule.onNodeWithContentDescription(firstEntry.title).performClick()

        assertTrue(
            "Tapping a tile should dispatch TileTapped",
            capturedIntent is BrowseIntent.TileTapped
        )
        assertEquals(firstEntry, (capturedIntent as BrowseIntent.TileTapped).entry)
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
