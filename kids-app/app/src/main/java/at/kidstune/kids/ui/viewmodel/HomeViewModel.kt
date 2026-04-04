package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.playback.NowPlayingState
import at.kidstune.kids.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val boundProfileName: String    = "Luna",
    val boundProfileEmoji: String   = "🐻",
    val nowPlayingTitle: String?    = null,
    val nowPlayingArtist: String?   = null,
    val nowPlayingImageUrl: String? = null,
    val isPlaying: Boolean          = false
)

sealed interface HomeIntent {
    data object TogglePlayPause : HomeIntent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    prefs: ProfilePreferences,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeState(
            boundProfileName  = prefs.boundProfileName  ?: "Luna",
            boundProfileEmoji = prefs.boundProfileEmoji ?: "🐻"
        )
    )
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playbackController.nowPlaying.collect { np ->
                _state.update { home ->
                    home.copy(
                        nowPlayingTitle    = np.title,
                        nowPlayingArtist   = np.artistName,
                        nowPlayingImageUrl = np.imageUrl,
                        isPlaying          = np.isPlaying
                    )
                }
            }
        }
    }

    fun onIntent(intent: HomeIntent) {
        viewModelScope.launch {
            when (intent) {
                HomeIntent.TogglePlayPause ->
                    if (state.value.isPlaying) playbackController.pause()
                    else playbackController.resume()
            }
        }
    }
}
