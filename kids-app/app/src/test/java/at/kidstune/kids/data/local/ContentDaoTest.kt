package at.kidstune.kids.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.domain.model.ContentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentDaoTest {

    private lateinit var db: KidstuneDatabase
    private lateinit var dao: ContentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KidstuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.contentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert and retrieve content entries by profileId`() = runTest {
        dao.insertAll(MockContentProvider.contentEntries)

        val emmaEntries = dao.getAll(MockContentProvider.PROFILE_EMMA).first()

        assert(emmaEntries.isNotEmpty())
        assert(emmaEntries.all { it.profileId == MockContentProvider.PROFILE_EMMA })
    }

    @Test
    fun `filter by content type`() = runTest {
        dao.insertAll(MockContentProvider.contentEntries)

        val musicEntries = dao.getByType(MockContentProvider.PROFILE_EMMA, ContentType.MUSIC).first()
        val audiobookEntries = dao.getByType(MockContentProvider.PROFILE_EMMA, ContentType.AUDIOBOOK).first()

        assert(musicEntries.isNotEmpty())
        assert(musicEntries.all { it.contentType == ContentType.MUSIC })
        assert(audiobookEntries.isNotEmpty())
        assert(audiobookEntries.all { it.contentType == ContentType.AUDIOBOOK })
    }

    @Test
    fun `return correct count by type`() = runTest {
        dao.insertAll(MockContentProvider.contentEntries)

        val musicCount = dao.countByType(MockContentProvider.PROFILE_EMMA, ContentType.MUSIC)

        val expectedCount = MockContentProvider.contentEntries
            .count { it.profileId == MockContentProvider.PROFILE_EMMA && it.contentType == ContentType.MUSIC }
        assertEquals(expectedCount, musicCount)
    }

    @Test
    fun `find entry by id`() = runTest {
        dao.insertAll(MockContentProvider.contentEntries)

        val entry = dao.getById("entry-bibi-artist")

        assertNotNull(entry)
        assertEquals("Bibi & Tina", entry!!.title)
    }

    @Test
    fun `return null for unknown id`() = runTest {
        dao.insertAll(MockContentProvider.contentEntries)

        val entry = dao.getById("does-not-exist")

        assertNull(entry)
    }

    @Test
    fun `delete all entries for a profile`() = runTest {
        dao.insertAll(MockContentProvider.contentEntries)

        dao.deleteAll(MockContentProvider.PROFILE_EMMA)
        val emmaEntries = dao.getAll(MockContentProvider.PROFILE_EMMA).first()
        val maxEntries = dao.getAll(MockContentProvider.PROFILE_MAX).first()

        assert(emmaEntries.isEmpty())
        assert(maxEntries.isNotEmpty())
    }

    @Test
    fun `replace existing entry on conflict`() = runTest {
        dao.insertAll(MockContentProvider.contentEntries)

        val updated = MockContentProvider.contentEntries.first().copy(title = "Bibi & Tina – Updated")
        dao.insertAll(listOf(updated))

        val entry = dao.getById(updated.id)
        assertEquals("Bibi & Tina – Updated", entry?.title)
    }
}
