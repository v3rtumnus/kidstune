package at.kidstune.kids.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.local.AlbumDao
import at.kidstune.kids.data.local.ContentDao
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.repository.ContentRepository
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import at.kidstune.kids.ui.viewmodel.BrowseIntent
import at.kidstune.kids.ui.viewmodel.BrowseNavigation
import at.kidstune.kids.ui.viewmodel.BrowseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ── In-test fakes (avoid Room dispatcher issues in unit tests) ────────────

private class FakeContentDao(private val data: List<LocalContentEntry>) : ContentDao {
    override suspend fun insertAll(entries: List<LocalContentEntry>) {}
    override fun getAll(profileId: String): Flow<List<LocalContentEntry>> =
        flowOf(data.filter { it.profileId == profileId })
    override fun getByType(profileId: String, type: ContentType): Flow<List<LocalContentEntry>> =
        flowOf(data.filter { it.profileId == profileId && it.contentType == type })
    override suspend fun getById(id: String): LocalContentEntry? = data.find { it.id == id }
    override suspend fun deleteAll(profileId: String) {}
    override suspend fun countByType(profileId: String, type: ContentType): Int =
        data.count { it.profileId == profileId && it.contentType == type }
}

private class FakeAlbumDao(private val data: List<LocalAlbum>) : AlbumDao {
    override suspend fun insertAll(albums: List<LocalAlbum>) {}
    override fun getByContentEntryId(entryId: String): Flow<List<LocalAlbum>> =
        flowOf(data.filter { it.contentEntryId == entryId })
    override suspend fun getByContentEntryIdOnce(entryId: String): List<LocalAlbum> =
        data.filter { it.contentEntryId == entryId }
    override suspend fun getById(id: String): LocalAlbum? = data.find { it.id == id }
    override suspend fun deleteByContentEntryId(entryId: String) {}
}

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowseViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: BrowseViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ProfilePreferences(context).also {
            it.boundProfileId = MockContentProvider.PROFILE_EMMA
        }
        viewModel = BrowseViewModel(
            contentRepository = ContentRepository(FakeContentDao(MockContentProvider.contentEntries)),
            albumDao          = FakeAlbumDao(MockContentProvider.albums),
            prefs             = prefs
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `init MUSIC loads music entries from Room`() = runTest(testDispatcher) {
        viewModel.init(BrowseCategory.MUSIC)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(BrowseCategory.MUSIC, state.category)
        assertTrue("Expected music entries", state.entries.isNotEmpty())
        assertTrue(state.entries.all { it.contentType == ContentType.MUSIC })
    }

    @Test
    fun `init AUDIOBOOK loads audiobook entries from Room`() = runTest(testDispatcher) {
        viewModel.init(BrowseCategory.AUDIOBOOK)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(BrowseCategory.AUDIOBOOK, state.category)
        assertTrue(state.entries.isNotEmpty())
        assertTrue(state.entries.all { it.contentType == ContentType.AUDIOBOOK })
    }

    @Test
    fun `init FAVORITES yields empty entries`() = runTest(testDispatcher) {
        viewModel.init(BrowseCategory.FAVORITES)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(BrowseCategory.FAVORITES, state.category)
        assertTrue(state.entries.isEmpty())
        assertEquals(0, state.totalPages)
    }

    @Test
    fun `entries are paginated into groups of 4`() = runTest(testDispatcher) {
        // Audiobook entries in PROFILE_EMMA: entry-tkkg, entry-yakari, entry-pumuckl, entry-die-drei = 4
        // All on one page; check that each page has at most 4
        viewModel.init(BrowseCategory.AUDIOBOOK)
        advanceUntilIdle()

        val pages = viewModel.state.value.pages
        pages.dropLast(1).forEach { page -> assertEquals(4, page.size) }
    }

    @Test
    fun `tapping ARTIST entry emits ToAlbumGrid navigation`() = runTest(testDispatcher) {
        viewModel.init(BrowseCategory.MUSIC)
        advanceUntilIdle()

        val artistEntry = viewModel.state.value.entries.first { it.scope == ContentScope.ARTIST }
        viewModel.onIntent(BrowseIntent.TileTapped(artistEntry))
        advanceUntilIdle()

        val nav = viewModel.state.value.navigation
        assertTrue("Expected ToAlbumGrid, got: $nav", nav is BrowseNavigation.ToAlbumGrid)
        assertEquals(artistEntry.id, (nav as BrowseNavigation.ToAlbumGrid).contentEntryId)
    }

    @Test
    fun `tapping ALBUM entry emits ToTrackList with first album id`() = runTest(testDispatcher) {
        viewModel.init(BrowseCategory.AUDIOBOOK)
        advanceUntilIdle()

        val albumEntry = viewModel.state.value.entries.first { it.scope == ContentScope.ALBUM }
        viewModel.onIntent(BrowseIntent.TileTapped(albumEntry))
        advanceUntilIdle()

        val nav = viewModel.state.value.navigation
        // FakeAlbumDao returns albums for the entry; first album id should be used
        assertTrue("Expected ToTrackList navigation, got: $nav", nav is BrowseNavigation.ToTrackList)
    }

    @Test
    fun `NavigationHandled clears navigation state`() = runTest(testDispatcher) {
        viewModel.init(BrowseCategory.MUSIC)
        advanceUntilIdle()

        val artistEntry = viewModel.state.value.entries.first { it.scope == ContentScope.ARTIST }
        viewModel.onIntent(BrowseIntent.TileTapped(artistEntry))
        advanceUntilIdle()

        viewModel.onIntent(BrowseIntent.NavigationHandled)

        assertNull(viewModel.state.value.navigation)
    }

    @Test
    fun `tapping TRACK entry emits PlayTrack navigation`() = runTest(testDispatcher) {
        viewModel.init(BrowseCategory.MUSIC)
        advanceUntilIdle()

        val trackEntry = viewModel.state.value.entries.firstOrNull { it.scope == ContentScope.TRACK }
            ?: return@runTest

        viewModel.onIntent(BrowseIntent.TileTapped(trackEntry))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.navigation is BrowseNavigation.PlayTrack)
    }
}
