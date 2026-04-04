package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.data.local.AlbumDao
import at.kidstune.kids.data.local.FavoriteDao
import at.kidstune.kids.data.local.entities.LocalContentEntry
import at.kidstune.kids.data.local.entities.LocalFavorite
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.repository.ContentRepository
import at.kidstune.kids.domain.model.BrowseCategory
import at.kidstune.kids.domain.model.ContentScope
import at.kidstune.kids.domain.model.ContentType
import at.kidstune.kids.playback.PlaybackController
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
    /** Tap on a TRACK entry or favorite → go to the player. */
    data object ToNowPlaying : BrowseNavigation
}

// ── State ────────────────────────────────────────────────────────────────────

data class BrowseState(
    val category: BrowseCategory                    = BrowseCategory.MUSIC,
    val entries: List<LocalContentEntry>             = emptyList(),
    val pages: List<List<LocalContentEntry>>         = emptyList(),
    val favorites: List<LocalFavorite>               = emptyList(),
    val favoritesPages: List<List<LocalFavorite>>    = emptyList(),
    val navigation: BrowseNavigation?                = null
) {
    val totalPages: Int get() = if (category == BrowseCategory.FAVORITES) favoritesPages.size else pages.size
}

// ── Intents ──────────────────────────────────────────────────────────────────

sealed interface BrowseIntent {
    data class TileTapped(val entry: LocalContentEntry) : BrowseIntent
    data class FavoriteTapped(val favorite: LocalFavorite) : BrowseIntent
    data object NavigationHandled : BrowseIntent
}

// ─────────────────────────────────────────────────────────────────────────────

private const val TILES_PER_PAGE = 4

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val albumDao: AlbumDao,
    private val favoriteDao: FavoriteDao,
    private val playbackController: PlaybackController,
    private val prefs: ProfilePreferences
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    private var contentJob: Job? = null

    fun init(category: BrowseCategory) {
        contentJob?.cancel()
        contentJob = viewModelScope.launch {
            val profileId = prefs.boundProfileId ?: return@launch
            when (category) {
                BrowseCategory.MUSIC, BrowseCategory.AUDIOBOOK -> {
                    val contentType = if (category == BrowseCategory.MUSIC) ContentType.MUSIC else ContentType.AUDIOBOOK
                    contentRepository.getByType(profileId, contentType).collect { entries ->
                        _state.update {
                            it.copy(
                                category = category,
                                entries  = entries,
                                pages    = entries.chunked(TILES_PER_PAGE)
                            )
                        }
                    }
                }
                BrowseCategory.FAVORITES -> {
                    favoriteDao.getAll(profileId).collect { favs ->
                        _state.update {
                            it.copy(
                                category       = category,
                                favorites      = favs,
                                favoritesPages = favs.chunked(TILES_PER_PAGE)
                            )
                        }
                    }
                }
            }
        }
    }

    fun onIntent(intent: BrowseIntent) {
        when (intent) {
            is BrowseIntent.TileTapped      -> handleTileTapped(intent.entry)
            is BrowseIntent.FavoriteTapped  -> handleFavoriteTapped(intent.favorite)
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
                    else BrowseNavigation.ToAlbumGrid(entry.id)
                }

                ContentScope.TRACK -> {
                    // Single track: play directly as a bare URI (similar to favorites)
                    try {
                        playbackController.spotifyRemote.play(entry.spotifyUri)
                    } catch (_: Exception) { }
                    BrowseNavigation.ToNowPlaying
                }
            }
            _state.update { it.copy(navigation = nav) }
        }
    }

    private fun handleFavoriteTapped(tappedFavorite: LocalFavorite) {
        viewModelScope.launch {
            val favorites = _state.value.favorites
            val startIndex = favorites.indexOf(tappedFavorite).coerceAtLeast(0)
            playbackController.playFavoritesFrom(favorites, startIndex)
            _state.update { it.copy(navigation = BrowseNavigation.ToNowPlaying) }
        }
    }
}
