package at.kidstune.kids.ui

import at.kidstune.kids.ui.viewmodel.NowPlayingIntent
import at.kidstune.kids.ui.viewmodel.NowPlayingViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NowPlayingViewModelTest {

    private lateinit var viewModel: NowPlayingViewModel

    @Before
    fun setUp() {
        viewModel = NowPlayingViewModel()
    }

    @Test
    fun `initial state has isFavorite false and isPlaying true`() {
        val state = viewModel.state.value
        assertFalse(state.isFavorite)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `ToggleFavorite sets isFavorite to true`() {
        viewModel.onIntent(NowPlayingIntent.ToggleFavorite)
        assertTrue(viewModel.state.value.isFavorite)
    }

    @Test
    fun `ToggleFavorite twice returns isFavorite to false`() {
        viewModel.onIntent(NowPlayingIntent.ToggleFavorite)
        viewModel.onIntent(NowPlayingIntent.ToggleFavorite)
        assertFalse(viewModel.state.value.isFavorite)
    }

    @Test
    fun `TogglePlayPause flips isPlaying`() {
        viewModel.onIntent(NowPlayingIntent.TogglePlayPause)
        assertFalse(viewModel.state.value.isPlaying)

        viewModel.onIntent(NowPlayingIntent.TogglePlayPause)
        assertTrue(viewModel.state.value.isPlaying)
    }

    @Test
    fun `SkipForward and SkipBack are no-ops in mock`() {
        val before = viewModel.state.value
        viewModel.onIntent(NowPlayingIntent.SkipForward)
        viewModel.onIntent(NowPlayingIntent.SkipBack)
        val after = viewModel.state.value

        // Only immutable mock fields – state unchanged
        assertTrue(before.title == after.title)
        assertTrue(before.progressMs == after.progressMs)
    }

    @Test
    fun `progress and duration match expected mock values`() {
        val state = viewModel.state.value
        // 1:23 = 83s = 83 000 ms
        assertTrue(state.progressMs == 83_000L)
        // 3:45 = 225s = 225 000 ms
        assertTrue(state.durationMs == 225_000L)
    }
}
