package at.kidstune.kids.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.local.KidstuneDatabase
import at.kidstune.kids.data.local.entities.LocalFavorite
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.data.remote.dto.DeltaSyncPayloadDto
import at.kidstune.kids.data.remote.dto.FavoriteResponseDto
import at.kidstune.kids.data.remote.dto.SyncAlbumDto
import at.kidstune.kids.data.remote.dto.SyncContentEntryDto
import at.kidstune.kids.data.remote.dto.SyncFavoriteDto
import at.kidstune.kids.data.remote.dto.SyncPayloadDto
import at.kidstune.kids.data.remote.dto.SyncProfileDto
import at.kidstune.kids.data.remote.dto.SyncTrackDto
import at.kidstune.kids.data.repository.FavoriteRepository
import at.kidstune.kids.data.repository.SyncRepository
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
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
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
 * Verifies that the delta sync logic (added / updated / removed / favoritesAdded /
 * favoritesRemoved) is applied correctly to the Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeltaApplicationTest {

    private lateinit var db: KidstuneDatabase
    private lateinit var syncRepository: SyncRepository
    private lateinit var favoriteRepository: FavoriteRepository

    private val testProfileId = "profile-delta-001"
    private val prefs = mockk<ProfilePreferences> {
        every { boundProfileId } returns testProfileId
        every { pinAvailable = any() } just Runs
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── Seed data: one entry with one album and one track ─────────────────────
    private val seedEntry = SyncContentEntryDto(
        id          = "entry-001",
        spotifyUri  = "spotify:artist:seed",
        scope       = "ARTIST",
        contentType = "MUSIC",
        title       = "Seed Artist",
        albums      = listOf(
            SyncAlbumDto(
                spotifyAlbumUri = "spotify:album:seed01",
                title           = "Seed Album",
                contentType     = "MUSIC",
                tracks          = listOf(
                    SyncTrackDto("spotify:track:seed01t1", "Seed Track 1")
                )
            )
        )
    )
    private val seedPayload = SyncPayloadDto(
        profile   = SyncProfileDto(testProfileId, "Tester"),
        content   = listOf(seedEntry),
        favorites = emptyList()
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KidstuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun buildRepo(fullPayload: SyncPayloadDto, deltaPayload: DeltaSyncPayloadDto): SyncRepository {
        val fullJson  = json.encodeToString(fullPayload)
        val deltaJson = json.encodeToString(deltaPayload)
        val favJson   = json.encodeToString(FavoriteResponseDto("", testProfileId, "", ""))

        val engine = MockEngine { req ->
            when {
                req.url.encodedPath.contains("/delta") ->
                    respond(ByteReadChannel(deltaJson), HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                req.url.encodedPath.contains("favorites") ->
                    respond(ByteReadChannel(favJson), HttpStatusCode.Created,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                else ->
                    respond(ByteReadChannel(fullJson), HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val api = KidstuneApiClient(client, "")
        favoriteRepository = FavoriteRepository(db.favoriteDao(), api, prefs)
        return SyncRepository(api, db, favoriteRepository, prefs)
    }

    // ── added ─────────────────────────────────────────────────────────────────

    @Test
    fun `delta adds new content entry to Room`() = runTest {
        val newEntry = SyncContentEntryDto(
            id          = "entry-002",
            spotifyUri  = "spotify:album:new",
            scope       = "ALBUM",
            contentType = "AUDIOBOOK",
            title       = "New Audiobook",
            albums      = listOf(
                SyncAlbumDto("spotify:album:new", "New Audiobook", contentType = "AUDIOBOOK",
                    tracks = listOf(SyncTrackDto("spotify:track:new01", "Chapter 1")))
            )
        )
        val delta = DeltaSyncPayloadDto(added = listOf(newEntry))
        val repo = buildRepo(seedPayload, delta)

        // Seed the DB with the initial full sync
        repo.fullSync(testProfileId)
        val beforeCount = db.contentDao().getAll(testProfileId).first().size

        val result = repo.deltaSync(testProfileId, "2025-01-01T00:00:00Z")

        assertTrue(result.isSuccess)
        val entries = db.contentDao().getAll(testProfileId).first()
        assertEquals(beforeCount + 1, entries.size)
        assertTrue(entries.any { it.id == "entry-002" && it.title == "New Audiobook" })
    }

    // ── removed ───────────────────────────────────────────────────────────────

    @Test
    fun `delta removes content entry and cascades albums and tracks`() = runTest {
        val delta = DeltaSyncPayloadDto(removed = listOf("entry-001"))
        val repo = buildRepo(seedPayload, delta)

        repo.fullSync(testProfileId)

        val result = repo.deltaSync(testProfileId, "2025-01-01T00:00:00Z")

        assertTrue(result.isSuccess)
        val entries = db.contentDao().getAll(testProfileId).first()
        assertFalse("Removed entry should be gone", entries.any { it.id == "entry-001" })

        val albums = db.albumDao().getByContentEntryIdOnce("entry-001")
        assertTrue("Albums should be cascade-deleted", albums.isEmpty())
    }

    // ── updated ───────────────────────────────────────────────────────────────

    @Test
    fun `delta updated replaces old albums and tracks with new ones`() = runTest {
        val updatedEntry = seedEntry.copy(
            albums = listOf(
                SyncAlbumDto(
                    spotifyAlbumUri = "spotify:album:new-version",
                    title           = "Updated Album",
                    contentType     = "MUSIC",
                    tracks          = listOf(
                        SyncTrackDto("spotify:track:upd01", "Updated Track")
                    )
                )
            )
        )
        val delta = DeltaSyncPayloadDto(updated = listOf(updatedEntry))
        val repo = buildRepo(seedPayload, delta)

        repo.fullSync(testProfileId)

        val result = repo.deltaSync(testProfileId, "2025-01-01T00:00:00Z")

        assertTrue(result.isSuccess)
        val albums = db.albumDao().getByContentEntryIdOnce("entry-001")
        assertEquals(1, albums.size)
        assertEquals("Updated Album", albums[0].title)
        assertEquals("spotify:album:new-version", albums[0].spotifyAlbumUri)

        val tracks = db.trackDao().getByAlbumIdOnce(albums[0].id)
        assertEquals(1, tracks.size)
        assertEquals("Updated Track", tracks[0].title)
    }

    // ── favoritesAdded ────────────────────────────────────────────────────────

    @Test
    fun `delta inserts server-side favorite additions`() = runTest {
        val delta = DeltaSyncPayloadDto(
            favoritesAdded = listOf(
                SyncFavoriteDto("spotify:track:serverFav", "Server Favourite", artistName = "Bibi")
            )
        )
        val repo = buildRepo(seedPayload, delta)

        repo.fullSync(testProfileId)
        val result = repo.deltaSync(testProfileId, "2025-01-01T00:00:00Z")

        assertTrue(result.isSuccess)
        val favs = db.favoriteDao().getAll(testProfileId).first()
        assertTrue(favs.any { it.spotifyTrackUri == "spotify:track:serverFav" && it.synced })
    }

    // ── favoritesRemoved – server wins ────────────────────────────────────────

    @Test
    fun `delta server removal wins even over locally-added unsynced favorite`() = runTest {
        val trackUri = "spotify:track:contested"
        val delta = DeltaSyncPayloadDto(favoritesRemoved = listOf(trackUri))
        val repo = buildRepo(seedPayload, delta)

        repo.fullSync(testProfileId)

        // Simulate a local (unsynced) addition of the same track
        db.favoriteDao().insert(
            LocalFavorite(
                id              = "fav__${testProfileId}__$trackUri",
                profileId       = testProfileId,
                spotifyTrackUri = trackUri,
                title           = "Local Only",
                artistName      = null,
                imageUrl        = null,
                addedAt         = Instant.now(),
                synced          = false
            )
        )
        val beforeFavs = db.favoriteDao().getAll(testProfileId).first()
        assertTrue(beforeFavs.any { it.spotifyTrackUri == trackUri })

        val result = repo.deltaSync(testProfileId, "2025-01-01T00:00:00Z")

        assertTrue(result.isSuccess)
        val afterFavs = db.favoriteDao().getAll(testProfileId).first()
        assertFalse(
            "Server removal should win over local unsynced addition",
            afterFavs.any { it.spotifyTrackUri == trackUri }
        )
    }

    // ── network failure ───────────────────────────────────────────────────────

    @Test
    fun `delta sync failure leaves existing Room data intact`() = runTest {
        // Seed with a full sync first
        val seedRepo = buildRepo(seedPayload, DeltaSyncPayloadDto())
        seedRepo.fullSync(testProfileId)

        val countBefore = db.contentDao().getAll(testProfileId).first().size

        // Wire a failing engine for the delta call
        val failEngine = MockEngine { respond(ByteReadChannel(""), HttpStatusCode.ServiceUnavailable) }
        val failClient = HttpClient(failEngine) { install(ContentNegotiation) { json(json) } }
        val failApi = KidstuneApiClient(failClient, "")
        val failFavRepo = FavoriteRepository(db.favoriteDao(), failApi, prefs)
        val failRepo = SyncRepository(failApi, db, failFavRepo, prefs)

        val result = failRepo.deltaSync(testProfileId, "2025-01-01T00:00:00Z")

        assertTrue(result.isFailure)
        val countAfter = db.contentDao().getAll(testProfileId).first().size
        assertEquals("Cached data should be untouched after failed delta sync", countBefore, countAfter)
    }
}
