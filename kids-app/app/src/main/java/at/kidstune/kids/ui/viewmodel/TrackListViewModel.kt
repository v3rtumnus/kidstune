package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.local.AlbumDao
import at.kidstune.kids.data.local.TrackDao
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackListState(
    val album: LocalAlbum? = null,
    val tracks: List<LocalTrack> = emptyList()
)

sealed interface TrackListIntent {
    data class TrackTapped(val track: LocalTrack) : TrackListIntent
}

@HiltViewModel
class TrackListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao
) : ViewModel() {

    private val albumId: String = checkNotNull(savedStateHandle["albumId"])

    private val _state = MutableStateFlow(TrackListState())
    val state: StateFlow<TrackListState> = _state.asStateFlow()

    /** Non-null while awaiting the UI to handle a track-tap navigation event. */
    private val _selectedTrackUri = MutableStateFlow<String?>(null)
    val selectedTrackUri: StateFlow<String?> = _selectedTrackUri.asStateFlow()

    init {
        viewModelScope.launch {
            val album = albumDao.getById(albumId)
            trackDao.getByAlbumId(albumId).collect { tracks ->
                _state.update { it.copy(album = album, tracks = tracks) }
            }
        }
    }

    fun onIntent(intent: TrackListIntent) {
        when (intent) {
            is TrackListIntent.TrackTapped -> {
                // Playback implemented in prompt 4.4. Store URI for NowPlayingScreen.
                _selectedTrackUri.value = intent.track.spotifyTrackUri
            }
        }
    }

    fun onTrackNavigationHandled() {
        _selectedTrackUri.value = null
    }
}
