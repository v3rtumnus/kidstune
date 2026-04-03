package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.domain.model.MockProfile
import at.kidstune.kids.domain.model.mockProfiles
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val prefs: ProfilePreferences
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileSelectionState())
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
                _state.update { it.copy(pendingProfile = null) }
            }

            ProfileSelectionIntent.DismissConfirmation ->
                _state.update { it.copy(pendingProfile = null) }
        }
    }
}
