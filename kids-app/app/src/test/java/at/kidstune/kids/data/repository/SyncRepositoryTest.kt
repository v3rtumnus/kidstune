package at.kidstune.kids.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.local.KidstuneDatabase
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.data.remote.dto.SyncAlbumDto
import at.kidstune.kids.data.remote.dto.SyncContentEntryDto
import at.kidstune.kids.data.remote.dto.SyncFavoriteDto
import at.kidstune.kids.data.remote.dto.SyncPayloadDto
import at.kidstune.kids.data.remote.dto.SyncProfileDto
import at.kidstune.kids.data.remote.dto.SyncTrackDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncRepositoryTest {

    private lateinit var db: KidstuneDatabase
    private lateinit var syncRepository: SyncRepository

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val testProfileId = "profile-test-001"

    private val samplePayload = SyncPayloadDto(
        profile = SyncProfileDto(id = testProfileId, name = "Emma"),
        favorites = listOf(
            SyncFavoriteDto(
                spotifyTrackUri = "spotify:track:fav1",
                trackTitle      = "Lieblingslied",
                artistName      = "Bibi & Tina",
                addedAt         = "2025-01-01T10:00:00Z"
            )
        ),
        content = listOf(
            SyncContentEntryDto(
                id          = "entry-001",
                spotifyUri  = "spotify:artist:abc",
                scope       = "ARTIST",
                contentType = "MUSIC",
                title       = "Bibi & Tina",
                imageUrl    = "https://example.com/bibi.jpg",
                artistName  = "Bibi & Tina",
                albums      = listOf(
                    SyncAlbumDto(
                        spotifyAlbumUri = "spotify:album:bibi01",
                        title           = "Bibi & Tina Folge 1",
                        contentType     = "MUSIC",
                        tracks          = listOf(
                            SyncTrackDto(
                                spotifyTrackUri = "spotify:track:bibi01t1",
                                title           = "Folge 1",
                                artistName      = "Bibi & Tina",
                                durationMs      = 3_600_000L,
                                trackNumber     = 1,
                                discNumber      = 1
                            )
                        )
                    )
                )
            )
        ),
        syncTimestamp = "2025-01-01T00:00:00Z"
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KidstuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val serializedPayload = json.encodeToString(SyncPayloadDto.serializer(), samplePayload)
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(serializedPayload),
                status  = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val apiClient = KidstuneApiClient(mockClient, baseUrl = "")
        syncRepository = SyncRepository(apiClient, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `full sync inserts content entries into Room`() = runTest {
        val result = syncRepository.fullSync(testProfileId)

        assertTrue("Sync should succeed", result.isSuccess)
        val entries = db.contentDao().getAll(testProfileId).first()
        assertEquals(1, entries.size)
        assertEquals("Bibi & Tina", entries[0].title)
        assertEquals("MUSIC", entries[0].contentType.name)
        assertEquals("ARTIST", entries[0].scope.name)
    }

    @Test
    fun `full sync inserts albums nested under content entries`() = runTest {
        syncRepository.fullSync(testProfileId)

        val albums = db.albumDao().getByContentEntryIdOnce("entry-001")
        assertEquals(1, albums.size)
        assertEquals("Bibi & Tina Folge 1", albums[0].title)
        assertEquals("spotify:album:bibi01", albums[0].spotifyAlbumUri)
    }

    @Test
    fun `full sync inserts tracks nested under albums`() = runTest {
        syncRepository.fullSync(testProfileId)

        val albums = db.albumDao().getByContentEntryIdOnce("entry-001")
        val tracks = db.trackDao().getByAlbumIdOnce(albums[0].id)
        assertEquals(1, tracks.size)
        assertEquals("Folge 1", tracks[0].title)
        assertEquals("spotify:track:bibi01t1", tracks[0].spotifyTrackUri)
        assertEquals(3_600_000L, tracks[0].durationMs)
    }

    @Test
    fun `full sync inserts favorites`() = runTest {
        syncRepository.fullSync(testProfileId)

        val favorites = db.favoriteDao().getAll(testProfileId).first()
        assertEquals(1, favorites.size)
        assertEquals("spotify:track:fav1", favorites[0].spotifyTrackUri)
        assertEquals("Lieblingslied", favorites[0].title)
        assertTrue("Synced favorites should be marked synced", favorites[0].synced)
    }

    @Test
    fun `second full sync replaces previous content`() = runTest {
        syncRepository.fullSync(testProfileId)

        // Second sync with a different payload (zero items)
        val emptyPayload = samplePayload.copy(content = emptyList(), favorites = emptyList())
        val serialized   = json.encodeToString(SyncPayloadDto.serializer(), emptyPayload)
        val emptyEngine  = MockEngine { respond(ByteReadChannel(serialized), HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json")) }
        val emptyClient  = HttpClient(emptyEngine) { install(ContentNegotiation) { json(json) } }
        val repo2        = SyncRepository(KidstuneApiClient(emptyClient, ""), db)

        repo2.fullSync(testProfileId)

        val entries = db.contentDao().getAll(testProfileId).first()
        assertTrue("Content should be empty after second sync with no items", entries.isEmpty())
    }

    @Test
    fun `sync failure leaves existing cached data intact`() = runTest {
        // Pre-populate Room
        syncRepository.fullSync(testProfileId)
        val beforeCount = db.contentDao().getAll(testProfileId).first().size

        // Wire up a failing client
        val failEngine = MockEngine { respond(ByteReadChannel(""), HttpStatusCode.ServiceUnavailable) }
        val failClient = HttpClient(failEngine) { install(ContentNegotiation) { json(json) } }
        val failRepo   = SyncRepository(KidstuneApiClient(failClient, ""), db)

        val result = failRepo.fullSync(testProfileId)

        assertTrue("Sync should fail", result.isFailure)
        val afterCount = db.contentDao().getAll(testProfileId).first().size
        assertEquals("Cached data should be unchanged after failed sync", beforeCount, afterCount)
    }

    @Test
    fun `unsynced favorites are preserved through a full sync`() = runTest {
        // Seed an unsynced favorite
        syncRepository.fullSync(testProfileId)
        db.favoriteDao().insert(
            at.kidstune.kids.data.local.entities.LocalFavorite(
                id              = "local-fav-1",
                profileId       = testProfileId,
                spotifyTrackUri = "spotify:track:localonly",
                title           = "Lokaler Favorit",
                artistName      = "Test",
                imageUrl        = null,
                addedAt         = java.time.Instant.now(),
                synced          = false
            )
        )

        // Run a second sync (server doesn't know about the local favorite)
        syncRepository.fullSync(testProfileId)

        val favorites = db.favoriteDao().getAll(testProfileId).first()
        assertTrue(
            "Unsynced local favorite should survive a full sync",
            favorites.any { it.spotifyTrackUri == "spotify:track:localonly" }
        )
    }
}
