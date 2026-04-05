package at.kidstune.kids.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.local.KidstuneDatabase
import at.kidstune.kids.data.local.entities.LocalFavorite
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.data.remote.dto.FavoriteResponseDto
import at.kidstune.kids.data.repository.FavoriteRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Tests for [OfflineQueue]:
 * - Adding a favorite offline puts it in the queue (synced=false)
 * - Draining the queue uploads it and marks it synced
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineQueueTest {

    private lateinit var db: KidstuneDatabase
    private lateinit var offlineQueue: OfflineQueue
    private lateinit var favoriteRepository: FavoriteRepository

    private val testProfileId = "profile-queue-001"
    private val prefs = mockk<ProfilePreferences> {
        every { boundProfileId } returns testProfileId
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KidstuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val favResponse = json.encodeToString(
            FavoriteResponseDto("srv-1", testProfileId, "spotify:track:queued", "Queued Track")
        )
        val engine = MockEngine { _ ->
            respond(ByteReadChannel(favResponse), HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val api = KidstuneApiClient(client, "")
        favoriteRepository = FavoriteRepository(db.favoriteDao(), api, prefs)
        offlineQueue = OfflineQueue(favoriteRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `adding a favorite offline creates an unsynced queue entry`() = runTest {
        db.favoriteDao().insert(unsyncedFav("spotify:track:queued"))

        val unsynced = db.favoriteDao().getUnsynced(testProfileId)
        assertEquals(1, unsynced.size)
        assertEquals("spotify:track:queued", unsynced[0].spotifyTrackUri)
    }

    @Test
    fun `draining the queue uploads the favorite and marks it synced`() = runTest {
        db.favoriteDao().insert(unsyncedFav("spotify:track:queued"))

        offlineQueue.drain(testProfileId)

        val unsynced = db.favoriteDao().getUnsynced(testProfileId)
        assertTrue("Queue should be empty after drain", unsynced.isEmpty())

        val all = db.favoriteDao().getAll(testProfileId).first()
        assertTrue("Favorite should be marked synced", all.all { it.synced })
    }

    @Test
    fun `draining an empty queue is a no-op`() = runTest {
        // No exception, queue stays empty
        offlineQueue.drain(testProfileId)
        val unsynced = db.favoriteDao().getUnsynced(testProfileId)
        assertTrue(unsynced.isEmpty())
    }

    @Test
    fun `queue holds multiple entries and drain uploads all`() = runTest {
        db.favoriteDao().insert(unsyncedFav("spotify:track:one"))
        db.favoriteDao().insert(unsyncedFav("spotify:track:two"))

        val before = db.favoriteDao().getUnsynced(testProfileId)
        assertEquals(2, before.size)

        offlineQueue.drain(testProfileId)

        val after = db.favoriteDao().getUnsynced(testProfileId)
        assertTrue("All entries should be uploaded after drain", after.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun unsyncedFav(trackUri: String) = LocalFavorite(
        id              = "fav__${testProfileId}__$trackUri",
        profileId       = testProfileId,
        spotifyTrackUri = trackUri,
        title           = "Test Track",
        artistName      = null,
        imageUrl        = null,
        addedAt         = Instant.now(),
        synced          = false
    )
}
