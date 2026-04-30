package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.preferences.PendingProfilesHolder
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.remote.KidstuneApiClient
import at.kidstune.kids.domain.model.MockProfile
import at.kidstune.kids.domain.model.mockProfiles
import at.kidstune.kids.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ProfileSelectionState(
    val profiles: List<MockProfile> = mockProfiles,
    val pendingProfile: MockProfile? = null
)

sealed interface ProfileSelectionIntent {
    data class SelectProfile(val profile: MockProfile) : ProfileSelectionIntent
    data object ConfirmBinding : ProfileSelectionIntent
    data object DismissConfirmation : ProfileSelectionIntent
}

@HiltViewModel
class ProfileSelectionViewModel @Inject constructor(
    private val prefs: ProfilePreferences,
    private val pendingProfilesHolder: PendingProfilesHolder,
    private val syncManager: SyncManager,
    private val apiClient: KidstuneApiClient
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileSelectionState(
            profiles = pendingProfilesHolder.profiles ?: mockProfiles
        )
    )
    val state: StateFlow<ProfileSelectionState> = _state.asStateFlow()

    fun onIntent(intent: ProfileSelectionIntent) {
        when (intent) {
            is ProfileSelectionIntent.SelectProfile ->
                _state.update { it.copy(pendingProfile = intent.profile) }

            ProfileSelectionIntent.ConfirmBinding -> {
                val profile = _state.value.pendingProfile ?: return
                prefs.boundProfileId    = profile.id
                prefs.boundProfileName  = profile.name
                prefs.boundProfileEmoji = profile.emoji
                pendingProfilesHolder.profiles = null
                _state.update { it.copy(pendingProfile = null) }
                viewModelScope.launch {
                    // Tell the backend which profile this device is bound to, then
                    // immediately kick off a full sync. Errors are non-fatal: the
                    // sync will just fail with 404 and WorkManager will retry.
                    try { apiClient.bindProfile(profile.id) } catch (_: Exception) {}
                    syncManager.syncNow()
                }
            }

            ProfileSelectionIntent.DismissConfirmation ->
                _state.update { it.copy(pendingProfile = null) }
        }
    }
}
