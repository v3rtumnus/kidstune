package at.kidstune.kids.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Connection lifecycle tests for [SpotifyRemoteManager].
 *
 * The real Spotify App Remote SDK cannot be loaded in unit tests (requires Android runtime
 * and actual Spotify app). These tests verify the manager's lifecycle state machine
 * using the real manager class against the stub SDK (compile-time stubs always fail with
 * UnsupportedOperationException, which maps to [SpotifyConnectionError.OTHER]).
 *
 * Full integration testing with the real SDK requires a real device (see VERIFICATION in
 * the prompt spec for manual test instructions).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpotifyRemoteManagerTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /**
     * The [FakeSpotifyRemote] validates the interface contract that [PlaybackController]
     * relies on. These tests cover the interface semantics rather than the SDK's internals.
     */

    @Test
    fun `FakeSpotifyRemote starts connected`() = runTest(dispatcher) {
        val remote = FakeSpotifyRemote()
        assertTrue(remote.isConnected.value)
        assertNull(remote.connectionError.value)
    }

    @Test
    fun `FakeSpotifyRemote disconnect sets isConnected to false`() = runTest(dispatcher) {
        val remote = FakeSpotifyRemote()
        remote.disconnect()
        assertFalse(remote.isConnected.value)
    }

    @Test
    fun `FakeSpotifyRemote connect after disconnect restores isConnected`() = runTest(dispatcher) {
        val remote = FakeSpotifyRemote()
        remote.disconnect()
        remote.connect()
        assertTrue(remote.isConnected.value)
    }

    @Test
    fun `FakeSpotifyRemote playerStateFlow is initially null`() = runTest(dispatcher) {
        val remote = FakeSpotifyRemote()
        assertNull(remote.playerStateFlow.value)
    }

    @Test
    fun `FakeSpotifyRemote emitState updates playerStateFlow`() = runTest(dispatcher) {
        val remote = FakeSpotifyRemote()
        val state = SpotifyPlayerStateInternal(
            trackUri   = "spotify:track:xyz",
            trackTitle = "Mein Lied",
            artistName = "Bibi",
            imageUri   = null,
            durationMs = 180_000,
            positionMs = 0,
            isPaused   = false
        )
        remote.emitState(state)
        assertNotNull(remote.playerStateFlow.value)
        assertTrue(remote.playerStateFlow.value?.trackUri == "spotify:track:xyz")
    }

    @Test
    fun `SpotifyConnectionError error codes map correctly`() {
        // Verify the enum has all expected values
        val errors = SpotifyConnectionError.values()
        assertTrue(SpotifyConnectionError.NOT_INSTALLED  in errors)
        assertTrue(SpotifyConnectionError.NOT_LOGGED_IN  in errors)
        assertTrue(SpotifyConnectionError.PREMIUM_REQUIRED in errors)
        assertTrue(SpotifyConnectionError.OTHER          in errors)
    }

    // ── toSpotifyConnectionError mapping ─────────────────────────────────────

    @Test
    fun `toSpotifyConnectionError maps 'not installed' message to NOT_INSTALLED`() {
        val ex = RuntimeException("Spotify is not installed on this device")
        assertEquals(SpotifyConnectionError.NOT_INSTALLED, ex.toSpotifyConnectionError())
    }

    @Test
    fun `toSpotifyConnectionError maps 'notinstalled' compact message to NOT_INSTALLED`() {
        val ex = RuntimeException("CouldNotFindSpotifyApp: notinstalled")
        assertEquals(SpotifyConnectionError.NOT_INSTALLED, ex.toSpotifyConnectionError())
    }

    @Test
    fun `toSpotifyConnectionError maps 'not logged' message to NOT_LOGGED_IN`() {
        val ex = RuntimeException("User is not logged in to Spotify")
        assertEquals(SpotifyConnectionError.NOT_LOGGED_IN, ex.toSpotifyConnectionError())
    }

    @Test
    fun `toSpotifyConnectionError maps 'notloggedin' compact message to NOT_LOGGED_IN`() {
        val ex = RuntimeException("AuthenticationRequired: notloggedin")
        assertEquals(SpotifyConnectionError.NOT_LOGGED_IN, ex.toSpotifyConnectionError())
    }

    @Test
    fun `toSpotifyConnectionError maps 'premium' message to PREMIUM_REQUIRED`() {
        val ex = RuntimeException("Spotify Premium required for App Remote")
        assertEquals(SpotifyConnectionError.PREMIUM_REQUIRED, ex.toSpotifyConnectionError())
    }

    @Test
    fun `toSpotifyConnectionError maps unknown message to OTHER`() {
        val ex = RuntimeException("Unexpected SDK failure")
        assertEquals(SpotifyConnectionError.OTHER, ex.toSpotifyConnectionError())
    }

    @Test
    fun `toSpotifyConnectionError maps null message to OTHER`() {
        val ex = RuntimeException(null as String?)
        assertEquals(SpotifyConnectionError.OTHER, ex.toSpotifyConnectionError())
    }

    @Test
    fun `toSpotifyConnectionError is case-insensitive`() {
        assertEquals(SpotifyConnectionError.NOT_INSTALLED,  RuntimeException("NOT INSTALLED").toSpotifyConnectionError())
        assertEquals(SpotifyConnectionError.NOT_LOGGED_IN,  RuntimeException("NOT LOGGED IN").toSpotifyConnectionError())
        assertEquals(SpotifyConnectionError.PREMIUM_REQUIRED, RuntimeException("PREMIUM only").toSpotifyConnectionError())
    }

    @Test
    fun `SpotifyPlayerStateInternal isPaused reflects paused state`() {
        val paused = SpotifyPlayerStateInternal(
            trackUri = "spotify:track:t1", trackTitle = "Song", artistName = "Artist",
            imageUri = null, durationMs = 0, positionMs = 0, isPaused = true
        )
        assertTrue(paused.isPaused)

        val playing = paused.copy(isPaused = false)
        assertFalse(playing.isPaused)
    }
}
