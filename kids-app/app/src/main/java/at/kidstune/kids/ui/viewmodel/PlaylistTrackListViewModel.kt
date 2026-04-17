package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.local.ContentDao
import at.kidstune.kids.data.local.TrackDao
import at.kidstune.kids.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistTrackListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contentDao: ContentDao,
    private val trackDao: TrackDao,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val contentEntryId: String = checkNotNull(savedStateHandle["contentEntryId"])

    private val _state = MutableStateFlow(TrackListState())
    val state: StateFlow<TrackListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val entry = contentDao.getById(contentEntryId)
            trackDao.getByContentEntryId(contentEntryId).collect { tracks ->
                _state.update { it.copy(title = entry?.title, tracks = tracks) }
            }
        }
    }

    fun onIntent(intent: TrackListIntent) {
        when (intent) {
            is TrackListIntent.TrackTapped    -> handleTrackTapped(intent.trackIndex)
            TrackListIntent.NavigationHandled -> _state.update { it.copy(navigateToNowPlaying = false) }
        }
    }

    private fun handleTrackTapped(trackIndex: Int) {
        viewModelScope.launch {
            val entry = contentDao.getById(contentEntryId) ?: return@launch
            playbackController.playFromPlaylist(entry.spotifyUri, trackIndex)
            _state.update { it.copy(navigateToNowPlaying = true) }
        }
    }
}
