package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import at.kidstune.kids.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Activity-scoped ViewModel that triggers an immediate sync once per process lifetime.
 *
 * Because ViewModels survive configuration changes [SyncManager.syncNow] is called
 * exactly once per app launch (not per rotation). WorkManager deduplicates concurrent
 * requests via [ExistingWorkPolicy.REPLACE], so this is safe to call from multiple
 * entry points.
 *
 * If the device is not yet bound to a profile the sync is a no-op inside [SyncWorker].
 */
@HiltViewModel
class SyncTriggerViewModel @Inject constructor(
    private val syncManager: SyncManager
) : ViewModel() {

    init {
        syncManager.syncNow()
    }
}
