package at.kidstune.kids.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.preferences.SyncPreferences
import at.kidstune.kids.data.repository.SyncRepository
import java.time.Instant

private const val TAG = "SyncWorker"

/**
 * WorkManager worker that performs a background sync on each run.
 *
 * - First run (no [SyncPreferences.lastSyncTimestamp]): calls full sync
 * - Subsequent runs: calls delta sync with the stored timestamp
 *
 * Returns [Result.retry] on any network/DB error so WorkManager reschedules
 * automatically with exponential back-off.
 *
 * Instantiated by [KidstuneWorkerFactory] which is itself injected by Hilt.
 * This avoids the `@HiltWorker` / `@AssistedInject` annotation processor which
 * is incompatible with Kotlin 2.x metadata in the current toolchain.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val offlineQueue: OfflineQueue,
    private val profilePrefs: ProfilePreferences,
    private val syncPrefs: SyncPreferences
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val profileId = profilePrefs.boundProfileId
        if (profileId == null) {
            // Device not yet bound to a profile – nothing to sync.
            return Result.success()
        }

        val lastSync = syncPrefs.lastSyncTimestamp
        val syncResult = if (lastSync != null) {
            Log.d(TAG, "Delta sync for profile $profileId since $lastSync")
            syncRepository.deltaSync(profileId, lastSync)
        } else {
            Log.d(TAG, "Full sync for profile $profileId (first run)")
            syncRepository.fullSync(profileId)
        }

        return if (syncResult.isSuccess) {
            syncPrefs.lastSyncTimestamp = Instant.now().toString()
            // OfflineQueue drain is already called inside sync methods, but calling
            // it here ensures any items queued between the sync transaction and now
            // are also flushed.
            offlineQueue.drain(profileId)
            Result.success()
        } else {
            val cause = syncResult.exceptionOrNull()
            Log.w(TAG, "Sync failed, will retry: ${cause?.message}")
            Result.retry()
        }
    }
}
