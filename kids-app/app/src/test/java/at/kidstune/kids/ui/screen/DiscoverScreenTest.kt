package at.kidstune.kids.ui.screen

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import at.kidstune.kids.data.mock.MockDiscoverData
import at.kidstune.kids.domain.model.PendingRequest
import at.kidstune.kids.domain.model.RequestStatus
import at.kidstune.kids.ui.screens.DiscoverScreen
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.DiscoverIntent
import at.kidstune.kids.ui.viewmodel.DiscoverState
import at.kidstune.kids.ui.viewmodel.DiscoverViewModel
import at.kidstune.kids.ui.viewmodel.pendingRequestTimeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Idle state ────────────────────────────────────────────────────────

    @Test
    fun `idle state shows suggestion tiles`() {
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(state = DiscoverState())
            }
        }

        // First suggestion tile should be visible
        composeTestRule
            .onNodeWithText(MockDiscoverData.mockSuggestions.first().title)
            .assertIsDisplayed()
    }

    @Test
    fun `idle state shows request button for first tile`() {
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(state = DiscoverState())
            }
        }

        val firstTile = MockDiscoverData.mockSuggestions.first()
        composeTestRule
            .onNodeWithContentDescription("Wünschen: ${firstTile.title}")
            .assertIsDisplayed()
    }

    // ── Active search state ───────────────────────────────────────────────

    @Test
    fun `typing query replaces suggestions with search results`() {
        val vm = DiscoverViewModel()
        composeTestRule.setContent {
            val state by vm.state.collectAsState()
            KidstuneTheme {
                DiscoverScreen(state = state, onIntent = vm::onIntent)
            }
        }

        // Trigger search
        composeTestRule.onNodeWithTag("discover_search_field").performTextInput("Frozen")
        composeTestRule.waitForIdle()

        // Search result should appear
        composeTestRule
            .onNodeWithText(MockDiscoverData.mockSearchResults.first().title)
            .assertIsDisplayed()

        // A suggestion that was not in search results should be gone
        composeTestRule
            .onNodeWithText(MockDiscoverData.mockSuggestions[1].title)
            .assertDoesNotExist()
    }

    // ── Request flow ──────────────────────────────────────────────────────

    @Test
    fun `tapping Request adds item to Meine Wünsche and disables button`() {
        val vm = DiscoverViewModel()
        composeTestRule.setContent {
            val state by vm.state.collectAsState()
            KidstuneTheme {
                DiscoverScreen(state = state, onIntent = vm::onIntent)
            }
        }

        // Find first tile whose URI is not yet in requestedUris
        val unrequestedTile = MockDiscoverData.mockSuggestions.first {
            it.spotifyUri !in vm.state.value.requestedUris
        }

        // Click the request button
        composeTestRule
            .onNodeWithContentDescription("Wünschen: ${unrequestedTile.title}")
            .performClick()
        composeTestRule.waitForIdle()

        // ViewModel state should now contain the new request
        assertTrue(
            vm.state.value.pendingRequests.any { it.tile.spotifyUri == unrequestedTile.spotifyUri }
        )

        // The request button for that tile should be gone (replaced by "Angefragt")
        composeTestRule
            .onNodeWithContentDescription("Wünschen: ${unrequestedTile.title}")
            .assertDoesNotExist()

        // Scroll to "Meine Wünsche" section and verify it is visible
        composeTestRule
            .onNodeWithTag("discover_lazy_column")
            .performScrollToNode(hasText("Meine Wünsche"))
        composeTestRule.onNodeWithText("Meine Wünsche").assertIsDisplayed()
    }

    @Test
    fun `tapping Request fires RequestContent intent`() {
        var capturedIntent: DiscoverIntent? = null
        val tile = MockDiscoverData.mockSuggestions[2]
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(
                    state    = DiscoverState(requestedUris = emptySet()),
                    onIntent = { capturedIntent = it }
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Wünschen: ${tile.title}")
            .performClick()

        assertTrue(capturedIntent is DiscoverIntent.RequestContent)
        assertEquals(tile, (capturedIntent as DiscoverIntent.RequestContent).tile)
    }

    // ── 3-request limit ───────────────────────────────────────────────────

    @Test
    fun `when 3 pending requests exist unrequested buttons show limit message`() {
        val threePending = MockDiscoverData.mockSuggestions.take(3).mapIndexed { i, tile ->
            PendingRequest(
                id          = "req-$i",
                tile        = tile,
                status      = RequestStatus.PENDING,
                requestedAt = Instant.now().minus(i.toLong() + 1, ChronoUnit.HOURS)
            )
        }
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(
                    state = DiscoverState(
                        pendingRequests = threePending,
                        requestedUris   = threePending.map { it.tile.spotifyUri }.toSet()
                    )
                )
            }
        }

        // The 4th suggestion is not in requestedUris – its "Wünschen:" button should be gone
        val unrequestedTile = MockDiscoverData.mockSuggestions[3]
        composeTestRule
            .onNodeWithContentDescription("Wünschen: ${unrequestedTile.title}")
            .assertDoesNotExist()

        // At least one limit message button is visible in the viewport
        composeTestRule
            .onAllNodesWithText(
                "Du hast schon 3 Wünsche offen",
                substring = true
            )
            .onFirst()
            .assertIsDisplayed()
    }

    // ── Back button ───────────────────────────────────────────────────────

    @Test
    fun `back button is present and has 72dp touch target`() {
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(state = DiscoverState())
            }
        }

        composeTestRule.onNodeWithContentDescription("Zurück").assertIsDisplayed()
    }

    @Test
    fun `back button invokes navigate up`() {
        var navigatedUp = false
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(
                    state        = DiscoverState(),
                    onNavigateUp = { navigatedUp = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Zurück").performClick()

        assertTrue("Back button should invoke navigateUp", navigatedUp)
    }

    // ── Meine Wünsche hidden when empty ───────────────────────────────────

    @Test
    fun `Meine Wünsche section is hidden when no pending requests`() {
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(state = DiscoverState(pendingRequests = emptyList()))
            }
        }

        composeTestRule.onNodeWithText("Meine Wünsche").assertDoesNotExist()
    }

    // ── pendingRequestTimeLabel unit tests ────────────────────────────────

    @Test
    fun `pendingRequestTimeLabel returns correct label for under 1 hour`() {
        val now = Instant.now()
        val requestedAt = now.minus(30, ChronoUnit.MINUTES)

        assertEquals(
            "Mama/Papa schauen sich das an",
            pendingRequestTimeLabel(requestedAt, now)
        )
    }

    @Test
    fun `pendingRequestTimeLabel returns correct label for 1-24 hours`() {
        val now = Instant.now()
        val requestedAt = now.minus(6, ChronoUnit.HOURS)

        assertEquals(
            "Gestern gewünscht",
            pendingRequestTimeLabel(requestedAt, now)
        )
    }

    @Test
    fun `pendingRequestTimeLabel returns correct label for over 24 hours`() {
        val now = Instant.now()
        val requestedAt = now.minus(3, ChronoUnit.DAYS)

        assertEquals(
            "Vor ein paar Tagen gewünscht",
            pendingRequestTimeLabel(requestedAt, now)
        )
    }
}
