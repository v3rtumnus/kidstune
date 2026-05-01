package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.local.AlbumDao
import at.kidstune.kids.data.local.entities.LocalAlbum
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.repository.ContentRepository
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
    val isLoading: Boolean               = true,
) {
    val totalPages: Int get() = pages.size
}

sealed interface AlbumGridIntent {
    data class AlbumTapped(val albumId: String) : AlbumGridIntent
    data object NavigationHandled : AlbumGridIntent
}

sealed interface AlbumGridNavigation {
    data class ToTrackList(val albumId: String) : AlbumGridNavigation
}

private const val ALBUMS_PER_PAGE = 8

@HiltViewModel
class AlbumGridViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
    private val albumDao: AlbumDao
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
                        pages        = albums.chunked(ALBUMS_PER_PAGE),
                        isLoading    = false,
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
        _state.update { it.copy(navigation = AlbumGridNavigation.ToTrackList(albumId)) }
    }
}
