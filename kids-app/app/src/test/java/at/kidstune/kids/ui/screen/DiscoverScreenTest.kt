package at.kidstune.kids.ui.screen

import android.content.Context
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
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.local.ContentDao
import at.kidstune.kids.data.local.ContentRequestDao
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.local.entities.LocalContentRequest
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.data.remote.dto.ContentRequestResponseDto
import at.kidstune.kids.data.remote.dto.DiscoverItemDto
import at.kidstune.kids.data.remote.dto.SearchResultsDto
import at.kidstune.kids.data.repository.DiscoverRepository
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import at.kidstune.kids.domain.model.DiscoverTile
import at.kidstune.kids.domain.model.PendingRequest
import at.kidstune.kids.domain.model.RequestStatus
import at.kidstune.kids.ui.screens.DiscoverScreen
import at.kidstune.kids.ui.theme.KidstuneTheme
import at.kidstune.kids.ui.viewmodel.DiscoverIntent
import at.kidstune.kids.ui.viewmodel.DiscoverState
import at.kidstune.kids.ui.viewmodel.DiscoverViewModel
import at.kidstune.kids.ui.viewmodel.pendingRequestTimeLabel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.temporal.ChronoUnit

// ── Minimal fakes ──────────────────────────────────────────────────────────────

private class SimpleContentDao : ContentDao {
    override suspend fun insertAll(entries: List<LocalContentEntry>) {}
    override fun getAll(profileId: String): Flow<List<LocalContentEntry>> = flowOf(emptyList())
    override fun getByType(profileId: String, type: ContentType): Flow<List<LocalContentEntry>> = flowOf(emptyList())
    override suspend fun getById(id: String): LocalContentEntry? = null
    override suspend fun deleteById(id: String) {}
    override suspend fun deleteAll(profileId: String) {}
    override suspend fun countByType(profileId: String, type: ContentType): Int = 0
    override fun countAllFlow(profileId: String): Flow<Int> = MutableStateFlow(0)
    override suspend fun getExistingUris(profileId: String): List<String> = emptyList()
}

private class SimpleContentRequestDao : ContentRequestDao {
    private val flow = MutableStateFlow<List<LocalContentRequest>>(emptyList())
    override suspend fun insert(request: LocalContentRequest) { flow.value = flow.value + request }
    override suspend fun update(request: LocalContentRequest) {}
    override fun getVisible(profileId: String): Flow<List<LocalContentRequest>> = flow
    override suspend fun getPendingUpload(profileId: String): List<LocalContentRequest> = emptyList()
    override suspend fun countPending(profileId: String): Int = 0
    override suspend fun deleteApprovedAndExpired(profileId: String) {}
    override suspend fun deleteById(id: String) {}
    override suspend fun updateStatusAndServerId(id: String, status: String, serverId: String?) {}
    override suspend fun updateByServerId(serverId: String, status: String, parentNote: String?) {}
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private val SUGGESTIONS = listOf(
    mockTile("spotify:artist:bibi-tina",        "Bibi & Tina",          ContentScope.ARTIST),
    mockTile("spotify:artist:pumuckl",          "Pumuckl",               ContentScope.ARTIST),
    mockTile("spotify:artist:die-drei",         "Die drei ???",          ContentScope.ARTIST),
    mockTile("spotify:artist:pippi",            "Pippi Långstrump",      ContentScope.ARTIST),
    mockTile("spotify:artist:tkkg",             "TKKG",                  ContentScope.ARTIST),
    mockTile("spotify:artist:benjamin",         "Benjamin Blümchen",     ContentScope.ARTIST),
    mockTile("spotify:artist:lillifee",         "Prinzessin Lillifee",   ContentScope.ARTIST),
    mockTile("spotify:artist:yakari",           "Yakari",                ContentScope.ARTIST),
)

private val SEARCH_RESULTS = listOf(
    mockTile("spotify:album:frozen-ost",  "Frozen – Die Eiskönigin (OST)", ContentScope.ALBUM),
    mockTile("spotify:album:frozen2-ost", "Frozen 2 (OST)",                ContentScope.ALBUM),
    mockTile("spotify:album:encanto",     "Encanto (OST)",                  ContentScope.ALBUM),
)

private fun mockTile(uri: String, title: String, scope: ContentScope) = DiscoverTile(
    spotifyUri = uri,
    title      = title,
    artistName = title,
    imageUrl   = null,
    type       = ContentType.MUSIC,
    scope      = scope,
)

// ─────────────────────────────────────────────────────────────────────────────

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: DiscoverViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ProfilePreferences(context).also { it.boundProfileId = "profile-screen-test" }

        val fakeClient = mockk<KidstuneApiClient>(relaxed = true)
        coEvery { fakeClient.fetchSuggestions(any()) } returns SUGGESTIONS.map {
            DiscoverItemDto(it.spotifyUri, it.title, null, it.spotifyUri, it.artistName)
        }
        coEvery { fakeClient.searchContent(any(), any()) } returns SearchResultsDto(
            albums = SEARCH_RESULTS.map {
                DiscoverItemDto(it.spotifyUri, it.title, null, it.spotifyUri, it.artistName)
            }
        )
        coEvery { fakeClient.createContentRequest(any(), any()) } returns ContentRequestResponseDto(
            id = "srv-1", profileId = "profile-screen-test", spotifyUri = "s",
            title = "T", contentType = "MUSIC", status = "PENDING",
            requestedAt = Instant.now().toString()
        )

        val repo = DiscoverRepository(fakeClient, SimpleContentDao(), SimpleContentRequestDao())
        viewModel = DiscoverViewModel(repo, SimpleContentDao(), prefs)
    }

    // ── Idle state ────────────────────────────────────────────────────────────

    @Test
    fun `idle state shows suggestion tiles`() {
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(state = DiscoverState(suggestions = SUGGESTIONS))
            }
        }
        composeTestRule.onNodeWithText(SUGGESTIONS.first().title).assertIsDisplayed()
    }

    @Test
    fun `idle state shows request button for first tile`() {
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(state = DiscoverState(suggestions = SUGGESTIONS))
            }
        }
        composeTestRule
            .onNodeWithContentDescription("Wünschen: ${SUGGESTIONS.first().title}")
            .assertIsDisplayed()
    }

    // ── Scope badge ───────────────────────────────────────────────────────────

    @Test
    fun `album scope shows Album badge in search results`() {
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(
                    state = DiscoverState(
                        query         = "Frozen",
                        searchResults = SEARCH_RESULTS
                    )
                )
            }
        }
        // At least one "Album" scope badge should be visible
        composeTestRule
            .onAllNodesWithText("Album", substring = false)
            .onFirst()
            .assertIsDisplayed()
    }

    // ── Active search state ───────────────────────────────────────────────────

    @Test
    fun `typing query replaces suggestions with search results`() {
        composeTestRule.setContent {
            val state by viewModel.state.collectAsState()
            KidstuneTheme {
                DiscoverScreen(state = state, onIntent = viewModel::onIntent)
            }
        }

        composeTestRule.onNodeWithTag("discover_search_field").performTextInput("Frozen")
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(SEARCH_RESULTS.first().title)
            .assertIsDisplayed()

        // A suggestion that was not in search results should be gone
        composeTestRule
            .onNodeWithText(SUGGESTIONS[1].title)
            .assertDoesNotExist()
    }

    // ── Request flow ──────────────────────────────────────────────────────────

    @Test
    fun `tapping Request adds item to Meine Wünsche`() {
        composeTestRule.setContent {
            val state by viewModel.state.collectAsState()
            KidstuneTheme {
                DiscoverScreen(state = state, onIntent = viewModel::onIntent)
            }
        }

        val unrequestedTile = SUGGESTIONS.first {
            it.spotifyUri !in viewModel.state.value.requestedUris
        }

        composeTestRule
            .onNodeWithContentDescription("Wünschen: ${unrequestedTile.title}")
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("discover_lazy_column")
            .performScrollToNode(hasText("Meine Wünsche"))
        composeTestRule.onNodeWithText("Meine Wünsche").assertIsDisplayed()
    }

    @Test
    fun `tapping Request fires RequestContent intent`() {
        var capturedIntent: DiscoverIntent? = null
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(
                    state    = DiscoverState(suggestions = SUGGESTIONS, requestedUris = emptySet()),
                    onIntent = { capturedIntent = it }
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Wünschen: ${SUGGESTIONS[2].title}")
            .performClick()

        assertTrue(capturedIntent is DiscoverIntent.RequestContent)
        assertEquals(SUGGESTIONS[2], (capturedIntent as DiscoverIntent.RequestContent).tile)
    }

    // ── 3-request limit ───────────────────────────────────────────────────────

    @Test
    fun `when 3 pending requests exist unrequested buttons show limit message`() {
        val threePending = SUGGESTIONS.take(3).mapIndexed { i, tile ->
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
                        suggestions     = SUGGESTIONS,
                        pendingRequests = threePending,
                        requestedUris   = threePending.map { it.tile.spotifyUri }.toSet()
                    )
                )
            }
        }

        val unrequestedTile = SUGGESTIONS[3]
        composeTestRule
            .onNodeWithContentDescription("Wünschen: ${unrequestedTile.title}")
            .assertDoesNotExist()

        composeTestRule
            .onAllNodesWithText("Du hast schon 3 Wünsche offen", substring = true)
            .onFirst()
            .assertIsDisplayed()
    }

    // ── Mic button ────────────────────────────────────────────────────────────

    @Test
    fun `mic button is present`() {
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(state = DiscoverState(suggestions = SUGGESTIONS))
            }
        }
        composeTestRule.onNodeWithContentDescription("Spracheingabe").assertIsDisplayed()
    }

    // ── Back button ───────────────────────────────────────────────────────────

    @Test
    fun `back button is present`() {
        composeTestRule.setContent {
            KidstuneTheme { DiscoverScreen(state = DiscoverState()) }
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
        assertTrue(navigatedUp)
    }

    // ── Meine Wünsche hidden when empty ───────────────────────────────────────

    @Test
    fun `Meine Wünsche section is hidden when no pending requests`() {
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(state = DiscoverState(pendingRequests = emptyList()))
            }
        }
        composeTestRule.onNodeWithText("Meine Wünsche").assertDoesNotExist()
    }

    // ── Rejected request ──────────────────────────────────────────────────────

    @Test
    fun `rejected request shows X and parent note`() {
        val rejectedRequest = PendingRequest(
            id          = "rej-1",
            tile        = SUGGESTIONS[0],
            status      = RequestStatus.REJECTED,
            requestedAt = Instant.now().minus(2, ChronoUnit.DAYS),
            parentNote  = "Das ist eher was für ältere Kinder."
        )
        composeTestRule.setContent {
            KidstuneTheme {
                DiscoverScreen(
                    state = DiscoverState(pendingRequests = listOf(rejectedRequest))
                )
            }
        }
        composeTestRule.onNodeWithText("❌").assertIsDisplayed()
        composeTestRule.onNodeWithText("Das ist eher was für ältere Kinder.").assertIsDisplayed()
    }

    // ── pendingRequestTimeLabel unit tests ────────────────────────────────────

    @Test
    fun `pendingRequestTimeLabel returns correct label for under 1 hour`() {
        val now = Instant.now()
        assertEquals(
            "Mama/Papa schauen sich das an",
            pendingRequestTimeLabel(now.minus(30, ChronoUnit.MINUTES), now)
        )
    }

    @Test
    fun `pendingRequestTimeLabel returns correct label for 1-24 hours`() {
        val now = Instant.now()
        assertEquals(
            "Gestern gewünscht",
            pendingRequestTimeLabel(now.minus(6, ChronoUnit.HOURS), now)
        )
    }

    @Test
    fun `pendingRequestTimeLabel returns correct label for over 24 hours`() {
        val now = Instant.now()
        assertEquals(
            "Vor ein paar Tagen gewünscht",
            pendingRequestTimeLabel(now.minus(3, ChronoUnit.DAYS), now)
        )
    }
}
