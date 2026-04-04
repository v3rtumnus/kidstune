package at.kidstune.kids.playback

import at.kidstune.kids.data.local.AlbumDao
import at.kidstune.kids.data.local.FavoriteDao
import at.kidstune.kids.data.local.PlaybackPositionDao
import at.kidstune.kids.data.local.TrackDao
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalFavorite
import at.kidstune.kids.data.local.entities.LocalPlaybackPosition
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.domain.model.ContentType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [PlaybackController].
 *
 * Spotify SDK interaction is isolated behind [FakeSpotifyRemote] which records all calls
 * in order. DAOs are hand-written fakes. ProfilePreferences is mocked with MockK.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackControllerTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeRemote: FakeSpotifyRemote
    private val fakeTrackDao   = FakeTrackDao()
    private val fakeAlbumDao   = FakeAlbumDao()
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
            scope               = TestScope(dispatcher)
        )
    }

    // ── playFromChapter ────────────────────────────────────────────────────

    @Test
    fun `playFromChapter calls play then skipToIndex in that order`() = runTest(dispatcher) {
        controller.playFromChapter("spotify:album:abc", 2)

        assertEquals(listOf("spotify:album:abc"), fakeRemote.playCalls)
        assertEquals(listOf("spotify:album:abc" to 2), fakeRemote.skipToIndexCalls)
        assertTrue(
            fakeRemote.callOrder.indexOf("play") < fakeRemote.callOrder.indexOf("skipToIndex"),
            "play must be called before skipToIndex"
        )
    }

    @Test
    fun `playAlbumFromStart calls play(albumUri) then skipToIndex(albumUri, 0)`() = runTest(dispatcher) {
        controller.playAlbumFromStart("spotify:album:xyz")

        assertEquals(listOf("spotify:album:xyz"), fakeRemote.playCalls)
        assertEquals(listOf("spotify:album:xyz" to 0), fakeRemote.skipToIndexCalls)
    }

    @Test
    fun `playFromPlaylist calls play then skipToIndex on playlist URI in order`() = runTest(dispatcher) {
        controller.playFromPlaylist("spotify:playlist:pl1", 3)

        assertEquals(listOf("spotify:playlist:pl1"), fakeRemote.playCalls)
        assertEquals(listOf("spotify:playlist:pl1" to 3), fakeRemote.skipToIndexCalls)
        assertTrue(
            fakeRemote.callOrder.indexOf("play") < fakeRemote.callOrder.indexOf("skipToIndex"),
            "play must be called before skipToIndex"
        )
    }

    // ── state transitions ─────────────────────────────────────────────────

    @Test
    fun `nowPlaying isPlaying is true when Spotify emits non-paused state`() = runTest(dispatcher) {
        fakeRemote.emitState(buildState("spotify:track:t1", isPaused = false))
        assertTrue(controller.nowPlaying.value.isPlaying)
    }

    @Test
    fun `nowPlaying isPlaying is false when Spotify emits paused state`() = runTest(dispatcher) {
        fakeRemote.emitState(buildState("spotify:track:t1", isPaused = true))
        assertTrue(!controller.nowPlaying.value.isPlaying)
    }

    @Test
    fun `pause delegates to SpotifyRemote`() = runTest(dispatcher) {
        controller.pause()
        assertEquals(1, fakeRemote.pauseCalls)
    }

    @Test
    fun `resume delegates to SpotifyRemote`() = runTest(dispatcher) {
        controller.resume()
        assertEquals(1, fakeRemote.resumeCalls)
    }

    // ── position persistence ───────────────────────────────────────────────

    @Test
    fun `pause persists position with correct context_uri, track_uri, track_index, position_ms`() =
        runTest(dispatcher) {
            fakeTrackDao.tracksByUri["spotify:track:t1"] = LocalTrack(
                id = "t1", albumId = "album1", spotifyTrackUri = "spotify:track:t1",
                title = "Song", artistName = "Artist", durationMs = 180_000,
                trackNumber = 1, discNumber = 1, imageUrl = null
            )
            fakeAlbumDao.albumsById["album1"] = LocalAlbum(
                id = "album1", contentEntryId = "entry1",
                spotifyAlbumUri = "spotify:album:abc", title = "Album",
                imageUrl = null, releaseDate = null, totalTracks = 3,
                contentType = ContentType.MUSIC
            )
            fakeTrackDao.indexByUri["spotify:track:t1"] = 0

            // Emit a player state so nowPlaying.positionMs is populated
            fakeRemote.emitState(buildState("spotify:track:t1", positionMs = 45_000, isPaused = false))

            controller.pause()

            val saved = fakePositionDao.lastUpsert
            assertTrue(saved != null, "Expected position to be persisted after pause")
            assertEquals("profile-1",         saved!!.profileId)
            assertEquals("spotify:album:abc", saved.contextUri)
            assertEquals("spotify:track:t1",  saved.trackUri)
            assertEquals(0,                   saved.trackIndex)
            assertEquals(45_000L,             saved.positionMs)
        }

    // ── chapter index / totalChapters ──────────────────────────────────────

    @Test
    fun `chapterIndex and totalChapters are populated for AUDIOBOOK content`() = runTest(dispatcher) {
        fakeTrackDao.tracksByUri["spotify:track:t2"] = LocalTrack(
            id = "t2", albumId = "ab1", spotifyTrackUri = "spotify:track:t2",
            title = "Kapitel 2", artistName = "Erzähler", durationMs = 1_200_000,
            trackNumber = 2, discNumber = 1, imageUrl = null
        )
        fakeAlbumDao.albumsById["ab1"] = LocalAlbum(
            id = "ab1", contentEntryId = "e2", spotifyAlbumUri = "spotify:album:ab1",
            title = "Hörbuch", imageUrl = null, releaseDate = null,
            totalTracks = 5, contentType = ContentType.AUDIOBOOK
        )
        fakeTrackDao.indexByUri["spotify:track:t2"] = 1  // 0-based

        fakeRemote.emitState(buildState("spotify:track:t2", isPaused = false))

        val state = controller.nowPlaying.value
        assertEquals(1, state.chapterIndex)
        assertEquals(5, state.totalChapters)
    }

    @Test
    fun `chapterIndex and totalChapters are null for MUSIC content`() = runTest(dispatcher) {
        fakeTrackDao.tracksByUri["spotify:track:t3"] = LocalTrack(
            id = "t3", albumId = "m1", spotifyTrackUri = "spotify:track:t3",
            title = "Song", artistName = "Band", durationMs = 210_000,
            trackNumber = 1, discNumber = 1, imageUrl = null
        )
        fakeAlbumDao.albumsById["m1"] = LocalAlbum(
            id = "m1", contentEntryId = "e3", spotifyAlbumUri = "spotify:album:m1",
            title = "Album", imageUrl = null, releaseDate = null,
            totalTracks = 10, contentType = ContentType.MUSIC
        )

        fakeRemote.emitState(buildState("spotify:track:t3", isPaused = false))

        val state = controller.nowPlaying.value
        assertNull(state.chapterIndex)
        assertNull(state.totalChapters)
    }

    // ── favorites queue ────────────────────────────────────────────────────

    @Test
    fun `playFavoritesFrom plays the track at startIndex via bare URI`() = runTest(dispatcher) {
        val favs = listOf(
            LocalFavorite("f1", "p1", "spotify:track:a", "Track A", null, null),
            LocalFavorite("f2", "p1", "spotify:track:b", "Track B", null, null),
            LocalFavorite("f3", "p1", "spotify:track:c", "Track C", null, null)
        )

        controller.playFavoritesFrom(favs, startIndex = 1)

        assertEquals(listOf("spotify:track:b"), fakeRemote.playCalls)
        // Should NOT call skipToIndex – favorites use bare URIs
        assertTrue(fakeRemote.skipToIndexCalls.isEmpty())
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun buildState(
        trackUri: String,
        positionMs: Long = 0L,
        isPaused: Boolean = false
    ) = SpotifyPlayerStateInternal(
        trackUri   = trackUri, trackTitle = "Title", artistName = "Artist",
        imageUri   = null, durationMs = 180_000, positionMs = positionMs,
        isPaused   = isPaused
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Fake SpotifyRemote – exported so SpotifyRemoteManagerTest can reuse it
// ─────────────────────────────────────────────────────────────────────────────

class FakeSpotifyRemote : SpotifyRemote {
    override val isConnected     = MutableStateFlow(true)
    override val connectionError = MutableStateFlow<SpotifyConnectionError?>(null)
    override val playerStateFlow = MutableStateFlow<SpotifyPlayerStateInternal?>(null)

    val playCalls        = mutableListOf<String>()
    val skipToIndexCalls = mutableListOf<Pair<String, Int>>()
    var pauseCalls       = 0
    var resumeCalls      = 0
    var skipNextCalls    = 0
    var skipPrevCalls    = 0
    val seekToCalls      = mutableListOf<Long>()
    val callOrder        = mutableListOf<String>()

    fun reset() {
        playCalls.clear(); skipToIndexCalls.clear(); pauseCalls = 0
        resumeCalls = 0; skipNextCalls = 0; skipPrevCalls = 0
        seekToCalls.clear(); callOrder.clear()
        playerStateFlow.value = null
    }

    fun emitState(state: SpotifyPlayerStateInternal) { playerStateFlow.value = state }

    override fun connect()    { isConnected.value = true }
    override fun disconnect() { isConnected.value = false }

    override suspend fun play(contextUri: String) {
        callOrder += "play"; playCalls += contextUri
    }
    override suspend fun skipToIndex(contextUri: String, index: Int) {
        callOrder += "skipToIndex"; skipToIndexCalls += contextUri to index
    }
    override suspend fun pause()        { callOrder += "pause";    pauseCalls++ }
    override suspend fun resume()       { callOrder += "resume";   resumeCalls++ }
    override suspend fun skipNext()     { callOrder += "skipNext"; skipNextCalls++ }
    override suspend fun skipPrevious() { callOrder += "skipPrev"; skipPrevCalls++ }
    override suspend fun seekTo(positionMs: Long) { callOrder += "seekTo"; seekToCalls += positionMs }
}

// ── Fake DAOs ─────────────────────────────────────────────────────────────────

class FakeTrackDao : TrackDao {
    val tracksByUri = mutableMapOf<String, LocalTrack>()
    val indexByUri  = mutableMapOf<String, Int>()

    override suspend fun getByUri(trackUri: String) = tracksByUri[trackUri]
    override suspend fun getIndexByUri(trackUri: String) = indexByUri[trackUri] ?: 0
    override suspend fun insertAll(tracks: List<LocalTrack>) {}
    override fun getByAlbumId(albumId: String) = flowOf(emptyList<LocalTrack>())
    override suspend fun getByAlbumIdOnce(albumId: String) = emptyList<LocalTrack>()
    override suspend fun deleteByAlbumId(albumId: String) {}
}

class FakeAlbumDao : AlbumDao {
    val albumsById = mutableMapOf<String, LocalAlbum>()

    override suspend fun insertAll(albums: List<LocalAlbum>) {}
    override fun getByContentEntryId(entryId: String) = flowOf(emptyList<LocalAlbum>())
    override suspend fun getByContentEntryIdOnce(entryId: String) = emptyList<LocalAlbum>()
    override suspend fun getById(id: String) = albumsById[id]
    override suspend fun deleteByContentEntryId(entryId: String) {}
}

class FakePositionDao : PlaybackPositionDao {
    var lastUpsert: LocalPlaybackPosition? = null
    override suspend fun upsert(position: LocalPlaybackPosition) { lastUpsert = position }
    override suspend fun getByProfileId(profileId: String) = lastUpsert
}

class FakeFavoriteDao : FavoriteDao {
    override suspend fun insert(favorite: LocalFavorite) {}
    override suspend fun deleteByUri(profileId: String, trackUri: String) {}
    override fun getAll(profileId: String): Flow<List<LocalFavorite>> = flowOf(emptyList())
    override fun existsByTrackUri(profileId: String, trackUri: String): Flow<Boolean> = flowOf(false)
    override suspend fun getUnsynced(profileId: String) = emptyList<LocalFavorite>()
    override suspend fun markSynced(id: String) {}
    override suspend fun deleteAllSynced(profileId: String) {}
}
