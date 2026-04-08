package at.kidstune.kids.playback

import android.graphics.Bitmap
import androidx.media3.common.C
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
 *  1. [NowPlayingState] is correctly mapped into the public [Player] state (metadata,
 *     playback-state fields) via the [SimpleBasePlayer] public API.
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
        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertEquals(0, player.mediaItemCount)
    }

    @Test
    fun `updateState with active track produces STATE_READY with correct metadata`() {
        player.updateState(activeNowPlaying(), artworkBitmap = null)

        assertEquals(Player.STATE_READY, player.playbackState)
        assertEquals(1, player.mediaItemCount)
        val meta = player.mediaMetadata
        assertEquals("Test Track", meta.title?.toString())
        assertEquals("Test Artist", meta.artist?.toString())
    }

    @Test
    fun `updateState isPlaying true sets playWhenReady true`() {
        player.updateState(activeNowPlaying(isPlaying = true), null)
        assertTrue(player.playWhenReady)
    }

    @Test
    fun `updateState isPlaying false sets playWhenReady false`() {
        player.updateState(activeNowPlaying(isPlaying = false), null)
        assertEquals(false, player.playWhenReady)
    }

    @Test
    fun `updateState maps durationMs to player duration`() {
        player.updateState(activeNowPlaying(durationMs = 180_000L), null)
        assertEquals(180_000L, player.duration)
    }

    @Test
    fun `updateState zero durationMs maps to unknown duration`() {
        player.updateState(activeNowPlaying(durationMs = 0L), null)
        // durationMs=0 → durationUs=-1 → player.duration reports C.TIME_UNSET
        assertEquals(C.TIME_UNSET, player.duration)
    }

    @Test
    fun `updateState with artwork bitmap embeds artworkData in metadata`() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        player.updateState(activeNowPlaying(), bitmap)
        val artworkData = player.mediaMetadata.artworkData
        assertNotNull(artworkData)
        assertTrue(artworkData!!.isNotEmpty())
    }

    @Test
    fun `updateState with null bitmap produces no artworkData`() {
        player.updateState(activeNowPlaying(), artworkBitmap = null)
        assertNull(player.mediaMetadata.artworkData)
    }

    @Test
    fun `updateState with null trackUri produces IDLE state`() {
        player.updateState(NowPlayingState(trackUri = null), null)
        assertEquals(Player.STATE_IDLE, player.playbackState)
    }

    @Test
    fun `clearState after active track produces IDLE state`() {
        player.updateState(activeNowPlaying(), null)
        player.clearState()
        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertEquals(0, player.mediaItemCount)
    }

    // ── Command forwarding ────────────────────────────────────────────────────

    @Test
    fun `setPlayWhenReady false forwards pause to PlaybackController`() = testScope.runTest {
        // Must be in STATE_READY for commands to be available
        player.updateState(activeNowPlaying(isPlaying = true), null)
        player.setPlayWhenReady(false)
        testScope.testScheduler.advanceUntilIdle()
        coVerify { mockController.pause() }
    }

    @Test
    fun `setPlayWhenReady true forwards resume to PlaybackController`() = testScope.runTest {
        player.updateState(activeNowPlaying(isPlaying = false), null)
        player.setPlayWhenReady(true)
        testScope.testScheduler.advanceUntilIdle()
        coVerify { mockController.resume() }
    }

    @Test
    fun `seekToNext forwards skipNext`() = testScope.runTest {
        player.updateState(activeNowPlaying(), null)
        player.seekToNext()
        testScope.testScheduler.advanceUntilIdle()
        coVerify { mockController.skipNext() }
    }

    @Test
    fun `seekToPrevious forwards skipPrevious`() = testScope.runTest {
        player.updateState(activeNowPlaying(), null)
        player.seekToPrevious()
        testScope.testScheduler.advanceUntilIdle()
        coVerify { mockController.skipPrevious() }
    }

    @Test
    fun `seekTo forwards seekTo with position`() = testScope.runTest {
        player.updateState(activeNowPlaying(), null)
        player.seekTo(42_000L)
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
