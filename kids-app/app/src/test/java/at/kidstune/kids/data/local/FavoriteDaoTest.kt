package at.kidstune.kids.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.mock.MockContentProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
class FavoriteDaoTest {

    private lateinit var db: KidstuneDatabase
    private lateinit var dao: FavoriteDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KidstuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.favoriteDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert and retrieve favorites`() = runTest {
        MockContentProvider.favorites.forEach { dao.insert(it) }

        val favorites = dao.getAll(MockContentProvider.PROFILE_EMMA).first()

        assertEquals(2, favorites.size)
    }

    @Test
    fun `track is favorited`() = runTest {
        MockContentProvider.favorites.forEach { dao.insert(it) }

        val isFavorited = dao.existsByTrackUri(
            MockContentProvider.PROFILE_EMMA,
            "spotify:track:bibi01t1"
        ).first()

        assertTrue(isFavorited)
    }

    @Test
    fun `non-favorited track returns false`() = runTest {
        val isFavorited = dao.existsByTrackUri(
            MockContentProvider.PROFILE_EMMA,
            "spotify:track:unknown"
        ).first()

        assertFalse(isFavorited)
    }

    @Test
    fun `delete favorite by uri`() = runTest {
        MockContentProvider.favorites.forEach { dao.insert(it) }

        dao.deleteByUri(MockContentProvider.PROFILE_EMMA, "spotify:track:bibi01t1")

        val isFavorited = dao.existsByTrackUri(
            MockContentProvider.PROFILE_EMMA,
            "spotify:track:bibi01t1"
        ).first()
        assertFalse(isFavorited)
    }

    @Test
    fun `return unsynced favorites`() = runTest {
        MockContentProvider.favorites.forEach { dao.insert(it) }

        val unsynced = dao.getUnsynced(MockContentProvider.PROFILE_EMMA)

        assertEquals(1, unsynced.size)
        assertEquals("spotify:track:tkkg200t1", unsynced.first().spotifyTrackUri)
    }

    @Test
    fun `mark favorite as synced`() = runTest {
        MockContentProvider.favorites.forEach { dao.insert(it) }

        dao.markSynced("fav-2")

        val unsynced = dao.getUnsynced(MockContentProvider.PROFILE_EMMA)
        assertTrue(unsynced.isEmpty())
    }

    @Test
    fun `favorites ordered by addedAt descending`() = runTest {
        MockContentProvider.favorites.forEach { dao.insert(it) }

        val favorites = dao.getAll(MockContentProvider.PROFILE_EMMA).first()

        // fav-2 was added after fav-1, so it should come first
        assertEquals("fav-2", favorites.first().id)
    }
}
