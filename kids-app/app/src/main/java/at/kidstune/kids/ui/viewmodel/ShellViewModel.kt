package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShellState(
    val nowPlayingTitle: String?    = null,
    val nowPlayingArtist: String?   = null,
    val nowPlayingImageUrl: String? = null,
    val isPlaying: Boolean          = false,
)

sealed interface ShellIntent {
    data object TogglePlayPause : ShellIntent
}

@HiltViewModel
class ShellViewModel @Inject constructor(
    private val playbackController: PlaybackController
) : ViewModel() {

    private val _state = MutableStateFlow(ShellState())
    val state: StateFlow<ShellState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playbackController.nowPlaying.collect { np ->
                _state.update {
                    it.copy(
                        nowPlayingTitle    = np.title,
                        nowPlayingArtist   = np.artistName,
                        nowPlayingImageUrl = np.imageUrl,
                        isPlaying          = np.isPlaying,
                    )
                }
            }
        }
    }

    fun onIntent(intent: ShellIntent) {
        viewModelScope.launch {
            when (intent) {
                ShellIntent.TogglePlayPause ->
                    if (state.value.isPlaying) playbackController.pause()
                    else playbackController.resume()
            }
        }
    }
}
