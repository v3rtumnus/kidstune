package at.kidstune.kids.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import at.kidstune.kids.data.local.KidstuneDatabase
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.domain.model.ContentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentRepositoryTest {

    private lateinit var db: KidstuneDatabase
    private lateinit var repo: ContentRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KidstuneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ContentRepository(db.contentDao())
        runTest { db.contentDao().insertAll(MockContentProvider.contentEntries) }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `getByType MUSIC emits only music entries for profile`() = runTest {
        val music = repo.getByType(MockContentProvider.PROFILE_EMMA, ContentType.MUSIC).first()

        assertTrue(music.isNotEmpty())
        assertTrue(music.all { it.contentType == ContentType.MUSIC })
        assertTrue(music.all { it.profileId == MockContentProvider.PROFILE_EMMA })
    }

    @Test
    fun `getByType AUDIOBOOK emits only audiobook entries for profile`() = runTest {
        val books = repo.getByType(MockContentProvider.PROFILE_EMMA, ContentType.AUDIOBOOK).first()

        assertTrue(books.isNotEmpty())
        assertTrue(books.all { it.contentType == ContentType.AUDIOBOOK })
    }

    @Test
    fun `getByType does not leak entries from another profile`() = runTest {
        val emmaMusicEntries = repo.getByType(MockContentProvider.PROFILE_EMMA, ContentType.MUSIC).first()
        val maxMusicEntries  = repo.getByType(MockContentProvider.PROFILE_MAX,  ContentType.MUSIC).first()

        val emmaIds = emmaMusicEntries.map { it.id }.toSet()
        val maxIds  = maxMusicEntries.map { it.id }.toSet()
        assertTrue("Emma and Max should have no overlapping entries", emmaIds.intersect(maxIds).isEmpty())
    }

    @Test
    fun `getAll emits all entries for a profile regardless of type`() = runTest {
        val all = repo.getAll(MockContentProvider.PROFILE_EMMA).first()

        val types = all.map { it.contentType }.toSet()
        assertTrue("getAll should return both MUSIC and AUDIOBOOK", types.size >= 2)
    }

    @Test
    fun `getById returns correct entry`() = runTest {
        val entry = repo.getById("entry-bibi-artist")

        assertNotNull(entry)
        assertEquals("Bibi & Tina", entry!!.title)
    }

    @Test
    fun `getById returns null for unknown id`() = runTest {
        val entry = repo.getById("does-not-exist")
        assertNull(entry)
    }

    @Test
    fun `flow emits updated data after insert`() = runTest {
        val before = repo.getByType(MockContentProvider.PROFILE_EMMA, ContentType.MUSIC).first()
        val newEntry = MockContentProvider.contentEntries.first()
            .copy(id = "new-entry", title = "New Song")
        db.contentDao().insertAll(listOf(newEntry))

        val after = repo.getByType(MockContentProvider.PROFILE_EMMA, ContentType.MUSIC).first()
        assertTrue("Flow should include newly inserted entry", after.size > before.size)
    }
}
