package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.local.AlbumDao
import at.kidstune.kids.data.local.TrackDao
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackListState(
    val album: LocalAlbum?       = null,
    val title: String?           = null,
    val tracks: List<LocalTrack> = emptyList(),
    val navigateToNowPlaying: Boolean = false
) {
    val screenTitle: String get() = album?.title ?: title ?: "Titel"
}

sealed interface TrackListIntent {
    data class TrackTapped(val track: LocalTrack, val trackIndex: Int) : TrackListIntent
    data object NavigationHandled : TrackListIntent
}

@HiltViewModel
class TrackListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val albumId: String = checkNotNull(savedStateHandle["albumId"])

    private val _state = MutableStateFlow(TrackListState())
    val state: StateFlow<TrackListState> = _state.asStateFlow()

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
            is TrackListIntent.TrackTapped -> handleTrackTapped(intent.track, intent.trackIndex)
            TrackListIntent.NavigationHandled -> _state.update { it.copy(navigateToNowPlaying = false) }
        }
    }

    private fun handleTrackTapped(track: LocalTrack, trackIndex: Int) {
        viewModelScope.launch {
            val album = _state.value.album ?: return@launch
            // Always play from the album URI so Spotify auto-advances within this album.
            // Playlist tracks are grouped by source album in Room; playing via the playlist URI
            // with a within-album index would play the wrong track.
            playbackController.playFromChapter(album.spotifyAlbumUri, trackIndex)
            _state.update { it.copy(navigateToNowPlaying = true) }
        }
    }
}
