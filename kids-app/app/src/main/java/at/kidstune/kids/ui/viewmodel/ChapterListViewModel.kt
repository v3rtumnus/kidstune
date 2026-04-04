package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.local.AlbumDao
import at.kidstune.kids.data.local.PlaybackPositionDao
import at.kidstune.kids.data.local.TrackDao
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalTrack
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChapterListState(
    val album: LocalAlbum?               = null,
    val chapters: List<LocalTrack>       = emptyList(),
    /** URI of the last-played chapter – null when no position saved yet. */
    val resumeTrackUri: String?          = null,
    val resumePositionMs: Long           = 0L,
    val navigateToNowPlaying: Boolean    = false
)

sealed interface ChapterListIntent {
    data class ChapterTapped(val track: LocalTrack, val trackIndex: Int) : ChapterListIntent
    data object NavigationHandled : ChapterListIntent
}

@HiltViewModel
class ChapterListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumDao: AlbumDao,
    private val trackDao: TrackDao,
    private val playbackPositionDao: PlaybackPositionDao,
    private val playbackController: PlaybackController,
    private val prefs: ProfilePreferences
) : ViewModel() {

    private val albumId: String = checkNotNull(savedStateHandle["albumId"])

    private val _state = MutableStateFlow(ChapterListState())
    val state: StateFlow<ChapterListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val album     = albumDao.getById(albumId)
            val profileId = prefs.boundProfileId
            val savedPos  = profileId?.let { playbackPositionDao.getByProfileId(it) }

            trackDao.getByAlbumId(albumId).collect { chapters ->
                _state.update {
                    it.copy(
                        album           = album,
                        chapters        = chapters,
                        resumeTrackUri  = savedPos?.trackUri,
                        resumePositionMs = savedPos?.positionMs ?: 0L
                    )
                }
            }
        }
    }

    fun onIntent(intent: ChapterListIntent) {
        when (intent) {
            is ChapterListIntent.ChapterTapped -> handleChapterTapped(intent.track, intent.trackIndex)
            ChapterListIntent.NavigationHandled -> _state.update { it.copy(navigateToNowPlaying = false) }
        }
    }

    private fun handleChapterTapped(track: LocalTrack, trackIndex: Int) {
        viewModelScope.launch {
            val albumUri = _state.value.album?.spotifyAlbumUri ?: return@launch
            playbackController.playFromChapter(albumUri, trackIndex)

            // If this is the resume chapter, seek to the saved position
            if (track.spotifyTrackUri == _state.value.resumeTrackUri) {
                val resumeMs = _state.value.resumePositionMs
                if (resumeMs > 0L) playbackController.seekTo(resumeMs)
            }

            _state.update { it.copy(navigateToNowPlaying = true) }
        }
    }
}
