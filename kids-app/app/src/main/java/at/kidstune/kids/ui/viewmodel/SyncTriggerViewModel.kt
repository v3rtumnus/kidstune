package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity-scoped ViewModel that triggers an immediate sync once per process lifetime.
 *
 * On each launch it first ensures the device–profile binding is registered on the backend
 * (idempotent), then enqueues a WorkManager sync. This covers both fresh setup and the
 * case where a prior launch bound the profile locally but the bind call never reached
 * the backend (e.g. first launch was offline).
 *
 * If the device has no profile yet the sync is a no-op inside [SyncWorker].
 */
@HiltViewModel
class SyncTriggerViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val apiClient: KidstuneApiClient,
    private val profilePrefs: ProfilePreferences
) : ViewModel() {

    init {
        viewModelScope.launch {
            if (profilePrefs.isBound) {
                try { apiClient.bindProfile(profilePrefs.boundProfileId!!) } catch (_: Exception) {}
            }
            syncManager.syncNow()
        }
    }
}
