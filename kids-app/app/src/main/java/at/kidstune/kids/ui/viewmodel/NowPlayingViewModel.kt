package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.local.TrackDao
import at.kidstune.kids.domain.usecase.ToggleFavoriteUseCase
import at.kidstune.kids.playback.NowPlayingState
import at.kidstune.kids.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NowPlayingIntent {
    data object ToggleFavorite  : NowPlayingIntent
    data object TogglePlayPause : NowPlayingIntent
    data object SkipForward     : NowPlayingIntent
    data object SkipBack        : NowPlayingIntent
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val trackDao: TrackDao
) : ViewModel() {

    val state: StateFlow<NowPlayingState> = playbackController.nowPlaying
        .stateIn(viewModelScope, SharingStarted.Eagerly, NowPlayingState())

    fun onIntent(intent: NowPlayingIntent) {
        viewModelScope.launch {
            when (intent) {
                NowPlayingIntent.TogglePlayPause ->
                    if (state.value.isPlaying) playbackController.pause()
                    else playbackController.resume()

                NowPlayingIntent.SkipForward -> playbackController.skipNext()
                NowPlayingIntent.SkipBack    -> playbackController.skipPrevious()

                NowPlayingIntent.ToggleFavorite -> {
                    val trackUri = state.value.trackUri ?: return@launch
                    val track = trackDao.getByUri(trackUri) ?: return@launch
                    toggleFavoriteUseCase(track)
                }
            }
        }
    }
}
