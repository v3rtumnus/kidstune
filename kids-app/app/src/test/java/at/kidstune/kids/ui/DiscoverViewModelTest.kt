package at.kidstune.kids.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.local.ContentDao
import at.kidstune.kids.data.local.ContentRequestDao
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.local.entities.LocalContentRequest
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.data.remote.dto.ContentRequestResponseDto
import at.kidstune.kids.data.remote.dto.CreateContentRequestDto
import at.kidstune.kids.data.remote.dto.DiscoverItemDto
import at.kidstune.kids.data.remote.dto.SearchResultsDto
import at.kidstune.kids.data.repository.DiscoverRepository
import at.kidstune.kids.data.repository.FavoriteRepository
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import at.kidstune.kids.domain.model.DiscoverTile
import at.kidstune.kids.domain.model.RequestStatus
import at.kidstune.kids.ui.viewmodel.DiscoverIntent
import at.kidstune.kids.ui.viewmodel.DiscoverViewModel
import at.kidstune.kids.ui.viewmodel.pendingRequestTimeLabel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.temporal.ChronoUnit

// ── Fake DAO implementations ───────────────────────────────────────────────────

private class FakeDiscoverContentDao(
    private val existingUris: List<String> = emptyList(),
    private val countFlow: MutableStateFlow<Int> = MutableStateFlow(0),
) : ContentDao {
    override suspend fun insertAll(entries: List<LocalContentEntry>) {}
    override fun getAll(profileId: String): Flow<List<LocalContentEntry>> = flowOf(emptyList())
    override fun getByType(profileId: String, type: ContentType): Flow<List<LocalContentEntry>> = flowOf(emptyList())
    override suspend fun getById(id: String): LocalContentEntry? = null
    override suspend fun deleteById(id: String) {}
    override suspend fun deleteAll(profileId: String) {}
    override suspend fun countByType(profileId: String, type: ContentType): Int = 0
    override fun countAllFlow(profileId: String): Flow<Int> = countFlow
    override suspend fun getExistingUris(profileId: String): List<String> = existingUris
}

private class FakeContentRequestDao(
    private val requestsFlow: MutableStateFlow<List<LocalContentRequest>> = MutableStateFlow(emptyList())
) : ContentRequestDao {
    val inserted = mutableListOf<LocalContentRequest>()

    override suspend fun insert(request: LocalContentRequest) {
        inserted += request
        requestsFlow.value = requestsFlow.value + request
    }
    override suspend fun update(request: LocalContentRequest) {}
    override fun getVisible(profileId: String): Flow<List<LocalContentRequest>> = requestsFlow
    override suspend fun getPendingUpload(profileId: String): List<LocalContentRequest> =
        requestsFlow.value.filter { it.status == "PENDING_UPLOAD" }
    override suspend fun countPending(profileId: String): Int =
        requestsFlow.value.count { it.status == "PENDING" }
    override suspend fun deleteApprovedAndExpired(profileId: String) {}
    override suspend fun deleteById(id: String) {
        requestsFlow.value = requestsFlow.value.filter { it.id != id }
    }
    override suspend fun updateStatusAndServerId(id: String, status: String, serverId: String?) {
        requestsFlow.value = requestsFlow.value.map {
            if (it.id == id) it.copy(status = status, serverId = serverId) else it
        }
    }
    override suspend fun updateByServerId(serverId: String, status: String, parentNote: String?) {}
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private val PROFILE_ID = "profile-discover-test"

private fun fakeTile(uri: String, scope: ContentScope = ContentScope.ALBUM) = DiscoverTile(
    spotifyUri = uri,
    title      = "Title $uri",
    artistName = "Artist",
    imageUrl   = null,
    type       = ContentType.MUSIC,
    scope      = scope,
)

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoverViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var contentDao: FakeDiscoverContentDao
    private lateinit var requestDao: FakeContentRequestDao
    private lateinit var prefs: ProfilePreferences
    private lateinit var discoverRepository: DiscoverRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = ProfilePreferences(context).also { it.boundProfileId = PROFILE_ID }
        contentDao  = FakeDiscoverContentDao()
        requestDao  = FakeContentRequestDao()

        discoverRepository = buildFakeRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ── Time-label helper ─────────────────────────────────────────────────────

    @Test
    fun `pendingRequestTimeLabel under 1 hour`() {
        val now = Instant.now()
        assertEquals("Mama/Papa schauen sich das an",
            pendingRequestTimeLabel(now.minus(30, ChronoUnit.MINUTES), now))
    }

    @Test
    fun `pendingRequestTimeLabel 1–24 hours`() {
        val now = Instant.now()
        assertEquals("Gestern gewünscht",
            pendingRequestTimeLabel(now.minus(6, ChronoUnit.HOURS), now))
    }

    @Test
    fun `pendingRequestTimeLabel over 24 hours`() {
        val now = Instant.now()
        assertEquals("Vor ein paar Tagen gewünscht",
            pendingRequestTimeLabel(now.minus(3, ChronoUnit.DAYS), now))
    }

    // ── Already-approved filter ───────────────────────────────────────────────

    @Test
    fun `already-approved filter removes approved URIs from suggestions`() = runTest(testDispatcher) {
        val approvedUri = "spotify:album:approved"
        val newUri      = "spotify:album:new"
        contentDao = FakeDiscoverContentDao(existingUris = listOf(approvedUri))
        discoverRepository = buildFakeRepository(
            suggestionsItems = listOf(
                DiscoverItemDto("a1", "Approved Album", null, approvedUri, null),
                DiscoverItemDto("a2", "New Album",      null, newUri,      null),
            )
        )

        val vm = buildViewModel()
        advanceUntilIdle()

        val suggestions = vm.state.value.suggestions
        assertFalse("Approved URI must be absent", suggestions.any { it.spotifyUri == approvedUri })
        assertTrue("New URI must be present",      suggestions.any { it.spotifyUri == newUri })
    }

    @Test
    fun `already-approved filter removes approved URIs from search results`() = runTest(testDispatcher) {
        val approvedUri = "spotify:album:already-here"
        contentDao = FakeDiscoverContentDao(existingUris = listOf(approvedUri))
        discoverRepository = buildFakeRepository(
            searchAlbums = listOf(
                DiscoverItemDto("s1", "Already Here", null, approvedUri, null),
                DiscoverItemDto("s2", "New Result",   null, "spotify:album:new-result", null),
            )
        )

        val vm = buildViewModel()
        vm.onIntent(DiscoverIntent.UpdateQuery("test"))
        advanceUntilIdle()

        val results = vm.state.value.searchResults
        assertFalse(results.any { it.spotifyUri == approvedUri })
        assertTrue(results.any { it.spotifyUri == "spotify:album:new-result" })
    }

    // ── Search rate-limiting ──────────────────────────────────────────────────

    @Test
    fun `second search within 5s shows rate-limit message`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        // First search
        vm.onIntent(DiscoverIntent.UpdateQuery("Bibi"))
        advanceUntilIdle()
        assertNull("No rate-limit message expected for first search",
            vm.state.value.rateLimitMessage)

        // Second search immediately after (< 5 s)
        vm.onIntent(DiscoverIntent.UpdateQuery("Tina"))
        advanceUntilIdle()

        assertNotNull("Rate-limit message expected for second search",
            vm.state.value.rateLimitMessage)
    }

    @Test
    fun `search after 5s cooldown clears rate-limit message`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onIntent(DiscoverIntent.UpdateQuery("Bibi"))
        advanceUntilIdle()

        // Simulate wall-clock time passing by backdating lastSearchAt directly
        vm.lastSearchAt = vm.lastSearchAt.minusSeconds(6)

        vm.onIntent(DiscoverIntent.UpdateQuery("Tina"))
        advanceUntilIdle()

        assertNull("Rate-limit must be cleared after cooldown",
            vm.state.value.rateLimitMessage)
    }

    // ── Celebration on Room count increase ────────────────────────────────────

    @Test
    fun `showCelebration triggered when content count increases with pending requests`() = runTest(testDispatcher) {
        val countFlow = MutableStateFlow(5)
        contentDao = FakeDiscoverContentDao(countFlow = countFlow)

        // Seed a pending request so pendingUrisSnapshot is non-empty
        val requestsFlow = MutableStateFlow<List<LocalContentRequest>>(listOf(
            LocalContentRequest(
                id          = "r1",
                profileId   = PROFILE_ID,
                spotifyUri  = "spotify:album:pending-one",
                title       = "Pending Album",
                imageUrl    = null,
                artistName  = null,
                contentType = "MUSIC",
                status      = "PENDING",
                requestedAt = Instant.now(),
            )
        ))
        requestDao = FakeContentRequestDao(requestsFlow)
        discoverRepository = buildFakeRepository()

        val vm = buildViewModel()
        advanceUntilIdle()

        assertFalse("No celebration yet", vm.state.value.showCelebration)

        // Simulate approval: content count increases
        countFlow.value = 6
        advanceUntilIdle()

        assertTrue("Celebration should show when count increases with pending requests",
            vm.state.value.showCelebration)
    }

    @Test
    fun `showCelebration not triggered without pending requests`() = runTest(testDispatcher) {
        val countFlow = MutableStateFlow(5)
        contentDao = FakeDiscoverContentDao(countFlow = countFlow)
        discoverRepository = buildFakeRepository()

        val vm = buildViewModel()
        advanceUntilIdle()

        // Increase count but no pending requests exist
        countFlow.value = 6
        advanceUntilIdle()

        assertFalse("No celebration without pending requests",
            vm.state.value.showCelebration)
    }

    @Test
    fun `DismissCelebration hides the overlay`() = runTest(testDispatcher) {
        val countFlow = MutableStateFlow(5)
        val requestsFlow = MutableStateFlow<List<LocalContentRequest>>(listOf(
            LocalContentRequest("r1", null, PROFILE_ID, "spotify:album:x", "X", null, null, "MUSIC", "PENDING", Instant.now())
        ))
        contentDao  = FakeDiscoverContentDao(countFlow = countFlow)
        requestDao  = FakeContentRequestDao(requestsFlow)
        discoverRepository = buildFakeRepository()

        val vm = buildViewModel()
        advanceUntilIdle()
        countFlow.value = 6
        advanceUntilIdle()

        assertTrue(vm.state.value.showCelebration)
        vm.onIntent(DiscoverIntent.DismissCelebration)
        advanceUntilIdle()
        assertFalse(vm.state.value.showCelebration)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildViewModel() = DiscoverViewModel(discoverRepository, contentDao, prefs)

    private fun buildFakeRepository(
        suggestionsItems: List<DiscoverItemDto> = emptyList(),
        searchAlbums: List<DiscoverItemDto>     = emptyList(),
    ): DiscoverRepository {
        val fakeClient = mockk<KidstuneApiClient>(relaxed = true)
        coEvery { fakeClient.fetchSuggestions(any()) } returns suggestionsItems
        coEvery { fakeClient.searchContent(any(), any()) } returns SearchResultsDto(albums = searchAlbums)
        coEvery { fakeClient.createContentRequest(any(), any()) } returns ContentRequestResponseDto(
            id          = "srv-id",
            profileId   = PROFILE_ID,
            spotifyUri  = "spotify:album:x",
            title       = "X",
            contentType = "MUSIC",
            status      = "PENDING",
            requestedAt = Instant.now().toString(),
        )
        val fakeFavRepo = mockk<FavoriteRepository>(relaxed = true)
        return DiscoverRepository(fakeClient, contentDao, requestDao)
    }
}
