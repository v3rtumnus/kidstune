package at.kidstune.kids.playback

import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.domain.model.ContentType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import at.kidstune.kids.data.preferences.ProfilePreferences

/**
 * Verifies correct behaviour when Samsung Kids pauses/resumes/restarts KidsTune.
 *
 * Samsung Kids containment model (relevant to these tests):
 *  - Only KidsTune Kids is added to Samsung Kids' allowed app list.
 *  - Spotify is NOT in the allowed list but runs as a background service.
 *  - When time limit is reached or the parent temporarily pauses Samsung Kids,
 *    the activity receives onStop → (later) onStart, with onDestroy only on a
 *    full Samsung Kids restart.
 *
 * Since MainActivity cannot be directly unit-tested (requires Android runtime),
 * these tests exercise the two lifecycle-sensitive collaborators directly:
 *  - [SpotifyRemote] (connection idempotency & reconnect after pause)
 *  - [PlaybackController] (position persistence on background + resume)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SamsungKidsLifecycleTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = TestScope(dispatcher)

    private lateinit var fakeRemote: FakeSpotifyRemote
    private val fakeTrackDao = FakeTrackDao()
    private val fakeAlbumDao = FakeAlbumDao()
    private val fakePositionDao = FakePositionDao()
    private val fakeFavoriteDao = FakeFavoriteDao()
    private val prefs = mockk<ProfilePreferences>(relaxed = true)

    private lateinit var controller: PlaybackController

    @BeforeEach
    fun setUp() {
        fakeRemote = FakeSpotifyRemote()
        fakeTrackDao.tracksByUri.clear()
        fakeAlbumDao.albumsById.clear()
        fakePositionDao.lastUpsert = null
        fakeRemote.reset()
        every { prefs.boundProfileId } returns "profile-1"

        controller = PlaybackController(
            spotifyRemote       = fakeRemote,
            trackDao            = fakeTrackDao,
            albumDao            = fakeAlbumDao,
            playbackPositionDao = fakePositionDao,
            favoriteDao         = fakeFavoriteDao,
            prefs               = prefs,
            scope               = scope
        )
    }

    // ── Connection idempotency ─────────────────────────────────────────────────

    @Test
    fun `connect is idempotent – calling it when already connected has no side effects`() {
        // FakeSpotifyRemote starts connected.
        assertTrue(fakeRemote.isConnected.value)

        // MainActivity.onStart() calls connect() every time the app is foregrounded.
        fakeRemote.connect()
        fakeRemote.connect()

        // Still connected, no error.
        assertTrue(fakeRemote.isConnected.value)
        assertNull(fakeRemote.connectionError.value)
    }

    @Test
    fun `connect after Samsung Kids pause restores isConnected`() {
        // Simulate Samsung Kids pausing (onStop → Spotify drops connection)
        fakeRemote.disconnect()
        assertFalse(fakeRemote.isConnected.value)

        // Simulate Samsung Kids resume (onStart → MainActivity calls connect())
        fakeRemote.connect()
        assertTrue(fakeRemote.isConnected.value)
    }

    @Test
    fun `connect after full Samsung Kids restart restores isConnected`() {
        // Full restart: onDestroy was called (disconnect), then fresh onCreate + onStart
        fakeRemote.disconnect()
        fakeRemote.connect()
        assertTrue(fakeRemote.isConnected.value)
        assertNull(fakeRemote.connectionError.value)
    }

    // ── Position persistence on app pause ────────────────────────────────────

    @Test
    fun `onBackground persists current playback position when Samsung Kids pauses app`() =
        runTest(dispatcher) {
            seedTrack("spotify:track:t1", "album1", "spotify:album:abc", positionIndex = 2)

            // Child is listening – Spotify emits a playing state
            fakeRemote.emitState(
                SpotifyPlayerStateInternal(
                    trackUri   = "spotify:track:t1",
                    trackTitle = "Mein Lied",
                    artistName = "Bibi",
                    imageUri   = null,
                    durationMs = 180_000,
                    positionMs = 62_000,
                    isPaused   = false
                )
            )

            // Samsung Kids time limit reached → MainActivity.onStop() calls onBackground()
            controller.onBackground()

            val saved = fakePositionDao.lastUpsert
            assertNotNull(saved)
            assertEquals("profile-1",         saved!!.profileId)
            assertEquals("spotify:album:abc", saved.contextUri)
            assertEquals("spotify:track:t1",  saved.trackUri)
            assertEquals(2,                   saved.trackIndex)
            assertEquals(62_000L,             saved.positionMs)
        }

    @Test
    fun `onBackground when nothing is playing does not persist a position`() = runTest(dispatcher) {
        // No player state emitted – nowPlaying.trackUri is null
        controller.onBackground()
        assertNull(fakePositionDao.lastUpsert)
    }

    // ── State preservation across Samsung Kids restart ─────────────────────

    @Test
    fun `position saved before restart can be retrieved after fresh launch`() =
        runTest(dispatcher) {
            seedTrack("spotify:track:t2", "album2", "spotify:album:xyz", positionIndex = 0)

            fakeRemote.emitState(
                SpotifyPlayerStateInternal(
                    trackUri   = "spotify:track:t2",
                    trackTitle = "Chapter 1",
                    artistName = "Narrator",
                    imageUri   = null,
                    durationMs = 900_000,
                    positionMs = 300_000,
                    isPaused   = false
                )
            )

            // App is stopped by Samsung Kids
            controller.onBackground()

            // Simulate restart: new controller instance reads the persisted row
            val restoredPosition = fakePositionDao.getByProfileId("profile-1")
            assertNotNull(restoredPosition)
            assertEquals("spotify:track:t2",  restoredPosition!!.trackUri)
            assertEquals(300_000L,             restoredPosition.positionMs)
        }

    // ── Audio focus (Spotify SDK owns focus) ─────────────────────────────────

    @Test
    fun `when Spotify emits paused state (audio focus loss) nowPlaying isPlaying is false`() =
        runTest(dispatcher) {
            // The Spotify App Remote SDK pauses automatically on AUDIOFOCUS_LOSS.
            // KidsTune observes the resulting player state and updates its UI.
            seedTrack("spotify:track:t3", "album3", "spotify:album:focus", positionIndex = 0)

            fakeRemote.emitState(
                SpotifyPlayerStateInternal(
                    trackUri   = "spotify:track:t3",
                    trackTitle = "Song",
                    artistName = "Artist",
                    imageUri   = null,
                    durationMs = 120_000,
                    positionMs = 45_000,
                    isPaused   = true   // Spotify paused due to audio focus loss
                )
            )

            assertFalse(controller.nowPlaying.value.isPlaying)
        }

    @Test
    fun `when Spotify emits playing state (audio focus regained) nowPlaying isPlaying is true`() =
        runTest(dispatcher) {
            seedTrack("spotify:track:t3", "album3", "spotify:album:focus", positionIndex = 0)

            // First: paused (focus lost)
            fakeRemote.emitState(
                SpotifyPlayerStateInternal(
                    trackUri = "spotify:track:t3", trackTitle = "Song", artistName = "Artist",
                    imageUri = null, durationMs = 120_000, positionMs = 45_000, isPaused = true
                )
            )
            assertFalse(controller.nowPlaying.value.isPlaying)

            // Then: Spotify SDK regains focus and resumes automatically
            fakeRemote.emitState(
                SpotifyPlayerStateInternal(
                    trackUri = "spotify:track:t3", trackTitle = "Song", artistName = "Artist",
                    imageUri = null, durationMs = 120_000, positionMs = 45_000, isPaused = false
                )
            )
            assertTrue(controller.nowPlaying.value.isPlaying)
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun seedTrack(trackUri: String, albumId: String, albumUri: String, positionIndex: Int) {
        fakeTrackDao.tracksByUri[trackUri] = LocalTrack(
            id = trackUri, albumId = albumId, spotifyTrackUri = trackUri,
            title = "Track", artistName = "Artist", durationMs = 180_000,
            trackNumber = positionIndex + 1, discNumber = 1, imageUrl = null
        )
        fakeAlbumDao.albumsById[albumId] = LocalAlbum(
            id = albumId, contentEntryId = "entry-$albumId",
            spotifyAlbumUri = albumUri, title = "Album",
            imageUrl = null, releaseDate = null, totalTracks = 10,
            contentType = ContentType.MUSIC
        )
        fakeTrackDao.indexByUri[trackUri] = positionIndex
    }
}
