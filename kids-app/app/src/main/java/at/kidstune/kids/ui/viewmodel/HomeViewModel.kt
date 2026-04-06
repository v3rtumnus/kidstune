package at.kidstune.kids.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.kidstune.kids.connectivity.ConnectivityObserver
import at.kidstune.kids.data.local.ContentDao
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.data.preferences.SyncPreferences
import at.kidstune.kids.playback.NowPlayingState
import at.kidstune.kids.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class HomeState(
    val boundProfileName: String    = "Luna",
    val boundProfileEmoji: String   = "🐻",
    val nowPlayingTitle: String?    = null,
    val nowPlayingArtist: String?   = null,
    val nowPlayingImageUrl: String? = null,
    val isPlaying: Boolean          = false,
    /** True while the device has no validated internet connection. */
    val isOffline: Boolean          = false,
    /**
     * True when the device is online but [SyncPreferences.lastSyncTimestamp] is older than
     * 24 hours. Shows a subtle yellow dot on HomeScreen – informational only.
     */
    val isStaleContent: Boolean     = false,
    /**
     * Number of [LocalContentEntry] rows in Room for the bound profile.
     * `null` until the first DB emission (loading state).
     * `0` with [isOffline] == `true` triggers the no-cache screen.
     */
    val cachedContentCount: Int?    = null
)

sealed interface HomeIntent {
    data object TogglePlayPause : HomeIntent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    prefs: ProfilePreferences,
    private val playbackController: PlaybackController,
    private val contentDao: ContentDao,
    private val syncPrefs: SyncPreferences,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val profileId = prefs.boundProfileId ?: ""

    private val _state = MutableStateFlow(
        HomeState(
            boundProfileName  = prefs.boundProfileName  ?: "Luna",
            boundProfileEmoji = prefs.boundProfileEmoji ?: "🐻"
        )
    )
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playbackController.nowPlaying.collect { np ->
                _state.update { home ->
                    home.copy(
                        nowPlayingTitle    = np.title,
                        nowPlayingArtist   = np.artistName,
                        nowPlayingImageUrl = np.imageUrl,
                        isPlaying          = np.isPlaying
                    )
                }
            }
        }

        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _state.update { it.copy(
                    isOffline      = !online,
                    isStaleContent = isStaleContent(online)
                )}
            }
        }

        viewModelScope.launch {
            contentDao.countAllFlow(profileId).collect { count ->
                _state.update { it.copy(cachedContentCount = count) }
            }
        }
    }

    fun onIntent(intent: HomeIntent) {
        viewModelScope.launch {
            when (intent) {
                HomeIntent.TogglePlayPause ->
                    if (state.value.isPlaying) playbackController.pause()
                    else playbackController.resume()
            }
        }
    }

    /**
     * Returns `true` when the device is online AND the last sync was more than 24 hours ago.
     * When offline there is nothing useful to show – the offline indicator takes precedence.
     */
    internal fun isStaleContent(isOnline: Boolean): Boolean {
        if (!isOnline) return false
        val raw = syncPrefs.lastSyncTimestamp ?: return false
        return try {
            Duration.between(Instant.parse(raw), Instant.now()) > Duration.ofHours(24)
        } catch (_: Exception) {
            false
        }
    }
}
