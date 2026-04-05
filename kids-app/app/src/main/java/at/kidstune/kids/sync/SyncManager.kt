package at.kidstune.kids.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Entry point for scheduling background sync via WorkManager.
 *
 * Call [registerPeriodicSync] once from [KidstuneApp.onCreate] to set up the
 * 15-minute repeating job. Call [syncNow] to enqueue an immediate one-time sync,
 * e.g. when HomeScreen becomes visible.
 *
 * Both jobs require [NetworkType.CONNECTED] so they are deferred automatically
 * when the device is offline.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val workManager get() = WorkManager.getInstance(context)

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Enqueues a periodic [SyncWorker] that runs every 15 minutes when online.
     * Uses [ExistingPeriodicWorkPolicy.KEEP] so re-calling on every app launch
     * does not reset the timer of an already-scheduled chain.
     */
    fun registerPeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Enqueues an immediate one-time [SyncWorker].
     * Uses [ExistingWorkPolicy.REPLACE] so tapping HomeScreen multiple times in
     * quick succession resets to a single pending run rather than queuing duplicates.
     */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            ONE_TIME_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    companion object {
        const val PERIODIC_SYNC_WORK_NAME = "kidstune_periodic_sync"
        const val ONE_TIME_SYNC_WORK_NAME = "kidstune_immediate_sync"
    }
}
