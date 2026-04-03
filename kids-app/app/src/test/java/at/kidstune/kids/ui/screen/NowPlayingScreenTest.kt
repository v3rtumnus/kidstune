package at.kidstune.kids.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.kidstune.kids.ui.screens.NowPlayingScreen
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.NowPlayingIntent
import at.kidstune.kids.ui.viewmodel.NowPlayingState
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
class NowPlayingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun launch(
        state: NowPlayingState = NowPlayingState(),
        onIntent: (NowPlayingIntent) -> Unit = {},
        onNavigateUp: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            KidstuneTheme {
                NowPlayingScreen(
                    state        = state,
                    onIntent     = onIntent,
                    onNavigateUp = onNavigateUp
                )
            }
        }
    }

    @Test
    fun `should display cover art with content description`() {
        val state = NowPlayingState(title = "Bibi & Tina – Folge 1")
        launch(state = state)

        composeTestRule
            .onNodeWithContentDescription("Bibi & Tina – Folge 1 – Cover")
            .assertIsDisplayed()
    }

    @Test
    fun `should display track title`() {
        launch(state = NowPlayingState(title = "Bibi & Tina – Folge 1"))
        composeTestRule.onNodeWithText("Bibi & Tina – Folge 1").assertIsDisplayed()
    }

    @Test
    fun `should display artist name`() {
        launch(state = NowPlayingState(artistName = "Bibi & Tina"))
        composeTestRule.onNodeWithText("Bibi & Tina").assertIsDisplayed()
    }

    @Test
    fun `should display pause button when playing`() {
        launch(state = NowPlayingState(isPlaying = true))
        composeTestRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun `should display play button when paused`() {
        launch(state = NowPlayingState(isPlaying = false))
        composeTestRule.onNodeWithContentDescription("Abspielen").assertIsDisplayed()
    }

    @Test
    fun `tapping play pause button toggles playing state`() {
        var state by mutableStateOf(NowPlayingState(isPlaying = false))
        // setContent directly so the lambda captures the mutableStateOf delegate,
        // not a snapshot value — required for recomposition to work in stateful tests.
        composeTestRule.setContent {
            KidstuneTheme {
                NowPlayingScreen(
                    state    = state,
                    onIntent = { intent ->
                        if (intent == NowPlayingIntent.TogglePlayPause)
                            state = state.copy(isPlaying = !state.isPlaying)
                    }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Abspielen").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Abspielen").performClick()
        composeTestRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun `tapping favorite button adds track to favorites`() {
        var state by mutableStateOf(NowPlayingState(isFavorite = false))
        composeTestRule.setContent {
            KidstuneTheme {
                NowPlayingScreen(
                    state    = state,
                    onIntent = { intent ->
                        if (intent == NowPlayingIntent.ToggleFavorite)
                            state = state.copy(isFavorite = !state.isFavorite)
                    }
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Zu Lieblingssongs hinzufügen")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription("Zu Lieblingssongs hinzufügen")
            .performClick()
        composeTestRule
            .onNodeWithContentDescription("Aus Lieblingssongs entfernen")
            .assertIsDisplayed()
    }

    @Test
    fun `should display skip previous and skip next buttons`() {
        launch()

        composeTestRule.onNodeWithContentDescription("Vorheriger Titel").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Nächster Titel").assertIsDisplayed()
    }

    @Test
    fun `should display time stamps`() {
        launch(state = NowPlayingState(progressMs = 83_000L, durationMs = 225_000L))

        composeTestRule.onNodeWithText("1:23").assertIsDisplayed()
        composeTestRule.onNodeWithText("3:45").assertIsDisplayed()
    }

    @Test
    fun `back button invokes navigate up`() {
        var navigatedUp = false
        launch(onNavigateUp = { navigatedUp = true })

        composeTestRule.onNodeWithContentDescription("Zurück").performClick()

        assertTrue("Back button should invoke navigateUp", navigatedUp)
    }
}
