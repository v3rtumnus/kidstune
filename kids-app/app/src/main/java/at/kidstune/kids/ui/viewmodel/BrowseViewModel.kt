package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.local.AlbumDao
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.repository.ContentRepository
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Navigation event (one-shot, consumed by BrowseScreen) ────────────────────

sealed interface BrowseNavigation {
    /** Tap on an ARTIST entry → show all albums by that artist. */
    data class ToAlbumGrid(val contentEntryId: String) : BrowseNavigation
    /** Tap on an ALBUM/PLAYLIST entry → go straight to the track list. */
    data class ToTrackList(val albumId: String) : BrowseNavigation
    /** Tap on a TRACK entry → playback (no-op until prompt 4.4). */
    data object PlayTrack : BrowseNavigation
}

// ── State ────────────────────────────────────────────────────────────────────

data class BrowseState(
    val category: BrowseCategory = BrowseCategory.MUSIC,
    val entries: List<LocalContentEntry> = emptyList(),
    val pages: List<List<LocalContentEntry>> = emptyList(),
    /** Non-null while a navigation event is pending. Cleared after consumption. */
    val navigation: BrowseNavigation? = null
) {
    val totalPages: Int get() = pages.size
}

// ── Intents ──────────────────────────────────────────────────────────────────

sealed interface BrowseIntent {
    data class TileTapped(val entry: LocalContentEntry) : BrowseIntent
    /** Called by the UI after it has acted on the current navigation event. */
    data object NavigationHandled : BrowseIntent
}

// ─────────────────────────────────────────────────────────────────────────────

private const val TILES_PER_PAGE = 4

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val albumDao: AlbumDao,
    private val prefs: ProfilePreferences
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    private var contentJob: Job? = null

    /**
     * Called from the UI whenever the visible category changes.
     * Cancels any previous collection job and starts a new one.
     */
    fun init(category: BrowseCategory) {
        contentJob?.cancel()
        contentJob = viewModelScope.launch {
            val profileId = prefs.boundProfileId ?: return@launch
            val contentType = when (category) {
                BrowseCategory.MUSIC     -> ContentType.MUSIC
                BrowseCategory.AUDIOBOOK -> ContentType.AUDIOBOOK
                BrowseCategory.FAVORITES -> null
            }
            if (contentType != null) {
                contentRepository.getByType(profileId, contentType).collect { entries ->
                    _state.update {
                        it.copy(
                            category = category,
                            entries  = entries,
                            pages    = entries.chunked(TILES_PER_PAGE)
                        )
                    }
                }
            } else {
                // FAVORITES – not implemented in this screen (has its own flow via FavoriteDao)
                _state.update {
                    it.copy(category = category, entries = emptyList(), pages = emptyList())
                }
            }
        }
    }

    fun onIntent(intent: BrowseIntent) {
        when (intent) {
            is BrowseIntent.TileTapped      -> handleTileTapped(intent.entry)
            BrowseIntent.NavigationHandled  -> _state.update { it.copy(navigation = null) }
        }
    }

    private fun handleTileTapped(entry: LocalContentEntry) {
        viewModelScope.launch {
            val nav: BrowseNavigation = when (entry.scope) {
                ContentScope.ARTIST ->
                    BrowseNavigation.ToAlbumGrid(entry.id)

                ContentScope.ALBUM, ContentScope.PLAYLIST -> {
                    val firstAlbum = albumDao.getByContentEntryIdOnce(entry.id).firstOrNull()
                    if (firstAlbum != null) BrowseNavigation.ToTrackList(firstAlbum.id)
                    else BrowseNavigation.ToAlbumGrid(entry.id) // fallback: show album grid
                }

                ContentScope.TRACK ->
                    BrowseNavigation.PlayTrack
            }
            _state.update { it.copy(navigation = nav) }
        }
    }
}
