package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import at.kidstune.kids.data.preferences.ProfilePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class HomeState(
    val boundProfileName: String   = "Luna",
    val boundProfileEmoji: String  = "🐻",
    val nowPlayingTitle: String?   = "Bibi & Tina – Folge 1",
    val nowPlayingArtist: String?  = "Bibi & Tina",
    val nowPlayingImageUrl: String? = "https://picsum.photos/seed/bibitina1/400/400",
    val isPlaying: Boolean         = true
)

sealed interface HomeIntent {
    data object TogglePlayPause : HomeIntent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    prefs: ProfilePreferences
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeState(
            boundProfileName  = prefs.boundProfileName  ?: "Luna",
            boundProfileEmoji = prefs.boundProfileEmoji ?: "🐻"
        )
    )
    val state: StateFlow<HomeState> = _state.asStateFlow()

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.TogglePlayPause ->
                _state.update { it.copy(isPlaying = !it.isPlaying) }
        }
    }
}
