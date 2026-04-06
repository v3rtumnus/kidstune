package at.kidstune.kids.playback

import android.graphics.Bitmap
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Unit tests for [SpotifyMirrorPlayer].
 *
 * Verifies that:
 *  1. [NowPlayingState] is correctly mapped into [SimpleBasePlayer.State] metadata
 *     and playback-state fields.
 *  2. Notification button taps (play/pause, seek, skip) are forwarded to
 *     [PlaybackController].
 *
 * Robolectric is required because [SimpleBasePlayer] internally references
 * [android.os.Looper]. [LooperMode.LEGACY] maps the test thread to the main looper
 * so [SimpleBasePlayer]'s thread-check passes without explicit UI-thread dispatch.
 */
@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.LEGACY)
class SpotifyMirrorPlayerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val mockController: PlaybackController = mockk(relaxed = true)

    private lateinit var player: SpotifyMirrorPlayer

    @Before
    fun setUp() {
        player = SpotifyMirrorPlayer(mockController, testScope)
    }

    // ── State mapping ─────────────────────────────────────────────────────────

    @Test
    fun `initial state is IDLE`() {
        val state = player.getState()
        assertEquals(Player.STATE_IDLE, state.playbackState)
        assertTrue(state.playlist.isEmpty())
    }

    @Test
    fun `updateState with active track produces STATE_READY with correct metadata`() {
        val nowPlaying = activeNowPlaying()

        player.updateState(nowPlaying, artworkBitmap = null)
        val state = player.getState()

        assertEquals(Player.STATE_READY, state.playbackState)
        assertEquals(1, state.playlist.size)
        val meta = state.playlist[0].mediaItem.mediaMetadata
        assertEquals("Test Track", meta.title?.toString())
        assertEquals("Test Artist", meta.artist?.toString())
    }

    @Test
    fun `updateState isPlaying true sets playWhenReady true`() {
        player.updateState(activeNowPlaying(isPlaying = true), null)
        assertTrue(player.getState().playWhenReady)
    }

    @Test
    fun `updateState isPlaying false sets playWhenReady false`() {
        player.updateState(activeNowPlaying(isPlaying = false), null)
        assertEquals(false, player.getState().playWhenReady)
    }

    @Test
    fun `updateState maps durationMs to durationUs in playlist item`() {
        player.updateState(activeNowPlaying(durationMs = 180_000L), null)
        val durationUs = player.getState().playlist[0].durationUs
        assertEquals(180_000L * 1_000L, durationUs)
    }

    @Test
    fun `updateState zero durationMs maps to -1 durationUs (unknown)`() {
        player.updateState(activeNowPlaying(durationMs = 0L), null)
        assertEquals(-1L, player.getState().playlist[0].durationUs)
    }

    @Test
    fun `updateState with artwork bitmap embeds artworkData in metadata`() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        player.updateState(activeNowPlaying(), bitmap)
        val artworkData = player.getState().playlist[0].mediaItem.mediaMetadata.artworkData
        assertNotNull(artworkData)
        assertTrue(artworkData!!.isNotEmpty())
    }

    @Test
    fun `updateState with null bitmap produces no artworkData`() {
        player.updateState(activeNowPlaying(), artworkBitmap = null)
        assertNull(player.getState().playlist[0].mediaItem.mediaMetadata.artworkData)
    }

    @Test
    fun `updateState with null trackUri produces IDLE state`() {
        player.updateState(NowPlayingState(trackUri = null), null)
        assertEquals(Player.STATE_IDLE, player.getState().playbackState)
    }

    @Test
    fun `clearState after active track produces IDLE state`() {
        player.updateState(activeNowPlaying(), null)
        player.clearState()
        assertEquals(Player.STATE_IDLE, player.getState().playbackState)
        assertTrue(player.getState().playlist.isEmpty())
    }

    // ── Command forwarding ────────────────────────────────────────────────────

    @Test
    fun `handleSetPlayWhenReady false forwards pause to PlaybackController`() = testScope.runTest {
        player.handleSetPlayWhenReady(false)
        testScope.testScheduler.advanceUntilIdle()
        coVerify { mockController.pause() }
    }

    @Test
    fun `handleSetPlayWhenReady true forwards resume to PlaybackController`() = testScope.runTest {
        player.handleSetPlayWhenReady(true)
        testScope.testScheduler.advanceUntilIdle()
        coVerify { mockController.resume() }
    }

    @Test
    fun `handleSeek COMMAND_SEEK_TO_NEXT forwards skipNext`() = testScope.runTest {
        player.handleSeek(0, 0L, Player.COMMAND_SEEK_TO_NEXT)
        testScope.testScheduler.advanceUntilIdle()
        coVerify { mockController.skipNext() }
    }

    @Test
    fun `handleSeek COMMAND_SEEK_TO_PREVIOUS forwards skipPrevious`() = testScope.runTest {
        player.handleSeek(0, 0L, Player.COMMAND_SEEK_TO_PREVIOUS)
        testScope.testScheduler.advanceUntilIdle()
        coVerify { mockController.skipPrevious() }
    }

    @Test
    fun `handleSeek COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM forwards seekTo with position`() =
        testScope.runTest {
            player.handleSeek(0, 42_000L, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            testScope.testScheduler.advanceUntilIdle()
            coVerify { mockController.seekTo(42_000L) }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun activeNowPlaying(
        isPlaying: Boolean = true,
        durationMs: Long = 180_000L,
    ) = NowPlayingState(
        trackUri   = "spotify:track:abc123",
        title      = "Test Track",
        artistName = "Test Artist",
        durationMs = durationMs,
        positionMs = 30_000L,
        isPlaying  = isPlaying,
    )
}
