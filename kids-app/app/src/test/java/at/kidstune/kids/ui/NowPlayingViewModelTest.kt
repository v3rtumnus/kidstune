package at.kidstune.kids.ui

import at.kidstune.kids.data.local.TrackDao
import at.kidstune.kids.domain.usecase.ToggleFavoriteUseCase
import at.kidstune.kids.playback.NowPlayingState
import at.kidstune.kids.playback.PlaybackController
import at.kidstune.kids.playback.SpotifyRemote
import at.kidstune.kids.ui.viewmodel.NowPlayingIntent
import at.kidstune.kids.ui.viewmodel.NowPlayingViewModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: NowPlayingViewModel
    private lateinit var playbackController: PlaybackController

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val nowPlayingFlow = MutableStateFlow(
            NowPlayingState(
                title      = "Bibi & Tina – Folge 1",
                artistName = "Bibi & Tina",
                isPlaying  = true,
                isFavorite = false,
                positionMs = 83_000L,
                durationMs = 225_000L
            )
        )

        playbackController = mockk(relaxed = true) {
            every { nowPlaying } returns nowPlayingFlow
            every { spotifyRemote } returns mockk<SpotifyRemote>(relaxed = true)
        }

        viewModel = NowPlayingViewModel(
            playbackController   = playbackController,
            toggleFavoriteUseCase = mockk(relaxed = true),
            trackDao             = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `initial state has isFavorite false and isPlaying true`() = runTest(testDispatcher) {
        val state = viewModel.state.value
        assertFalse(state.isFavorite)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `TogglePlayPause on playing track calls pause`() = runTest(testDispatcher) {
        // Initial state: isPlaying = true
        viewModel.onIntent(NowPlayingIntent.TogglePlayPause)
        coVerify { playbackController.pause() }
    }

    @Test
    fun `SkipForward calls skipNext on PlaybackController`() = runTest(testDispatcher) {
        viewModel.onIntent(NowPlayingIntent.SkipForward)
        coVerify { playbackController.skipNext() }
    }

    @Test
    fun `SkipBack calls skipPrevious on PlaybackController`() = runTest(testDispatcher) {
        viewModel.onIntent(NowPlayingIntent.SkipBack)
        coVerify { playbackController.skipPrevious() }
    }

    @Test
    fun `nowPlaying state has correct title and artist`() = runTest(testDispatcher) {
        val state = viewModel.state.value
        assertTrue(state.title == "Bibi & Tina – Folge 1")
        assertTrue(state.artistName == "Bibi & Tina")
    }

    @Test
    fun `nowPlaying positionMs and durationMs match injected values`() = runTest(testDispatcher) {
        val state = viewModel.state.value
        assertTrue(state.positionMs == 83_000L)
        assertTrue(state.durationMs == 225_000L)
    }
}
