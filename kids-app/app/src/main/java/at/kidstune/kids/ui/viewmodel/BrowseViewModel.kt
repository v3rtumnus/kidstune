package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import at.kidstune.kids.data.mock.MockContentProvider
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.domain.model.BrowseTile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class BrowseState(
    val category: BrowseCategory = BrowseCategory.MUSIC,
    val tiles: List<BrowseTile> = emptyList(),
    val pages: List<List<BrowseTile>> = emptyList()
) {
    val totalPages: Int get() = pages.size
}

sealed interface BrowseIntent {
    // Page changes are managed by HorizontalPager in the UI layer.
    // Reserved for future actions (e.g. selecting a tile for a track-list view).
}

private const val TILES_PER_PAGE = 4

@HiltViewModel
class BrowseViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    fun init(category: BrowseCategory) {
        val tiles = when (category) {
            BrowseCategory.MUSIC     -> MockContentProvider.mockMusicTiles
            BrowseCategory.AUDIOBOOK -> MockContentProvider.mockAudiobookTiles
            BrowseCategory.FAVORITES -> emptyList()
        }
        _state.update {
            it.copy(
                category = category,
                tiles    = tiles,
                pages    = tiles.chunked(TILES_PER_PAGE)
            )
        }
    }
}
