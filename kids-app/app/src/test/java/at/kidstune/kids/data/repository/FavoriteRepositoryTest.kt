package at.kidstune.kids.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.local.KidstuneDatabase
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.remote.KidstuneApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoriteRepositoryTest {

    private lateinit var db: KidstuneDatabase
    private lateinit var repository: FavoriteRepository

    private val profileId = "profile-test"
    private val prefs = mockk<ProfilePreferences> {
        every { boundProfileId } returns profileId
    }

    private val track = LocalTrack(
        id              = "track-1",
        albumId         = "album-1",
        spotifyTrackUri = "spotify:track:abc123",
        title           = "Test Track",
        artistName      = "Test Artist",
        durationMs      = 180_000L,
        trackNumber     = 1,
        discNumber      = 1,
        imageUrl        = null
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KidstuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val mockEngine = MockEngine { respond(ByteReadChannel(""), HttpStatusCode.NoContent) }
        val mockClient = HttpClient(mockEngine) { install(ContentNegotiation) { json(json) } }
        val apiClient = KidstuneApiClient(mockClient, baseUrl = "")

        repository = FavoriteRepository(db.favoriteDao(), apiClient, prefs)
    }

    @After
    fun tearDown() { db.close() }

    // ── toggleFavorite ────────────────────────────────────────────────────────

    @Test
    fun `toggleFavorite adds track when not yet favorited`() = runTest {
        repository.toggleFavorite(track)

        val isFav = repository.isFavorite(track.spotifyTrackUri).first()
        assertTrue(isFav)
    }

    @Test
    fun `toggleFavorite removes track when already favorited`() = runTest {
        repository.toggleFavorite(track) // add
        repository.toggleFavorite(track) // remove

        val isFav = repository.isFavorite(track.spotifyTrackUri).first()
        assertFalse(isFav)
    }

    @Test
    fun `toggleFavorite inserts with synced=false`() = runTest {
        repository.toggleFavorite(track)

        val unsynced = db.favoriteDao().getUnsynced(profileId)
        assertEquals(1, unsynced.size)
        assertFalse(unsynced.first().synced)
        assertEquals(track.spotifyTrackUri, unsynced.first().spotifyTrackUri)
    }

    // ── isFavorite ────────────────────────────────────────────────────────────

    @Test
    fun `isFavorite emits false before toggle`() = runTest {
        assertFalse(repository.isFavorite(track.spotifyTrackUri).first())
    }

    @Test
    fun `isFavorite emits true after toggle add`() = runTest {
        repository.toggleFavorite(track)

        assertTrue(repository.isFavorite(track.spotifyTrackUri).first())
    }

    @Test
    fun `isFavorite emits false after toggle remove`() = runTest {
        repository.toggleFavorite(track) // add
        repository.toggleFavorite(track) // remove

        assertFalse(repository.isFavorite(track.spotifyTrackUri).first())
    }

    // ── getUnsynced ───────────────────────────────────────────────────────────

    @Test
    fun `getUnsynced returns only favorites with synced=false`() = runTest {
        repository.toggleFavorite(track) // inserts with synced=false

        // Manually mark it synced
        val id = "fav__${profileId}__${track.spotifyTrackUri}"
        db.favoriteDao().markSynced(id)

        val unsynced = db.favoriteDao().getUnsynced(profileId)
        assertTrue(unsynced.isEmpty())
    }

    @Test
    fun `getUnsynced returns the newly added favorite`() = runTest {
        repository.toggleFavorite(track)

        val unsynced = db.favoriteDao().getUnsynced(profileId)
        assertEquals(1, unsynced.size)
        assertEquals(track.spotifyTrackUri, unsynced.first().spotifyTrackUri)
    }
}
