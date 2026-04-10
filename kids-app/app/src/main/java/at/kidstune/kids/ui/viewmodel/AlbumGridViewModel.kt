package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.local.AlbumDao
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.repository.ContentRepository
import at.kidstune.kids.domain.model.ContentType
import at.kidstune.kids.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumGridState(
    val contentEntry: LocalContentEntry? = null,
    val albums: List<LocalAlbum>         = emptyList(),
    val pages: List<List<LocalAlbum>>    = emptyList(),
    val navigation: AlbumGridNavigation? = null,
) {
    val totalPages: Int get() = pages.size
}

sealed interface AlbumGridIntent {
    data class AlbumTapped(val albumId: String) : AlbumGridIntent
    data object NavigationHandled : AlbumGridIntent
}

sealed interface AlbumGridNavigation {
    /** AUDIOBOOK album tapped – show chapter list. */
    data class ToChapterList(val albumId: String) : AlbumGridNavigation
    /** MUSIC album tapped – playback started, go to player. */
    data object ToNowPlaying : AlbumGridNavigation
}

private const val ALBUMS_PER_PAGE = 4

@HiltViewModel
class AlbumGridViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
    private val albumDao: AlbumDao,
    private val playbackController: PlaybackController
) : ViewModel() {

    private val contentEntryId: String = checkNotNull(savedStateHandle["contentEntryId"])

    private val _state = MutableStateFlow(AlbumGridState())
    val state: StateFlow<AlbumGridState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val entry = contentRepository.getById(contentEntryId)
            albumDao.getByContentEntryId(contentEntryId).collect { albums ->
                _state.update {
                    it.copy(
                        contentEntry = entry,
                        albums       = albums,
                        pages        = albums.chunked(ALBUMS_PER_PAGE)
                    )
                }
            }
        }
    }

    fun onIntent(intent: AlbumGridIntent) {
        when (intent) {
            is AlbumGridIntent.AlbumTapped    -> handleAlbumTapped(intent.albumId)
            AlbumGridIntent.NavigationHandled -> _state.update { it.copy(navigation = null) }
        }
    }

    private fun handleAlbumTapped(albumId: String) {
        viewModelScope.launch {
            val album = albumDao.getById(albumId) ?: return@launch
            if (album.contentType == ContentType.MUSIC) {
                // Music: play immediately from track 1 and go to player
                playbackController.playAlbumFromStart(album.spotifyAlbumUri)
                _state.update { it.copy(navigation = AlbumGridNavigation.ToNowPlaying) }
            } else {
                // Audiobook: show chapter list for deliberate chapter selection
                _state.update { it.copy(navigation = AlbumGridNavigation.ToChapterList(albumId)) }
            }
        }
    }
}
