package at.kidstune.kids.sync

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.preferences.SyncPreferences
import at.kidstune.kids.data.repository.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom [WorkerFactory] that provides Hilt-managed dependencies to [SyncWorker]
 * without requiring the `@HiltWorker` annotation processor (which conflicts with
 * Kotlin 2.x metadata in the current toolchain).
 *
 * Registered in [at.kidstune.kids.KidstuneApp] via [androidx.work.Configuration.Provider].
 */
@Singleton
class KidstuneWorkerFactory @Inject constructor(
    private val syncRepository: SyncRepository,
    private val offlineQueue: OfflineQueue,
    private val profilePrefs: ProfilePreferences,
    private val syncPrefs: SyncPreferences
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = when (workerClassName) {
        SyncWorker::class.java.name -> SyncWorker(
            appContext    = appContext,
            params        = workerParameters,
            syncRepository = syncRepository,
            offlineQueue  = offlineQueue,
            profilePrefs  = profilePrefs,
            syncPrefs     = syncPrefs
        )
        // Return null for unknown workers so WorkManager falls back to the default factory.
        else -> null
    }
}
