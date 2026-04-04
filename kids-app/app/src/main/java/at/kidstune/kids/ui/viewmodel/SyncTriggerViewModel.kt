package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity-scoped ViewModel that fires a full sync once per process lifetime.
 *
 * Because ViewModels survive configuration changes the sync runs exactly once
 * per app launch (not per rotation). WorkManager-based background sync is
 * added in prompt 5.
 */
@HiltViewModel
class SyncTriggerViewModel @Inject constructor(
    private val syncRepository: SyncRepository,
    private val prefs: ProfilePreferences
) : ViewModel() {

    init {
        val profileId = prefs.boundProfileId
        if (profileId != null) {
            viewModelScope.launch {
                // Failure is intentionally swallowed: cached Room data will be shown.
                syncRepository.fullSync(profileId)
            }
        }
    }
}
