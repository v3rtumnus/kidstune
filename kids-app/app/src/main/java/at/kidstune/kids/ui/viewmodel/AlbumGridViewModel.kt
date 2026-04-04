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
    val albums: List<LocalAlbum> = emptyList(),
    val pages: List<List<LocalAlbum>> = emptyList()
) {
    val totalPages: Int get() = pages.size
}

sealed interface AlbumGridIntent {
    data class AlbumTapped(val albumId: String) : AlbumGridIntent
    data object NavigationHandled : AlbumGridIntent
}

data class AlbumGridNavigation(val albumId: String)

private const val ALBUMS_PER_PAGE = 4

@HiltViewModel
class AlbumGridViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
    private val albumDao: AlbumDao
) : ViewModel() {

    private val contentEntryId: String = checkNotNull(savedStateHandle["contentEntryId"])

    private val _state = MutableStateFlow(AlbumGridState())
    val state: StateFlow<AlbumGridState> = _state.asStateFlow()

    private val _navigation = MutableStateFlow<AlbumGridNavigation?>(null)
    val navigation: StateFlow<AlbumGridNavigation?> = _navigation.asStateFlow()

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
            is AlbumGridIntent.AlbumTapped    -> _navigation.value = AlbumGridNavigation(intent.albumId)
            AlbumGridIntent.NavigationHandled -> _navigation.value = null
        }
    }
}
