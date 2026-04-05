package at.kidstune.kids.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.preferences.SyncPreferences
import at.kidstune.kids.data.repository.SyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [SyncWorker] using [TestListenableWorkerBuilder].
 *
 * Dependencies are mocked with MockK so no real network or Room is needed here.
 * Integration of the full chain is covered in [DeltaApplicationTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerTest {

    private lateinit var context: Context

    private val syncRepository = mockk<SyncRepository>()
    private val offlineQueue   = mockk<OfflineQueue>()
    private val profilePrefs   = mockk<ProfilePreferences>()
    private val syncPrefs      = mockk<SyncPreferences>(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        coEvery { offlineQueue.drain(any()) } returns Unit
    }

    private fun buildWorker() =
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(
                FakeSyncWorkerFactory(syncRepository, offlineQueue, profilePrefs, syncPrefs)
            )
            .build()

    // ── No profile bound → success without syncing ────────────────────────────

    @Test
    fun `returns success immediately when no profile is bound`() = runTest {
        every { profilePrefs.boundProfileId } returns null

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { syncRepository.fullSync(any()) }
        coVerify(exactly = 0) { syncRepository.deltaSync(any(), any()) }
    }

    // ── First run (no lastSyncTimestamp) → full sync ───────────────────────────

    @Test
    fun `calls full sync when no lastSyncTimestamp is stored`() = runTest {
        every { profilePrefs.boundProfileId } returns "profile-001"
        every { syncPrefs.lastSyncTimestamp } returns null
        coEvery { syncRepository.fullSync("profile-001") } returns kotlin.Result.success(Unit)

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { syncRepository.fullSync("profile-001") }
        coVerify(exactly = 0) { syncRepository.deltaSync(any(), any()) }
    }

    // ── Subsequent run (timestamp present) → delta sync ──────────────────────

    @Test
    fun `calls delta sync when lastSyncTimestamp is stored`() = runTest {
        every { profilePrefs.boundProfileId } returns "profile-001"
        every { syncPrefs.lastSyncTimestamp } returns "2025-01-01T00:00:00Z"
        coEvery { syncRepository.deltaSync("profile-001", "2025-01-01T00:00:00Z") } returns
                kotlin.Result.success(Unit)

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { syncRepository.deltaSync("profile-001", "2025-01-01T00:00:00Z") }
        coVerify(exactly = 0) { syncRepository.fullSync(any()) }
    }

    // ── Successful sync → timestamp is updated ────────────────────────────────

    @Test
    fun `updates lastSyncTimestamp after successful sync`() = runTest {
        every { profilePrefs.boundProfileId } returns "profile-001"
        every { syncPrefs.lastSyncTimestamp } returns null
        coEvery { syncRepository.fullSync("profile-001") } returns kotlin.Result.success(Unit)

        val captured = slot<String>()
        every { syncPrefs.lastSyncTimestamp = capture(captured) } answers { }

        buildWorker().doWork()

        assertNotNull("Timestamp should be written after success", captured.captured)
    }

    // ── Network failure → retry ───────────────────────────────────────────────

    @Test
    fun `returns retry when full sync fails`() = runTest {
        every { profilePrefs.boundProfileId } returns "profile-001"
        every { syncPrefs.lastSyncTimestamp } returns null
        coEvery { syncRepository.fullSync("profile-001") } returns
                kotlin.Result.failure(Exception("Network error"))

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `returns retry when delta sync fails`() = runTest {
        every { profilePrefs.boundProfileId } returns "profile-001"
        every { syncPrefs.lastSyncTimestamp } returns "2025-01-01T00:00:00Z"
        coEvery { syncRepository.deltaSync(any(), any()) } returns
                kotlin.Result.failure(Exception("Timeout"))

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    // ── Offline queue is drained after success ────────────────────────────────

    @Test
    fun `drains offline queue after successful sync`() = runTest {
        every { profilePrefs.boundProfileId } returns "profile-001"
        every { syncPrefs.lastSyncTimestamp } returns null
        coEvery { syncRepository.fullSync("profile-001") } returns kotlin.Result.success(Unit)

        buildWorker().doWork()

        coVerify(exactly = 1) { offlineQueue.drain("profile-001") }
    }
}
