package at.kidstune.kids.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Room-backed integration tests for [TrackDao.getIndexByUri] and [TrackDao.getByUri].
 *
 * These verify the SQL that PlaybackController relies on to determine chapter position.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackDaoIndexTest {

    private lateinit var db: KidstuneDatabase
    private lateinit var contentDao: ContentDao
    private lateinit var albumDao: AlbumDao
    private lateinit var trackDao: TrackDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KidstuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        contentDao = db.contentDao()
        albumDao   = db.albumDao()
        trackDao   = db.trackDao()

        // Seed: one content entry, one album with 3 tracks on disc 1
        runTest {
            contentDao.insertAll(listOf(
                LocalContentEntry(
                    id = "entry1", profileId = "p1", spotifyUri = "spotify:album:abc",
                    scope = ContentScope.ALBUM, contentType = ContentType.AUDIOBOOK,
                    title = "Hörbuch", imageUrl = null, artistName = "Erzähler",
                    lastSyncedAt = Instant.now()
                )
            ))
            albumDao.insertAll(listOf(
                LocalAlbum(
                    id = "album1", contentEntryId = "entry1",
                    spotifyAlbumUri = "spotify:album:abc", title = "Hörbuch",
                    imageUrl = null, releaseDate = null, totalTracks = 3,
                    contentType = ContentType.AUDIOBOOK
                )
            ))
            trackDao.insertAll(listOf(
                // disc 1, track 1 → index 0
                LocalTrack("t1", "album1", "spotify:track:t1", "Kapitel 1", "Erzähler", 1_200_000, 1, 1, null),
                // disc 1, track 2 → index 1
                LocalTrack("t2", "album1", "spotify:track:t2", "Kapitel 2", "Erzähler", 1_200_000, 2, 1, null),
                // disc 1, track 3 → index 2
                LocalTrack("t3", "album1", "spotify:track:t3", "Kapitel 3", "Erzähler", 1_200_000, 3, 1, null),
            ))
        }
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `getIndexByUri returns 0 for the first track`() = runTest {
        val index = trackDao.getIndexByUri("spotify:track:t1")
        assertEquals(0, index)
    }

    @Test
    fun `getIndexByUri returns 1 for the second track`() = runTest {
        val index = trackDao.getIndexByUri("spotify:track:t2")
        assertEquals(1, index)
    }

    @Test
    fun `getIndexByUri returns 2 for the third track`() = runTest {
        val index = trackDao.getIndexByUri("spotify:track:t3")
        assertEquals(2, index)
    }

    @Test
    fun `getByUri returns the correct track`() = runTest {
        val track = trackDao.getByUri("spotify:track:t2")
        assertEquals("t2", track?.id)
        assertEquals("Kapitel 2", track?.title)
    }

    @Test
    fun `getByUri returns null for unknown URI`() = runTest {
        val track = trackDao.getByUri("spotify:track:nonexistent")
        assertNull(track)
    }

    @Test
    fun `getIndexByUri is ordered by disc then track number`() = runTest {
        // Add disc 2 tracks – they should come AFTER all disc 1 tracks
        trackDao.insertAll(listOf(
            LocalTrack("d2t1", "album1", "spotify:track:d2t1", "Disc2 Kap1", "Erzähler", 600_000, 1, 2, null),
        ))

        val disc2Track1Index = trackDao.getIndexByUri("spotify:track:d2t1")
        // disc 1 has 3 tracks (indices 0,1,2), disc 2 track 1 should be index 3
        assertEquals(3, disc2Track1Index)
    }
}
