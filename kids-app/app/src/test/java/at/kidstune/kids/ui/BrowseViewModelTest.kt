package at.kidstune.kids.ui

import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.ui.viewmodel.BrowseViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrowseViewModelTest {

    private lateinit var viewModel: BrowseViewModel

    @Before
    fun setUp() {
        viewModel = BrowseViewModel()
    }

    @Test
    fun `init MUSIC loads music tiles`() {
        viewModel.init(BrowseCategory.MUSIC)

        val state = viewModel.state.value
        assertEquals(BrowseCategory.MUSIC, state.category)
        assertEquals(MockContentProvider.mockMusicTiles, state.tiles)
        assertTrue(state.pages.isNotEmpty())
    }

    @Test
    fun `init AUDIOBOOK loads audiobook tiles`() {
        viewModel.init(BrowseCategory.AUDIOBOOK)

        val state = viewModel.state.value
        assertEquals(BrowseCategory.AUDIOBOOK, state.category)
        assertEquals(MockContentProvider.mockAudiobookTiles, state.tiles)
        assertTrue(state.pages.isNotEmpty())
    }

    @Test
    fun `init FAVORITES yields empty tiles`() {
        viewModel.init(BrowseCategory.FAVORITES)

        val state = viewModel.state.value
        assertEquals(BrowseCategory.FAVORITES, state.category)
        assertTrue(state.tiles.isEmpty())
        assertTrue(state.pages.isEmpty())
        assertEquals(0, state.totalPages)
    }

    @Test
    fun `pages are chunked to 4 tiles each`() {
        viewModel.init(BrowseCategory.MUSIC)

        val pages = viewModel.state.value.pages
        pages.dropLast(1).forEach { page ->
            assertEquals(4, page.size)
        }
    }

    @Test
    fun `totalPages matches ceil of tile count divided by 4`() {
        viewModel.init(BrowseCategory.MUSIC)
        val state = viewModel.state.value
        val expected = (state.tiles.size + 3) / 4
        assertEquals(expected, state.totalPages)
    }
}
