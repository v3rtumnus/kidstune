package at.kidstune.kids.playback

import android.util.Log
import at.kidstune.kids.data.local.AlbumDao
import at.kidstune.kids.data.local.FavoriteDao
import at.kidstune.kids.data.local.PlaybackPositionDao
import at.kidstune.kids.data.local.TrackDao
import at.kidstune.kids.data.local.entities.LocalFavorite
import at.kidstune.kids.data.local.entities.LocalPlaybackPosition
import at.kidstune.kids.data.preferences.ProfilePreferences
import at.kidstune.kids.domain.model.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlaybackController"
private const val POSITION_PERSIST_INTERVAL_MS = 5_000L

/**
 * Central playback controller – the single point of contact for all playback commands.
 *
 * Rules enforced here:
 *  - Albums & playlists are always started as a context ([play] then [skipToIndex]) so
 *    Spotify auto-advances through subsequent tracks.
 *  - Only favorites use bare track URIs (no shared context).
 *  - Playback position is persisted every 5 s, on pause, and on [onBackground].
 */
@Singleton
class PlaybackController @Inject constructor(
    val spotifyRemote: SpotifyRemote,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val playbackPositionDao: PlaybackPositionDao,
    private val favoriteDao: FavoriteDao,
    private val prefs: ProfilePreferences,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val _nowPlaying = MutableStateFlow(NowPlayingState())
    val nowPlaying: StateFlow<NowPlayingState> = _nowPlaying.asStateFlow()

    // ── Favorites queue (null = not in favorites mode) ────────────────────────
    @Volatile private var favoritesQueue: List<String>? = null
    @Volatile private var currentFavoriteIndex: Int = 0

    private var persistJob: Job? = null

    init {
        observePlayerState()
        startFavoritesAdvancement()
    }

    // ── Public playback commands ──────────────────────────────────────────────

    /**
     * Play an album context starting at [trackIndex] (0-based).
     * Spotify will auto-advance through subsequent tracks.
     */
    suspend fun playFromChapter(albumUri: String, trackIndex: Int) {
        favoritesQueue = null
        try {
            spotifyRemote.play(albumUri)
            spotifyRemote.skipToIndex(albumUri, trackIndex)
        } catch (e: Exception) {
            Log.w(TAG, "playFromChapter failed: ${e.message}")
        }
    }

    /** Shorthand – play an album context from the very first track. */
    suspend fun playAlbumFromStart(albumUri: String) = playFromChapter(albumUri, 0)

    /**
     * Play a playlist context starting at [trackIndex] (0-based).
     * [playlistUri] must be a `spotify:playlist:xxx` URI stored in [LocalContentEntry.spotifyUri].
     */
    suspend fun playFromPlaylist(playlistUri: String, trackIndex: Int) {
        favoritesQueue = null
        try {
            spotifyRemote.play(playlistUri)
            spotifyRemote.skipToIndex(playlistUri, trackIndex)
        } catch (e: Exception) {
            Log.w(TAG, "playFromPlaylist failed: ${e.message}")
        }
    }

    /**
     * Play favorites sequentially starting at [startIndex].
     * Bare track URIs are acceptable here because favorites have no shared context.
     * When the current track ends the controller advances to the next favorite.
     */
    suspend fun playFavoritesFrom(favorites: List<LocalFavorite>, startIndex: Int) {
        if (favorites.isEmpty()) return
        val queue = favorites.map { it.spotifyTrackUri }
        favoritesQueue = queue
        currentFavoriteIndex = startIndex.coerceIn(0, queue.lastIndex)
        try {
            spotifyRemote.play(queue[currentFavoriteIndex])
        } catch (e: Exception) {
            Log.w(TAG, "playFavoritesFrom failed: ${e.message}")
        }
    }

    suspend fun pause() {
        try {
            spotifyRemote.pause()
            persistPosition()
        } catch (e: Exception) {
            Log.w(TAG, "pause failed: ${e.message}")
        }
    }

    suspend fun resume() {
        try { spotifyRemote.resume() } catch (e: Exception) {
            Log.w(TAG, "resume failed: ${e.message}")
        }
    }

    suspend fun skipNext() {
        try { spotifyRemote.skipNext() } catch (e: Exception) {
            Log.w(TAG, "skipNext failed: ${e.message}")
        }
    }

    suspend fun skipPrevious() {
        try { spotifyRemote.skipPrevious() } catch (e: Exception) {
            Log.w(TAG, "skipPrevious failed: ${e.message}")
        }
    }

    suspend fun seekTo(positionMs: Long) {
        try { spotifyRemote.seekTo(positionMs) } catch (e: Exception) {
            Log.w(TAG, "seekTo failed: ${e.message}")
        }
    }

    /** Called from MainActivity.onStop() to flush position before the app is backgrounded. */
    fun onBackground() {
        scope.launch { persistPosition() }
    }

    // ── PlayerState observation ───────────────────────────────────────────────

    private fun observePlayerState() {
        scope.launch {
            spotifyRemote.playerStateFlow.collect { state ->
                state ?: return@collect
                updateNowPlayingFromState(state)
                schedulePersistenceThrottle()
            }
        }
    }

    private suspend fun updateNowPlayingFromState(state: SpotifyPlayerStateInternal) {
        val trackUri = state.trackUri
        if (trackUri == null) {
            _nowPlaying.update { NowPlayingState() }
            return
        }

        // Look up the track in Room for imageUrl and chapter info
        val localTrack = trackDao.getByUri(trackUri)
        val album = localTrack?.let { albumDao.getById(it.albumId) }

        val chapterIndex: Int?
        val totalChapters: Int?
        if (album?.contentType == ContentType.AUDIOBOOK) {
            chapterIndex  = trackDao.getIndexByUri(trackUri)
            totalChapters = album.totalTracks
        } else {
            chapterIndex  = null
            totalChapters = null
        }

        // Check favorite status
        val profileId = prefs.boundProfileId
        val isFavorite = if (profileId != null) {
            favoriteDao.existsByTrackUri(profileId, trackUri).first()
        } else false

        _nowPlaying.update {
            it.copy(
                trackUri      = trackUri,
                title         = state.trackTitle ?: localTrack?.title,
                artistName    = state.artistName ?: localTrack?.artistName,
                imageUrl      = localTrack?.imageUrl ?: album?.imageUrl,
                durationMs    = state.durationMs.takeIf { d -> d > 0 } ?: (localTrack?.durationMs ?: 0L),
                positionMs    = state.positionMs,
                isPlaying     = !state.isPaused,
                isFavorite    = isFavorite,
                chapterIndex  = chapterIndex,
                totalChapters = totalChapters
            )
        }
    }

    // ── Favorites auto-advancement ────────────────────────────────────────────

    private fun startFavoritesAdvancement() {
        scope.launch {
            var lastTrackUri: String? = null
            var wasPlaying = false
            spotifyRemote.playerStateFlow.collect { state ->
                state ?: return@collect
                val queue = favoritesQueue ?: run {
                    wasPlaying = !state.isPaused
                    lastTrackUri = state.trackUri
                    return@collect
                }

                // Detect end-of-track: same URI, now paused at position 0, was previously playing.
                // The wasPlaying guard prevents false-positive when a user manually pauses
                // right at the start of a track (positionMs == 0 but track never played).
                val currentUri = state.trackUri
                if (state.isPaused && state.positionMs == 0L
                        && currentUri == lastTrackUri && wasPlaying) {
                    val nextIndex = currentFavoriteIndex + 1
                    if (nextIndex < queue.size) {
                        currentFavoriteIndex = nextIndex
                        try {
                            spotifyRemote.play(queue[nextIndex])
                        } catch (e: Exception) {
                            Log.w(TAG, "Favorites advance failed: ${e.message}")
                        }
                    }
                    // Wrap around: uncomment if you want looping
                    // else { currentFavoriteIndex = 0; spotifyRemote.play(queue[0]) }
                }
                lastTrackUri = currentUri
                wasPlaying = !state.isPaused
            }
        }
    }

    // ── Position persistence ──────────────────────────────────────────────────

    private fun schedulePersistenceThrottle() {
        if (persistJob?.isActive == true) return
        persistJob = scope.launch {
            delay(POSITION_PERSIST_INTERVAL_MS)
            persistPosition()
        }
    }

    private suspend fun persistPosition() {
        val state = _nowPlaying.value
        val profileId = prefs.boundProfileId ?: return
        val trackUri = state.trackUri ?: return

        // We need the context URI (album/playlist) stored in Room via the track
        val localTrack = trackDao.getByUri(trackUri) ?: run {
            Log.w(TAG, "persistPosition: no local track for URI $trackUri – skipping")
            return
        }
        val album = albumDao.getById(localTrack.albumId) ?: run {
            Log.w(TAG, "persistPosition: no album ${localTrack.albumId} for track $trackUri – skipping")
            return
        }
        val trackIndex = trackDao.getIndexByUri(trackUri)

        playbackPositionDao.upsert(
            LocalPlaybackPosition(
                profileId  = profileId,
                contextUri = album.spotifyAlbumUri,
                trackUri   = trackUri,
                trackIndex = trackIndex,
                positionMs = state.positionMs,
                updatedAt  = Instant.now()
            )
        )
    }
}
