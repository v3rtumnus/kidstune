package at.kidstune.kids.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.local.KidstuneDatabase
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.data.remote.dto.FavoriteResponseDto
import at.kidstune.kids.sync.OfflineQueue
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
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
import java.time.Instant

/**
 * Integration test covering the offline favorite lifecycle:
 *
 * 1. Device goes offline (network returns error).
 * 2. User adds a favorite → stored in Room with synced=false.
 * 3. Device reconnects (network returns success).
 * 4. OfflineQueue.drain() uploads the favorite → marked synced=true.
 *
 * Uses an in-memory Room database and Ktor MockEngine to simulate
 * network failure and recovery without requiring a real server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoriteOfflineIntTest {

    private lateinit var db: KidstuneDatabase
    private lateinit var favoriteRepository: FavoriteRepository
    private lateinit var offlineQueue: OfflineQueue

    private val profileId = "profile-offline-test"
    private val prefs = mockk<ProfilePreferences> {
        every { boundProfileId } returns profileId
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Controls whether the mock network is reachable or not.
    private var networkAvailable = false

    private val track = LocalTrack(
        id              = "track-offline-1",
        albumId         = "album-offline-1",
        spotifyTrackUri = "spotify:track:offline001",
        title           = "Offline Track",
        artistName      = "Test Artist",
        durationMs      = 200_000L,
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

        val engine = MockEngine { _ ->
            if (networkAvailable) {
                val body = json.encodeToString(
                    FavoriteResponseDto(
                        id              = "srv-offline-1",
                        profileId       = profileId,
                        spotifyTrackUri = track.spotifyTrackUri,
                        trackTitle      = track.title
                    )
                )
                respond(
                    content = ByteReadChannel(body),
                    status  = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(ByteReadChannel(""), HttpStatusCode.ServiceUnavailable)
            }
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val api    = KidstuneApiClient(client, "")

        favoriteRepository = FavoriteRepository(db.favoriteDao(), api, prefs)
        offlineQueue       = OfflineQueue(favoriteRepository)
    }

    @After
    fun tearDown() { db.close() }

    // ── Offline add → Room stored with synced=false ───────────────────────────

    @Test
    fun `adding favorite while offline stores entry with synced=false`() = runTest {
        networkAvailable = false

        favoriteRepository.toggleFavorite(track)

        val unsynced = db.favoriteDao().getUnsynced(profileId)
        assertEquals(1, unsynced.size)
        assertFalse("Should be unsynced after offline add", unsynced.first().synced)
        assertEquals(track.spotifyTrackUri, unsynced.first().spotifyTrackUri)
    }

    @Test
    fun `favorite is visible in isFavorite immediately after offline add`() = runTest {
        networkAvailable = false

        favoriteRepository.toggleFavorite(track)

        assertTrue(favoriteRepository.isFavorite(track.spotifyTrackUri).first())
    }

    // ── Reconnect → drain uploads and marks synced ────────────────────────────

    @Test
    fun `draining queue after reconnect uploads favorite and marks synced=true`() = runTest {
        // Step 1: offline – add favorite
        networkAvailable = false
        favoriteRepository.toggleFavorite(track)
        val unsynced = db.favoriteDao().getUnsynced(profileId)
        assertEquals("Setup: should have 1 unsynced entry", 1, unsynced.size)

        // Step 2: reconnect – drain queue
        networkAvailable = true
        offlineQueue.drain(profileId)

        // Step 3: verify synced
        val remaining = db.favoriteDao().getUnsynced(profileId)
        assertTrue("Queue should be empty after successful drain", remaining.isEmpty())

        val all = db.favoriteDao().getAll(profileId).first()
        assertTrue("Favorite should be marked synced=true", all.all { it.synced })
    }

    @Test
    fun `drain with network still down leaves entry unsynced for next attempt`() = runTest {
        networkAvailable = false
        favoriteRepository.toggleFavorite(track)

        // Drain while still offline – upload will fail, entry should remain unsynced.
        offlineQueue.drain(profileId)

        val unsynced = db.favoriteDao().getUnsynced(profileId)
        assertEquals("Entry should remain unsynced after failed drain", 1, unsynced.size)
    }

    @Test
    fun `multiple offline adds are all uploaded on reconnect`() = runTest {
        networkAvailable = false

        val track2 = track.copy(
            id              = "track-offline-2",
            spotifyTrackUri = "spotify:track:offline002",
            title           = "Offline Track 2"
        )

        favoriteRepository.toggleFavorite(track)
        favoriteRepository.toggleFavorite(track2)

        assertEquals(2, db.favoriteDao().getUnsynced(profileId).size)

        // Reconnect and drain
        networkAvailable = true
        offlineQueue.drain(profileId)

        assertTrue("All entries should be synced after drain", db.favoriteDao().getUnsynced(profileId).isEmpty())
    }

    // ── SyncWorker no-network → Result.retry ─────────────────────────────────
    // The SyncWorker passes any failure from SyncRepository.fullSync/deltaSync to
    // WorkManager as Result.retry(). This is covered in SyncWorkerTest.
    // The test below verifies the lower-level repository fails gracefully when the
    // network is unavailable during a sync operation (rather than throwing an
    // unhandled exception).

    @Test
    fun `favoriteRepository uploadUnsynced tolerates network failure without throwing`() = runTest {
        networkAvailable = false
        favoriteRepository.toggleFavorite(track)

        // Should not throw even though network is down.
        favoriteRepository.uploadUnsynced(profileId)

        // Entry remains unsynced – safe for retry.
        assertEquals(1, db.favoriteDao().getUnsynced(profileId).size)
    }
}
