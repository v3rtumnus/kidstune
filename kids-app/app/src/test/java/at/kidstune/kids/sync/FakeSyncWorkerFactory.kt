package at.kidstune.kids.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.preferences.SyncPreferences
import at.kidstune.kids.data.repository.SyncRepository

/**
 * Test-only [WorkerFactory] that injects mocked dependencies into [SyncWorker]
 * without requiring the full Hilt graph.
 *
 * Used by [SyncWorkerTest] via [androidx.work.testing.TestListenableWorkerBuilder.setWorkerFactory].
 */
class FakeSyncWorkerFactory(
    private val syncRepository: SyncRepository,
    private val offlineQueue: OfflineQueue,
    private val profilePrefs: ProfilePreferences,
    private val syncPrefs: SyncPreferences
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker = SyncWorker(
        appContext    = appContext,
        params        = workerParameters,
        syncRepository = syncRepository,
        offlineQueue  = offlineQueue,
        profilePrefs  = profilePrefs,
        syncPrefs     = syncPrefs
    )
}
