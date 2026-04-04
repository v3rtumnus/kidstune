package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class NowPlayingState(
    val title: String           = "Bibi & Tina – Folge 1",
    val artistName: String      = "Bibi & Tina",
    val imageUrl: String?       = "https://picsum.photos/seed/bibitina1/400/400",
    val isPlaying: Boolean      = true,
    val isFavorite: Boolean     = false,
    val progressMs: Long        = 83_000L,  // 1:23
    val durationMs: Long        = 225_000L  // 3:45
)

sealed interface NowPlayingIntent {
    data object ToggleFavorite  : NowPlayingIntent
    data object TogglePlayPause : NowPlayingIntent
    data object SkipForward     : NowPlayingIntent
    data object SkipBack        : NowPlayingIntent
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    fun onIntent(intent: NowPlayingIntent) {
        when (intent) {
            NowPlayingIntent.ToggleFavorite  ->
                _state.update { it.copy(isFavorite = !it.isFavorite) }
            NowPlayingIntent.TogglePlayPause ->
                _state.update { it.copy(isPlaying = !it.isPlaying) }
            NowPlayingIntent.SkipForward,
            NowPlayingIntent.SkipBack        -> { /* Spotify SDK integration in prompt 4.4 */ }
        }
    }
}
